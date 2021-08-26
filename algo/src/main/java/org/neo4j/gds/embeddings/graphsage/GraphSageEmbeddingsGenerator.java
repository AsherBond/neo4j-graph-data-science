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
package org.neo4j.gds.embeddings.graphsage;

import org.neo4j.gds.ml.core.ComputationContext;
import org.neo4j.gds.ml.core.Variable;
import org.neo4j.gds.ml.core.tensor.Matrix;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.concurrency.ParallelUtil;
import org.neo4j.graphalgo.core.utils.ProgressLogger;
import org.neo4j.graphalgo.core.utils.mem.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.HugeObjectArray;
import org.neo4j.graphalgo.core.utils.partition.Partition;
import org.neo4j.graphalgo.core.utils.partition.PartitionUtils;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;

import static org.neo4j.gds.embeddings.graphsage.GraphSageHelper.embeddings;

public class GraphSageEmbeddingsGenerator {
    private final Layer[] layers;
    private final int batchSize;
    private final int concurrency;
    private final boolean isWeighted;
    private final FeatureFunction featureFunction;
    private final ExecutorService executor;
    private final ProgressLogger progressLogger;
    private final AllocationTracker tracker;

    public GraphSageEmbeddingsGenerator(
        Layer[] layers,
        int batchSize,
        int concurrency,
        boolean isWeighted,
        FeatureFunction featureFunction,
        ExecutorService executor,
        ProgressLogger progressLogger,
        AllocationTracker tracker
    ) {
        this.layers = layers;
        this.batchSize = batchSize;
        this.concurrency = concurrency;
        this.isWeighted = isWeighted;
        this.featureFunction = featureFunction;
        this.executor = executor;
        this.progressLogger = progressLogger;
        this.tracker = tracker;
    }

    public HugeObjectArray<double[]> makeEmbeddings(
        Graph graph,
        HugeObjectArray<double[]> features
    ) {
        HugeObjectArray<double[]> result = HugeObjectArray.newArray(
            double[].class,
            graph.nodeCount(),
            tracker
        );

        progressLogger.logStart();

        var tasks = PartitionUtils.rangePartition(
            concurrency,
            graph.nodeCount(),
            batchSize,
            partition -> createEmbeddings(graph, partition, features, result)
        );

        ParallelUtil.runWithConcurrency(concurrency, tasks, executor);

        progressLogger.logFinish();

        return result;
    }

    private Runnable createEmbeddings(
        Graph graph,
        Partition partition,
        HugeObjectArray<double[]> features,
        HugeObjectArray<double[]> result
    ) {
        return () -> {
            ComputationContext ctx = new ComputationContext();
            Variable<Matrix> embeddingVariable = embeddings(
                graph,
                isWeighted,
                partition.stream().toArray(),
                features,
                layers,
                featureFunction
            );
            int cols = embeddingVariable.dimension(1);
            double[] embeddings = ctx.forward(embeddingVariable).data();

            var partitionStartNodeId = partition.startNode();
            var partitionNodeCount = partition.nodeCount();
            for (int nodeIndex = 0; nodeIndex < partitionNodeCount; nodeIndex++) {
                // TODO: Try to avoid `Arrays.copyOfRange`
                double[] nodeEmbedding = Arrays.copyOfRange(
                    embeddings,
                    nodeIndex * cols,
                    (nodeIndex + 1) * cols
                );

                result.set(nodeIndex + partitionStartNodeId, nodeEmbedding);
            }

            progressLogger.logProgress();
        };
    }
}
