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
package org.neo4j.gds.embeddings.graphsage.algo;

import org.eclipse.collections.api.tuple.primitive.IntObjectPair;
import org.eclipse.collections.api.tuple.primitive.LongLongPair;
import org.eclipse.collections.impl.tuple.Tuples;
import org.eclipse.collections.impl.tuple.primitive.PrimitiveTuples;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.gds.api.schema.GraphSchema;
import org.neo4j.gds.applications.algorithms.embeddings.GraphSageModelCatalog;
import org.neo4j.gds.applications.algorithms.embeddings.NodeEmbeddingAlgorithmsEstimationModeBusinessFacade;
import org.neo4j.gds.collections.ha.HugeObjectArray;
import org.neo4j.gds.config.MutateConfig;
import org.neo4j.gds.core.GraphDimensions;
import org.neo4j.gds.core.concurrency.Concurrency;
import org.neo4j.gds.core.model.InjectModelCatalog;
import org.neo4j.gds.core.model.Model;
import org.neo4j.gds.core.model.ModelCatalog;
import org.neo4j.gds.core.model.ModelCatalogExtension;
import org.neo4j.gds.embeddings.graphsage.GraphSageModelTrainer;
import org.neo4j.gds.embeddings.graphsage.Layer;
import org.neo4j.gds.embeddings.graphsage.ModelData;
import org.neo4j.gds.embeddings.graphsage.SingleLabelFeatureFunction;
import org.neo4j.gds.embeddings.graphsage.TrainConfigTransformer;
import org.neo4j.gds.mem.MemoryRange;
import org.neo4j.gds.mem.MemoryTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.LongUnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.collections.impl.tuple.primitive.PrimitiveTuples.pair;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.neo4j.gds.mem.Estimate.sizeOfDoubleArray;
import static org.neo4j.gds.mem.Estimate.sizeOfIntArray;
import static org.neo4j.gds.mem.Estimate.sizeOfLongArray;
import static org.neo4j.gds.mem.Estimate.sizeOfObjectArray;
import static org.neo4j.gds.mem.Estimate.sizeOfOpenHashContainer;
import static org.neo4j.gds.mem.MemoryEstimations.RESIDENT_MEMORY;
import static org.neo4j.gds.mem.MemoryEstimations.TEMPORARY_MEMORY;

@ModelCatalogExtension
class GraphSageAlgorithmFactoryTest {

    @InjectModelCatalog
    private ModelCatalog modelCatalog;

