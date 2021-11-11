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
package org.neo4j.gds.ml.nodemodels.logisticregression;

import org.neo4j.gds.api.Graph;
import org.neo4j.gds.core.utils.TerminationFlag;
import org.neo4j.gds.core.utils.mem.MemoryEstimation;
import org.neo4j.gds.core.utils.mem.MemoryEstimations;
import org.neo4j.gds.core.utils.paged.HugeLongArray;
import org.neo4j.gds.core.utils.paged.ReadOnlyHugeLongArray;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.ml.Training;
import org.neo4j.gds.ml.core.batch.BatchQueue;
import org.neo4j.gds.ml.core.batch.HugeBatchQueue;

import java.util.function.Supplier;

public class NodeLogisticRegressionTrain {

    private final Graph graph;
    private final ReadOnlyHugeLongArray trainSet;
    private final NodeLogisticRegressionTrainConfig config;
    private final ProgressTracker progressTracker;
    private final int concurrency;
    private TerminationFlag terminationFlag;

    public static MemoryEstimation memoryEstimation(
        int numberOfClasses,
        int numberOfFeatures,
        int batchSize
    ) {
        var CONSTANT_NUMBER_OF_WEIGHTS_IN_MODEL_DATA = 1;
        return MemoryEstimations.builder(NodeLogisticRegressionTrain.class)
            .add("model data", NodeLogisticRegressionData.memoryEstimation(numberOfClasses, numberOfFeatures))
            .add("training", Training.memoryEstimation(numberOfFeatures, numberOfClasses, CONSTANT_NUMBER_OF_WEIGHTS_IN_MODEL_DATA))
            .perThread("computation graph", sizeInBytesOfComputationGraph(batchSize, numberOfFeatures, numberOfClasses))
            .build();
    }

    private static long sizeInBytesOfComputationGraph(int batchSize, int numberOfFeatures, int numberOfClasses) {
        return NodeLogisticRegressionObjective.sizeOfBatchInBytes(batchSize, numberOfFeatures, numberOfClasses);
    }

    public NodeLogisticRegressionTrain(
        Graph graph,
        HugeLongArray trainSet,
        NodeLogisticRegressionTrainConfig config,
        ProgressTracker progressTracker,
        TerminationFlag terminationFlag,
        int concurrency
    ) {
        this.graph = graph;
        this.trainSet = ReadOnlyHugeLongArray.of(trainSet);
        this.config = config;
        this.progressTracker = progressTracker;
        this.terminationFlag = terminationFlag;
        this.concurrency = concurrency;
    }

    public NodeLogisticRegressionData compute() {
        var modelData = NodeLogisticRegressionData.from(
            graph,
            config.featureProperties(),
            config.targetProperty()
        );
        var predictor = new NodeLogisticRegressionPredictor(modelData, config.featureProperties());
        var objective = new NodeLogisticRegressionObjective(
            graph,
            predictor,
            config.targetProperty(),
            config.penalty()
        );
        var training = new Training(config, progressTracker, graph.nodeCount(), terminationFlag);
        Supplier<BatchQueue> queueSupplier = () -> new HugeBatchQueue(trainSet, config.batchSize());
        training.train(objective, queueSupplier, concurrency);

        return objective.modelData();
    }
}
