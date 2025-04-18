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
package org.neo4j.gds.triangle;

import org.jetbrains.annotations.Nullable;
import org.neo4j.gds.Algorithm;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.properties.nodes.NodePropertyValues;
import org.neo4j.gds.collections.ha.HugeDoubleArray;
import org.neo4j.gds.collections.haa.HugeAtomicLongArray;
import org.neo4j.gds.core.concurrency.Concurrency;
import org.neo4j.gds.core.concurrency.DefaultPool;
import org.neo4j.gds.core.concurrency.ParallelUtil;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.termination.TerminationFlag;
import org.neo4j.gds.utils.CloseableThreadLocal;

import java.util.Optional;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.LongToDoubleFunction;

public class LocalClusteringCoefficient extends Algorithm<LocalClusteringCoefficientResult> {

    private final Concurrency concurrency;
    private final long maxDegree;
    private final NodePropertyValues triangleCountProperty;

    private final Graph graph;

    // Results
    private HugeDoubleArray localClusteringCoefficients;
    private double averageClusteringCoefficient;

    public LocalClusteringCoefficient(
        Graph graph,
        Concurrency concurrency,
        long maxDegree,
        @Nullable String seedProperty,
        ProgressTracker progressTracker,
        TerminationFlag terminationFlag
    ) {
        super(progressTracker);
        this.graph = graph;
        this.concurrency = concurrency;
        this.maxDegree = maxDegree;

        this.triangleCountProperty =
            Optional.ofNullable(seedProperty)
                .map(graph::nodeProperties)
                .orElse(null);

        this.terminationFlag = terminationFlag;
    }

    public LocalClusteringCoefficient(
        Graph graph,
        LocalClusteringCoefficientParameters parameters,
        ProgressTracker progressTracker,
        TerminationFlag terminationFlag
    ) {
        this(
            graph,
            parameters.concurrency(),
            parameters.maxDegree(),
            parameters.seedProperty(),
            progressTracker,
            terminationFlag
        );
    }

    @Override
    public LocalClusteringCoefficientResult compute() {
        progressTracker.beginSubTask();

        if (null == triangleCountProperty) {
            HugeAtomicLongArray triangleCounts = computeTriangleCounts();
            calculateCoefficients(triangleCounts::get);
        } else {
            calculateCoefficients(triangleCountProperty::doubleValue);
        }

        progressTracker.endSubTask();
        return new LocalClusteringCoefficientResult(
            localClusteringCoefficients,
            averageClusteringCoefficient
        );
    }

    private void calculateCoefficients(LongToDoubleFunction propertyValueFunction) {
        progressTracker.beginSubTask();

        long nodeCount = graph.nodeCount();
        localClusteringCoefficients = HugeDoubleArray.newArray(nodeCount);

        DoubleAdder localClusteringCoefficientSum = new DoubleAdder();

        try (var concurrentGraphCopy = CloseableThreadLocal.withInitial(() -> graph.concurrentCopy())) {
            ParallelUtil.parallelForEachNode(graph.nodeCount(), concurrency, terminationFlag, nodeId -> {
                double localClusteringCoefficient = calculateCoefficient(
                    propertyValueFunction.applyAsDouble(nodeId),
                    graph.isMultiGraph() ?
                        concurrentGraphCopy.get().degreeWithoutParallelRelationships(nodeId) :
                        graph.degree(nodeId)
                );
                localClusteringCoefficients.set(nodeId, localClusteringCoefficient);
                localClusteringCoefficientSum.add(localClusteringCoefficient);
                progressTracker.logProgress();
            });
        }

        // compute average clustering coefficient
        averageClusteringCoefficient = localClusteringCoefficientSum.doubleValue() / nodeCount;

        progressTracker.endSubTask();
    }

    private HugeAtomicLongArray computeTriangleCounts() {
        var intersectingTriangleCount = IntersectingTriangleCount.create(
            graph,
            concurrency,
            maxDegree,
            DefaultPool.INSTANCE,
            progressTracker,
            TerminationFlag.RUNNING_TRUE
        );

        return intersectingTriangleCount.compute().localTriangles();
    }

    private double calculateCoefficient(double triangles, int degree) {
        if (Double.isNaN(triangles) || triangles == IntersectingTriangleCount.EXCLUDED_NODE_TRIANGLE_COUNT) {
            return Double.NaN;
        }

        if (triangles == 0) {
            return 0.0;
        }

        // local clustering coefficient C(v) = 2 * triangles(v) / (degree(v) * (degree(v) - 1))
        return triangles * 2 / (degree * (degree - 1));
    }

}