    @SuppressWarnings("UnnecessaryLocalVariable")
    @ParameterizedTest
    @MethodSource("parameters")
    void memoryEstimation(
        Function<ModelCatalog, GraphSageBaseConfig> gsConfigProvider,
        GraphSageTrainMemoryEstimateParameters trainParameters,
        long nodeCount,
        LongUnaryOperator hugeObjectArraySize
    ) {
        var gsConfig = gsConfigProvider.apply(modelCatalog);

        // features: HugeOA[nodeCount * double[featureSize]]
        var initialFeaturesArray = sizeOfDoubleArray(trainParameters.estimationFeatureDimension());
        var initialFeaturesMemory = hugeObjectArraySize.applyAsLong(initialFeaturesArray);

        // result: HugeOA[nodeCount * double[embeddingDimension]]
        var resultFeaturesArray = sizeOfDoubleArray(trainParameters.embeddingDimension());
        var resultFeaturesMemory = hugeObjectArraySize.applyAsLong(resultFeaturesArray);

        // batches:
        // per thread:

        var minBatchNodeCount = (long) gsConfig.batchSize();
        var maxBatchNodeCount = (long) gsConfig.batchSize();

        var batchSizes = new ArrayList<LongLongPair>();
        // additional final layer size
        batchSizes.add(PrimitiveTuples.pair(minBatchNodeCount, maxBatchNodeCount));
        var subGraphMemories = new ArrayList<MemoryRange>();
        var layerConfigs = trainParameters.layerParameters();
        for (LayerParameters layerParameters : layerConfigs) {
            var sampleSize = layerParameters.sampleSize();

            // int[3bs] selfAdjacency
            var minSelfAdjacencyMemory = sizeOfIntArray(minBatchNodeCount);
            var maxSelfAdjacencyMemory = sizeOfIntArray(maxBatchNodeCount);

            // int[3bs][0-sampleSize] adjacency
            var minAdjacencyMemory = sizeOfObjectArray(minBatchNodeCount);
            var maxAdjacencyMemory = sizeOfObjectArray(maxBatchNodeCount);

            var minInnerAdjacencyMemory = sizeOfIntArray(0);
            var maxInnerAdjacencyMemory = sizeOfIntArray(sampleSize);

            minAdjacencyMemory += minBatchNodeCount * minInnerAdjacencyMemory;
            maxAdjacencyMemory += maxBatchNodeCount * maxInnerAdjacencyMemory;

            // 3bs -> [min(3bs, nodeCount) .. min(3bs * (sampleSize + 1), nodeCount)]
            var minNextNodeCount = Math.min(minBatchNodeCount, nodeCount);
            var maxNextNodeCount = Math.min(maxBatchNodeCount * (sampleSize + 1), nodeCount);

            // nodeIds long[3bs]
            var minNextNodesMemory = sizeOfLongArray(minNextNodeCount);
            var maxNextNodesMemory = sizeOfLongArray(maxNextNodeCount);

            var minSubGraphMemory = minSelfAdjacencyMemory + minAdjacencyMemory + minNextNodesMemory;
            var maxSubGraphMemory = maxSelfAdjacencyMemory + maxAdjacencyMemory + maxNextNodesMemory;

            // LongIntHashMap toInternalId;
            // This is a local peak; will be GC'd before the global peak
            @SuppressWarnings("unused")
            var minLocalIdMapMemory = sizeOfOpenHashContainer(minNextNodeCount) + sizeOfLongArray(minNextNodeCount);
            @SuppressWarnings("unused")
            var maxLocalIdMapMemory = sizeOfOpenHashContainer(maxNextNodeCount) + sizeOfLongArray(maxNextNodeCount);

            subGraphMemories.add(MemoryRange.of(minSubGraphMemory, maxSubGraphMemory));

            minBatchNodeCount = minNextNodeCount;
            maxBatchNodeCount = maxNextNodeCount;

            // add next layer's sizes
            batchSizes.add(PrimitiveTuples.pair(minBatchNodeCount, maxBatchNodeCount));
        }

        Collections.reverse(batchSizes);

        var nextLayerIndex = new AtomicInteger();
        var aggregatorMemories = layerConfigs.stream().map(layerConfig -> {
            var layerIndex = nextLayerIndex.getAndIncrement();

            var nodeCounts = batchSizes.get(layerIndex + 1);
            var minNodeCount = nodeCounts.getOne();
            var maxNodeCount = nodeCounts.getTwo();

            var previousNodeCounts = batchSizes.get(layerIndex);

            long minAggregatorMemory;
            long maxAggregatorMemory;
            if (layerConfig.aggregatorType() == AggregatorType.MEAN) {
                var featureOrEmbeddingSize = layerConfig.cols();

                //   multi mean - new double[[..adjacency.length(=iterNodeCount)] * featureSize];
                var minMeans = sizeOfDoubleArray(minNodeCount * featureOrEmbeddingSize);
                var maxMeans = sizeOfDoubleArray(maxNodeCount * featureOrEmbeddingSize);

                //   MatrixMultiplyWithTransposedSecondOperand - new double[iterNodeCount * embeddingDimension]
                var minProduct = sizeOfDoubleArray(minNodeCount * trainParameters.embeddingDimension());
                var maxProduct = sizeOfDoubleArray(maxNodeCount * trainParameters.embeddingDimension());

                //   activation function = same as input
                var minActivation = minProduct;
                var maxActivation = maxProduct;

                minAggregatorMemory = minMeans + minProduct + minActivation;
                maxAggregatorMemory = maxMeans + maxProduct + maxActivation;
            } else if (layerConfig.aggregatorType() == AggregatorType.POOL) {
                var minPreviousNodeCount = previousNodeCounts.getOne();
                var maxPreviousNodeCount = previousNodeCounts.getTwo();

                //  MatrixMultiplyWithTransposedSecondOperand - new double[iterNodeCount(-1) * embeddingDimension]
                var minWeightedPreviousLayer = sizeOfDoubleArray(minPreviousNodeCount * trainParameters.embeddingDimension());
                var maxWeightedPreviousLayer = sizeOfDoubleArray(maxPreviousNodeCount * trainParameters.embeddingDimension());

                // MatrixVectorSum = shape is matrix input == weightedPreviousLayer
                var minBiasedWeightedPreviousLayer = minWeightedPreviousLayer;
                var maxBiasedWeightedPreviousLayer = maxWeightedPreviousLayer;

                //   activation function = same as input
                var minNeighborhoodActivations = minBiasedWeightedPreviousLayer;
                var maxNeighborhoodActivations = maxBiasedWeightedPreviousLayer;

                //  ElementwiseMax - double[iterNodeCount * embeddingDimension]
                var minElementwiseMax = sizeOfDoubleArray(minNodeCount * trainParameters.embeddingDimension());
                var maxElementwiseMax = sizeOfDoubleArray(maxNodeCount * trainParameters.embeddingDimension());

                //  Slice - double[iterNodeCount * embeddingDimension]
                var minSelfPreviousLayer = sizeOfDoubleArray(minNodeCount * trainParameters.embeddingDimension());
                var maxSelfPreviousLayer = sizeOfDoubleArray(maxNodeCount * trainParameters.embeddingDimension());

                //  MatrixMultiplyWithTransposedSecondOperand - new double[iterNodeCount * embeddingDimension]
                var minSelf = sizeOfDoubleArray(minNodeCount * trainParameters.embeddingDimension());
                var maxSelf = sizeOfDoubleArray(maxNodeCount * trainParameters.embeddingDimension());

                //  MatrixMultiplyWithTransposedSecondOperand - new double[iterNodeCount * embeddingDimension]
                var minNeighbors = sizeOfDoubleArray(minNodeCount * trainParameters.embeddingDimension());
                var maxNeighbors = sizeOfDoubleArray(maxNodeCount * trainParameters.embeddingDimension());

                //  MatrixSum - new double[iterNodeCount * embeddingDimension]
                var minSum = minSelf;
                var maxSum = maxSelf;

                //  activation function = same as input
                var minActivation = minSum;
                var maxActivation = maxSum;

                minAggregatorMemory = minWeightedPreviousLayer + minBiasedWeightedPreviousLayer + minNeighborhoodActivations + minElementwiseMax + minSelfPreviousLayer + minSelf + minNeighbors + minSum + minActivation;
                maxAggregatorMemory = maxWeightedPreviousLayer + maxBiasedWeightedPreviousLayer + maxNeighborhoodActivations + maxElementwiseMax + maxSelfPreviousLayer + maxSelf + maxNeighbors + maxSum + maxActivation;
            } else {
                // never happens
                minAggregatorMemory = 0;
                maxAggregatorMemory = 0;
            }

            return MemoryRange.of(minAggregatorMemory, maxAggregatorMemory);
        }).collect(toList());

        // normalize rows = same as input (output of aggregator)
        var lastLayerBatchNodeCount = batchSizes.get(batchSizes.size() - 1);
        var minNormalizeRows = sizeOfDoubleArray(lastLayerBatchNodeCount.getOne() * trainParameters.embeddingDimension());
        var maxNormalizeRows = sizeOfDoubleArray(lastLayerBatchNodeCount.getTwo() * trainParameters.embeddingDimension());
        aggregatorMemories.add(MemoryRange.of(minNormalizeRows, maxNormalizeRows));

        // previous layer representation = parent = local features: double[(bs..3bs) * featureSize]
        var firstLayerBatchNodeCount = batchSizes.get(0);
        var minFirstLayerMemory = sizeOfDoubleArray(firstLayerBatchNodeCount.getOne() * trainParameters.estimationFeatureDimension());
        var maxFirstLayerMemory = sizeOfDoubleArray(firstLayerBatchNodeCount.getTwo() * trainParameters.estimationFeatureDimension());
        aggregatorMemories.add(0, MemoryRange.of(minFirstLayerMemory, maxFirstLayerMemory));

        var lossFunctionMemory = Stream.concat(
            subGraphMemories.stream(),
            aggregatorMemories.stream()
        ).reduce(MemoryRange.empty(), MemoryRange::add);

        var concurrency = gsConfig.concurrency();
        var evaluateLossMemory = lossFunctionMemory.times(concurrency.value());

        var expectedMemory = evaluateLossMemory
            .add(MemoryRange.of(initialFeaturesMemory))
            .add(MemoryRange.of(resultFeaturesMemory))
            .add(MemoryRange.of(40L)); // GraphSage.class

        var graphSageModelCatalog = new GraphSageModelCatalog(modelCatalog);
        var estimationFacade = new NodeEmbeddingAlgorithmsEstimationModeBusinessFacade(graphSageModelCatalog, null);
        var memoryEstimation = estimationFacade.graphSage(gsConfig, gsConfig instanceof MutateConfig);

        var actualTree = memoryEstimation.estimate(GraphDimensions.of(nodeCount), concurrency);

        MemoryRange actual = actualTree.memoryUsage();

        assertEquals(expectedMemory.min, actual.min);
        assertEquals(expectedMemory.max, actual.max);
    }

