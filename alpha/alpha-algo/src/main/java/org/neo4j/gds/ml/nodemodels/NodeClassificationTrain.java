/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gds.ml.nodemodels;

import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.tuple.Tuples;
import org.jetbrains.annotations.NotNull;
import org.neo4j.gds.Algorithm;
import org.neo4j.gds.annotation.ValueClass;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.NodeProperties;
import org.neo4j.gds.core.model.Model;
import org.neo4j.gds.core.utils.mem.AllocationTracker;
import org.neo4j.gds.core.utils.mem.MemoryEstimation;
import org.neo4j.gds.core.utils.mem.MemoryEstimations;
import org.neo4j.gds.core.utils.mem.MemoryRange;
import org.neo4j.gds.core.utils.paged.HugeLongArray;
import org.neo4j.gds.core.utils.paged.ReadOnlyHugeLongArray;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.core.utils.progress.tasks.Task;
import org.neo4j.gds.core.utils.progress.tasks.Tasks;
import org.neo4j.gds.ml.Training;
import org.neo4j.gds.ml.TrainingConfig;
import org.neo4j.gds.ml.core.batch.BatchQueue;
import org.neo4j.gds.ml.nodemodels.logisticregression.NodeLogisticRegressionData;
import org.neo4j.gds.ml.nodemodels.logisticregression.NodeLogisticRegressionPredictor;
import org.neo4j.gds.ml.nodemodels.logisticregression.NodeLogisticRegressionTrain;
import org.neo4j.gds.ml.nodemodels.logisticregression.NodeLogisticRegressionTrainConfig;
import org.neo4j.gds.ml.nodemodels.metrics.Metric;
import org.neo4j.gds.ml.nodemodels.metrics.MetricSpecification;
import org.neo4j.gds.ml.splitting.FractionSplitter;
import org.neo4j.gds.ml.splitting.StratifiedKFoldSplitter;
import org.neo4j.gds.ml.splitting.TrainingExamplesSplit;
import org.neo4j.gds.ml.util.ShuffleUtil;
import org.openjdk.jol.util.Multiset;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongUnaryOperator;
import java.util.stream.Collectors;

import static org.neo4j.gds.core.utils.mem.MemoryEstimations.delegateEstimation;
import static org.neo4j.gds.core.utils.mem.MemoryEstimations.maxEstimation;
import static org.neo4j.gds.mem.MemoryUsage.sizeOfDoubleArray;
import static org.neo4j.gds.ml.util.ShuffleUtil.createRandomDataGenerator;

public final class NodeClassificationTrain extends Algorithm<NodeClassificationTrain, Model<NodeLogisticRegressionData, NodeClassificationTrainConfig, NodeClassificationModelInfo>> {

    public static final String MODEL_TYPE = "nodeLogisticRegression";

    private final Graph graph;
    private final NodeClassificationTrainConfig config;
    private final HugeLongArray targets;
    private final Multiset<Long> classCounts;
    private final HugeLongArray nodeIds;
    private final AllocationTracker allocationTracker;
    private final List<Metric> metrics;
    private final StatsMap trainStats;
    private final StatsMap validationStats;

    static MemoryEstimation estimate(NodeClassificationTrainConfig config) {
        var maxBatchSize = config.paramsConfig()
            .stream()
            .mapToInt(TrainingConfig::batchSize)
            .max()
            .getAsInt();
        var fudgedClassCount = 1000;
        var fudgedFeatureCount = 500;
        var holdoutFraction = config.holdoutFraction();
        var validationFolds = config.validationFolds();

        var modelSelection = modelTrainAndEvaluateMemoryUsage(
            maxBatchSize,
            fudgedClassCount,
            fudgedFeatureCount,
            (nodeCount) -> (long) (nodeCount * holdoutFraction * (validationFolds - 1) / validationFolds)
        );
        var bestModelEvaluation = delegateEstimation(
            modelTrainAndEvaluateMemoryUsage(
                maxBatchSize,
                fudgedClassCount,
                fudgedFeatureCount,
                (nodeCount) -> (long) (nodeCount * holdoutFraction)
            ),
            "best model evaluation"
        );
        var maxOfModelSelectionAndBestModelEvaluation = maxEstimation(List.of(modelSelection, bestModelEvaluation));
        // Final step is to retrain the best model with the entire node set.
        // Training memory is independent of node set size so we can skip that last estimation.
        return MemoryEstimations.builder()
            .perNode("global targets", HugeLongArray::memoryEstimation)
            .rangePerNode("global class counts", __ -> MemoryRange.of(2 * 8, fudgedClassCount * 8))
            .add("metrics", MetricSpecification.memoryEstimation(fudgedClassCount))
            .perNode("node IDs", HugeLongArray::memoryEstimation)
            .add("outer split", FractionSplitter.estimate(1 - holdoutFraction))
            .add("inner split", StratifiedKFoldSplitter.memoryEstimation(validationFolds, 1 - holdoutFraction))
            .add("stats map train", StatsMap.memoryEstimation(config.metrics().size(), config.params().size()))
            .add("stats map validation", StatsMap.memoryEstimation(config.metrics().size(), config.params().size()))
            .add("max of model selection and best model evaluation", maxOfModelSelectionAndBestModelEvaluation)
            .build();
    }