    @Test
    void memoryEstimationTreeStructure() {
        var trainConfig = GraphSageTrainConfigImpl
            .builder()
            .modelUser("")
            .modelName("modelName")
            .featureProperties(List.of("a"))
            .sampleSizes(List.of(1, 2))
            .aggregator(AggregatorType.MEAN)
            .build();

        var model = Model.of(
            "graphSage",
            GraphSchema.empty(),
            ModelData.of(new Layer[]{}, new SingleLabelFeatureFunction()),
            trainConfig,
            GraphSageModelTrainer.GraphSageTrainMetrics.empty()
        );

        modelCatalog.set(model);

        var gsConfig = GraphSageStreamConfigImpl
            .builder()
            .modelUser("")
            .modelName("modelName")
            .build();

        var graphSageModelCatalog = new GraphSageModelCatalog(modelCatalog);
        var estimationFacade = new NodeEmbeddingAlgorithmsEstimationModeBusinessFacade(graphSageModelCatalog, null);
        var memoryEstimation = estimationFacade.graphSage(gsConfig, gsConfig instanceof MutateConfig);
        var actualEstimation = memoryEstimation.estimate(GraphDimensions.of(1337), new Concurrency(42));

        assertThat(flatten(actualEstimation)).containsExactly(
            pair(0, "GraphSage"),
            pair(1, TEMPORARY_MEMORY),
            pair(2, "this.instance"),
            pair(2, "initialFeatures"),
            pair(2, "concurrentBatches"),
            pair(3, "computationGraph"),
            pair(4, "subgraphs"),
            pair(5, "subgraph 1"),
            pair(5, "subgraph 2"),
            pair(4, "forward"),
            pair(5, "firstLayer"),
            pair(5, "MEAN 1"),
            pair(5, "MEAN 2"),
            pair(5, "normalizeRows"),
            pair(2, "resultFeatures")
        );
    }

    @Test
    void memoryEstimationMutateTreeStructure() {
        var trainConfig = GraphSageTrainConfigImpl
            .builder()
            .modelUser("")
            .modelName("modelName")
            .featureProperties(List.of("a"))
            .sampleSizes(List.of(1, 2))
            .aggregator(AggregatorType.MEAN)
            .build();

        var model = Model.of(
            "graphSage",
            GraphSchema.empty(),
            ModelData.of(new Layer[]{}, new SingleLabelFeatureFunction()),
            trainConfig,
            GraphSageModelTrainer.GraphSageTrainMetrics.empty()
        );

        modelCatalog.set(model);

        var gsConfig = GraphSageMutateConfigImpl
            .builder()
            .modelUser("")
            .modelName("modelName")
            .mutateProperty("foo")
            .build();

        var graphSageModelCatalog = new GraphSageModelCatalog(modelCatalog);
        var estimationFacade = new NodeEmbeddingAlgorithmsEstimationModeBusinessFacade(graphSageModelCatalog, null);
        var memoryEstimation = estimationFacade.graphSage(gsConfig, true);
        var actualEstimation = memoryEstimation.estimate(GraphDimensions.of(1337), new Concurrency(42));

        assertThat(flatten(actualEstimation)).containsExactly(
            pair(0, "GraphSage"),
            pair(1, RESIDENT_MEMORY),
            pair(2, "resultFeatures"),
            pair(1, TEMPORARY_MEMORY),
            pair(2, "this.instance"),
            pair(2, "initialFeatures"),
            pair(2, "concurrentBatches"),
            pair(3, "computationGraph"),
            pair(4, "subgraphs"),
            pair(5, "subgraph 1"),
            pair(5, "subgraph 2"),
            pair(4, "forward"),
            pair(5, "firstLayer"),
            pair(5, "MEAN 1"),
            pair(5, "MEAN 2"),
            pair(5, "normalizeRows")
        );
    }

    private static List<IntObjectPair<String>> flatten(MemoryTree memoryTree) {
        return leaves(0, memoryTree).collect(toList());
    }

    private static Stream<IntObjectPair<String>> leaves(int depth, MemoryTree memoryTree) {
        return Stream.concat(
            Stream.of(pair(depth, memoryTree.description())),
            memoryTree.components().stream().flatMap(tree -> leaves(depth + 1, tree))
        );
    }