    public static String taskName() {
        return "NCTrain";
    }

    public static Task progressTask(int validationFolds, int paramsSize) {
        return Tasks.task(
            taskName(),
            Tasks.leaf("ShuffleAndSplit"),
            Tasks.iterativeFixed(
                "SelectBestModel",
                () -> List.of(Tasks.iterativeFixed("Model Candidate", () -> List.of(
                        Tasks.task(
                            "Split",
                            Training.progressTask("Training"),
                            Tasks.leaf("Evaluate")
                        )
                    ), validationFolds)
                ),
                paramsSize
            ),
            Training.progressTask("TrainSelectedOnRemainder"),
            Tasks.leaf("EvaluateSelectedModel"),
            Training.progressTask("RetrainSelectedModel")
        );
    }

    @NotNull
    private static MemoryEstimation modelTrainAndEvaluateMemoryUsage(
        int maxBatchSize,
        int fudgedClassCount,
        int fudgedFeatureCount,
        LongUnaryOperator nodeSetSize
    ) {
        MemoryEstimation training = NodeLogisticRegressionTrain.memoryEstimation(
            fudgedClassCount,
            fudgedFeatureCount,
            maxBatchSize
        );

        MemoryEstimation metricsComputation = MemoryEstimations.builder("computing metrics")
            .perNode("local targets", (nodeCount) -> {
                var sizeOfLargePartOfAFold = nodeSetSize.applyAsLong(nodeCount);
                return HugeLongArray.memoryEstimation(sizeOfLargePartOfAFold);
            })
            .perNode("predicted classes", (nodeCount) -> {
                var sizeOfLargePartOfAFold = nodeSetSize.applyAsLong(nodeCount);
                return HugeLongArray.memoryEstimation(sizeOfLargePartOfAFold);
            })
            .fixed("probabilities", sizeOfDoubleArray(fudgedClassCount))
            .fixed("computation graph", NodeLogisticRegressionPredictor.sizeOfPredictionsVariableInBytes(
                BatchQueue.DEFAULT_BATCH_SIZE,
                fudgedFeatureCount,
                fudgedClassCount
            ))
            .build();

        return MemoryEstimations.builder("model selection")
            .max(List.of(training, metricsComputation))
            .build();
    }

    public static NodeClassificationTrain create(
        Graph graph,
        NodeClassificationTrainConfig config,
        AllocationTracker allocationTracker,
        ProgressTracker progressTracker
    ) {
        var targetNodeProperty = graph.nodeProperties(config.targetProperty());
        var targetsAndClasses = computeGlobalTargetsAndClasses(targetNodeProperty, graph.nodeCount(), allocationTracker);
        var targets = targetsAndClasses.getOne();
        var classCounts = targetsAndClasses.getTwo();
        var metrics = createMetrics(config, classCounts);
        var nodeIds = HugeLongArray.newArray(graph.nodeCount(), allocationTracker);
        nodeIds.setAll(i -> i);
        var trainStats = StatsMap.create(metrics);
        var validationStats = StatsMap.create(metrics);
        return new NodeClassificationTrain(graph, config, targets, classCounts, metrics, nodeIds, trainStats, validationStats, allocationTracker, progressTracker);
    }

    private static Pair<HugeLongArray, Multiset<Long>> computeGlobalTargetsAndClasses(NodeProperties targetNodeProperty, long nodeCount, AllocationTracker allocationTracker) {
        var classCounts = new Multiset<Long>();
        var targets = HugeLongArray.newArray(nodeCount, allocationTracker);
        for (long nodeId = 0; nodeId < nodeCount; nodeId++) {
            targets.set(nodeId, targetNodeProperty.longValue(nodeId));
            classCounts.add(targetNodeProperty.longValue(nodeId));
        }
        return Tuples.pair(targets, classCounts);
    }

    private static List<Metric> createMetrics(NodeClassificationTrainConfig config, Multiset<Long> globalClassCounts) {
        return config.metrics()
            .stream()
            .flatMap(spec -> spec.createMetrics(globalClassCounts.keys()))
            .collect(Collectors.toList());
    }