    static Stream<Arguments> parameters() {
        var smallNodeCounts = List.of(1L, 10L, 100L, 10_000L);
        var largeNodeCounts = List.of(11000000000L, 100000000000L);
        var nodeCounts = Stream.concat(
            smallNodeCounts.stream().map(nc -> Tuples.pair(
                nc,
                (LongUnaryOperator) new LongUnaryOperator() {
                    @Override
                    public long applyAsLong(long innerSize) {
                        return HugeObjectArray.memoryEstimation(nc, innerSize);
                    }

                    @Override
                    public String toString() {
                        return "single page";
                    }
                }
            )),
            largeNodeCounts.stream().map(nc -> Tuples.pair(
                nc,
                (LongUnaryOperator) new LongUnaryOperator() {
                    @Override
                    public long applyAsLong(long innerSize) {
                        return HugeObjectArray.memoryEstimation(nc, innerSize);
                    }

                    @Override
                    public String toString() {
                        return "multiple pages";
                    }
                }
            ))
        );

        var userName = "userName";
        var modelName = "modelName";

        var concurrencies = List.of(1, 4);
        var batchSizes = List.of(1, 100, 10_000);
        var nodePropertySizes = List.of(1, 9, 42);
        var embeddingDimensions = List.of(64, 256);
        var aggregators = List.of(AggregatorType.MEAN, AggregatorType.POOL);
        var degreesAsProperty = List.of(true, false);
        var sampleSizesList = List.of(List.of(5, 100));

        return nodeCounts.flatMap(nodeCountPair -> {
            var nodeCount = nodeCountPair.getOne();
            var hugeObjectArraySize = nodeCountPair.getTwo();

            return concurrencies.stream().flatMap(concurrency ->
                sampleSizesList.stream().flatMap(sampleSizes ->
                    batchSizes.stream().flatMap(batchSize ->
                        aggregators.stream().flatMap(aggregator ->
                            embeddingDimensions.stream().flatMap(embeddingDimension ->
                                degreesAsProperty.stream().flatMap(degreeAsProperty ->
                                    nodePropertySizes.stream().map(nodePropertySize -> {
                                        var trainConfig = GraphSageTrainConfigImpl
                                            .builder()
                                            .modelName(modelName)
                                            .modelUser(userName)
                                            .concurrency(concurrency)
                                            .sampleSizes(sampleSizes)
                                            .batchSize(batchSize)
                                            .aggregator(aggregator)
                                            .embeddingDimension(embeddingDimension)
                                            .featureProperties(
                                                IntStream.range(0, nodePropertySize)
                                                    .mapToObj(i -> String.valueOf('a' + i))
                                                    .collect(toList())
                                            )
                                            .build();

                                        var model = Model.of(
                                            "graphSage",
                                            GraphSchema.empty(),
                                            ModelData.of(new Layer[]{}, new SingleLabelFeatureFunction()),
                                            trainConfig,
                                            GraphSageModelTrainer.GraphSageTrainMetrics.empty()
                                        );

                                        Function<ModelCatalog, GraphSageBaseConfig> streamConfigProvider = (modelCatalog) -> {
                                            modelCatalog.set(model);

                                            return GraphSageStreamConfigImpl
                                                .builder()
                                                .concurrency(concurrency)
                                                .modelName(modelName)
                                                .modelUser(userName)
                                                .batchSize(batchSize)
                                                .build();
                                        };

                                        return arguments(
                                            streamConfigProvider,
                                            TrainConfigTransformer.toMemoryEstimateParameters(trainConfig),
                                            nodeCount,
                                            hugeObjectArraySize
                                        );
                                    })
                                )
                            )
                        )
                    )
                )
            );
        });
    }

    @Test
    void mutateHasPersistentPart() {
        var modelName = "modelName";

        var trainConfig = GraphSageTrainConfigImpl
            .builder()
            .modelName(modelName)
            .modelUser("")
            .featureProperties(List.of("a"))
            .build();

        var model = Model.of(
            "graphSage",
            GraphSchema.empty(),
            ModelData.of(new Layer[]{}, new SingleLabelFeatureFunction()),
            trainConfig,
            GraphSageModelTrainer.GraphSageTrainMetrics.empty()
        );

        modelCatalog.set(model);

        var config = GraphSageMutateConfigImpl
            .builder()
            .modelUser("")
            .modelName(modelName)
            .mutateProperty("foo")
            .build();

        var graphSageModelCatalog = new GraphSageModelCatalog(modelCatalog);
        var estimationFacade = new NodeEmbeddingAlgorithmsEstimationModeBusinessFacade(graphSageModelCatalog, null);
        var memoryEstimation = estimationFacade.graphSage(config, true);
        var actualTree = memoryEstimation.estimate(GraphDimensions.of(10000), new Concurrency(4));

        MemoryRange actual = actualTree.memoryUsage();

        assertEquals(6861864, actual.min);
        assertEquals(18593064, actual.max);

        assertThat(actualTree.residentMemory())
            .isPresent()
            .map(MemoryTree::memoryUsage)
            .contains(MemoryRange.of(5320064L));
    }
}