    private NodeClassificationTrain(
        Graph graph,
        NodeClassificationTrainConfig config,
        HugeLongArray targets,
        Multiset<Long> classCounts,
        List<Metric> metrics,
        HugeLongArray nodeIds,
        StatsMap trainStats,
        StatsMap validationStats,
        AllocationTracker allocationTracker,
        ProgressTracker progressTracker
    ) {
        super(progressTracker);
        this.graph = graph;
        this.config = config;
        this.targets = targets;
        this.classCounts = classCounts;
        this.metrics = metrics;
        this.nodeIds = nodeIds;
        this.trainStats = trainStats;
        this.validationStats = validationStats;
        this.allocationTracker = allocationTracker;
    }

    @Override
    public NodeClassificationTrain me() {
        return this;
    }

    @Override
    public void release() {}

    @Override
    public Model<NodeLogisticRegressionData, NodeClassificationTrainConfig, NodeClassificationModelInfo> compute() {
        progressTracker.beginSubTask();

        progressTracker.beginSubTask();
        ShuffleUtil.shuffleHugeLongArray(nodeIds, createRandomDataGenerator(config.randomSeed()));
        var outerSplit = new FractionSplitter(allocationTracker).split(nodeIds, 1 - config.holdoutFraction());
        var innerSplits = new StratifiedKFoldSplitter(
            config.validationFolds(),
            ReadOnlyHugeLongArray.of(outerSplit.trainSet()),
            ReadOnlyHugeLongArray.of(targets),
            config.randomSeed()
        ).splits();
        progressTracker.endSubTask();

        var modelSelectResult = selectBestModel(innerSplits);
        var bestParameters = modelSelectResult.bestParameters();
        var metricResults = evaluateBestModel(outerSplit, modelSelectResult, bestParameters);

        var retrainedModelData = retrainBestModel(bestParameters);
        progressTracker.endSubTask();

        return createModel(bestParameters, metricResults, retrainedModelData);
    }

    private ModelSelectResult selectBestModel(List<TrainingExamplesSplit> nodeSplits) {
        progressTracker.beginSubTask();
        for (NodeLogisticRegressionTrainConfig modelParams : config.paramsConfig()) {
            progressTracker.beginSubTask();
            var validationStatsBuilder = new ModelStatsBuilder(modelParams, nodeSplits.size());
            var trainStatsBuilder = new ModelStatsBuilder(modelParams, nodeSplits.size());

            for (TrainingExamplesSplit nodeSplit : nodeSplits) {
                progressTracker.beginSubTask();

                var trainSet = nodeSplit.trainSet();
                var validationSet = nodeSplit.testSet();

                progressTracker.beginSubTask("Training");
                var modelData = trainModel(trainSet, modelParams);
                progressTracker.endSubTask("Training");

                progressTracker.beginSubTask(validationSet.size() + trainSet.size());
                computeMetrics(classCounts, validationSet, modelData, metrics).forEach(validationStatsBuilder::update);
                computeMetrics(classCounts, trainSet, modelData, metrics).forEach(trainStatsBuilder::update);
                progressTracker.endSubTask();

                progressTracker.endSubTask();
            }
            progressTracker.endSubTask();

            metrics.forEach(metric -> {
                validationStats.add(metric, validationStatsBuilder.build(metric));
                trainStats.add(metric, trainStatsBuilder.build(metric));
            });
        }
        progressTracker.endSubTask();

        var mainMetric = metrics.get(0);
        var bestModelStats = validationStats.pickBestModelStats(mainMetric);

        return ModelSelectResult.of(bestModelStats.params(), trainStats, validationStats);
    }

    private Map<Metric, MetricData<NodeLogisticRegressionTrainConfig>> evaluateBestModel(
        TrainingExamplesSplit outerSplit,
        ModelSelectResult modelSelectResult,
        NodeLogisticRegressionTrainConfig bestParameters
    ) {
        progressTracker.beginSubTask("TrainSelectedOnRemainder");
        NodeLogisticRegressionData bestModelData = trainModel(outerSplit.trainSet(), bestParameters);
        progressTracker.endSubTask("TrainSelectedOnRemainder");

        progressTracker.beginSubTask(outerSplit.testSet().size() + outerSplit.trainSet().size());
        var testMetrics = computeMetrics(classCounts, outerSplit.testSet(), bestModelData, metrics);
        var outerTrainMetrics = computeMetrics(classCounts, outerSplit.trainSet(), bestModelData, metrics);
        progressTracker.endSubTask();

        return mergeMetricResults(modelSelectResult, outerTrainMetrics, testMetrics);
    }

    private NodeLogisticRegressionData retrainBestModel(NodeLogisticRegressionTrainConfig bestParameters) {
        progressTracker.beginSubTask("RetrainSelectedModel");
        var retrainedModelData = trainModel(nodeIds, bestParameters);
        progressTracker.endSubTask("RetrainSelectedModel");
        return retrainedModelData;
    }

    private Model<NodeLogisticRegressionData, NodeClassificationTrainConfig, NodeClassificationModelInfo> createModel(
        NodeLogisticRegressionTrainConfig bestParameters,
        Map<Metric, MetricData<NodeLogisticRegressionTrainConfig>> metricResults,
        NodeLogisticRegressionData retrainedModelData
    ) {
        var modelInfo = NodeClassificationModelInfo.of(
            retrainedModelData.classIdMap().originalIdsList(),
            bestParameters,
            metricResults
        );

        return Model.of(
            config.username(),
            config.modelName(),
            MODEL_TYPE,
            graph.schema(),
            retrainedModelData,
            config,
            modelInfo
        );
    }

    private Map<Metric, MetricData<NodeLogisticRegressionTrainConfig>> mergeMetricResults(
        ModelSelectResult modelSelectResult,
        Map<Metric, Double> outerTrainMetrics,
        Map<Metric, Double> testMetrics
    ) {
        return modelSelectResult.validationStats().keySet().stream().collect(Collectors.toMap(
            Function.identity(),
            metric ->
                MetricData.of(
                    modelSelectResult.trainStats().get(metric),
                    modelSelectResult.validationStats().get(metric),
                    outerTrainMetrics.get(metric),
                    testMetrics.get(metric)
                )
        ));
    }

    private NodeLogisticRegressionData trainModel(
        HugeLongArray trainSet,
        NodeLogisticRegressionTrainConfig nlrConfig
    ) {
        var train = new NodeLogisticRegressionTrain(graph, trainSet, nlrConfig, progressTracker, terminationFlag, config.concurrency());
        return train.compute();
    }

    private Map<Metric, Double> computeMetrics(
        Multiset<Long> globalClassCounts,
        HugeLongArray evaluationSet,
        NodeLogisticRegressionData modelData,
        Collection<Metric> metrics
    ) {
        var predictor = new NodeLogisticRegressionPredictor(modelData, config.featureProperties());
        var predictedClasses = HugeLongArray.newArray(evaluationSet.size(), allocationTracker);

        // consume from queue which contains local nodeIds, i.e. indices into evaluationSet
        // the consumer internally remaps to original nodeIds before prediction
        var consumer = new NodeClassificationPredictConsumer(
            graph,
            evaluationSet::get,
            predictor,
            null,
            predictedClasses,
            config.featureProperties(),
            progressTracker
        );

        var queue = new BatchQueue(evaluationSet.size());
        queue.parallelConsume(consumer, config.concurrency(), terminationFlag);

        var localTargets = makeLocalTargets(evaluationSet);
        return metrics.stream().collect(Collectors.toMap(
            Function.identity(),
            metric -> metric.compute(localTargets, predictedClasses, globalClassCounts)
        ));
    }

    private HugeLongArray makeLocalTargets(HugeLongArray nodeIds) {
        var targets = HugeLongArray.newArray(nodeIds.size(), allocationTracker);
        var targetNodeProperty = graph.nodeProperties(config.targetProperty());

        targets.setAll(i -> targetNodeProperty.longValue(nodeIds.get(i)));
        return targets;
    }

    @ValueClass
    interface ModelSelectResult {
        NodeLogisticRegressionTrainConfig bestParameters();
        Map<Metric, List<ModelStats<NodeLogisticRegressionTrainConfig>>> trainStats();
        Map<Metric, List<ModelStats<NodeLogisticRegressionTrainConfig>>> validationStats();

        static ModelSelectResult of(
            NodeLogisticRegressionTrainConfig bestConfig,
            StatsMap trainStats,
            StatsMap validationStats
        ) {
            return ImmutableModelSelectResult.of(bestConfig, trainStats.getMap(), validationStats.getMap());
        }

    }

    private static class ModelStatsBuilder {
        private final Map<Metric, Double> min;
        private final Map<Metric, Double> max;
        private final Map<Metric, Double> sum;
        private final NodeLogisticRegressionTrainConfig modelParams;
        private final int numberOfSplits;

        ModelStatsBuilder(NodeLogisticRegressionTrainConfig modelParams, int numberOfSplits) {
            this.modelParams = modelParams;
            this.numberOfSplits = numberOfSplits;
            this.min = new HashMap<>();
            this.max = new HashMap<>();
            this.sum = new HashMap<>();
        }

        void update(Metric metric, double value) {
            min.merge(metric, value, Math::min);
            max.merge(metric, value, Math::max);
            sum.merge(metric, value, Double::sum);
        }

        ModelStats<NodeLogisticRegressionTrainConfig> build(Metric metric) {
            return ImmutableModelStats.of(
                modelParams,
                sum.get(metric) / numberOfSplits,
                min.get(metric),
                max.get(metric)
            );
        }
    }
}
