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
package org.neo4j.gds.impl;

import org.neo4j.gds.Algorithm;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.core.concurrency.ParallelUtil;
import org.neo4j.gds.core.utils.container.Buckets;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.neo4j.gds.impl.Converters.longToIntConsumer;

/**
 * parallel non-negative single source shortest path algorithm
 * <p>
 * Delta-Stepping is a parallel non-negative single source shortest paths (NSSSP) algorithm
 * to calculate the length of the shortest paths from a starting node to all other
 * nodes in the graph. It can be tweaked using the delta-parameter which controls
 * the grade of concurrency.<br>
 * <p>
 * More information in:<br>
 * <p>
 * <a href="https://arxiv.org/pdf/1604.02113v1.pdf">https://arxiv.org/pdf/1604.02113v1.pdf</a><br>
 * <a href="https://ae.cs.uni-frankfurt.de/pdf/diss_uli.pdf">https://ae.cs.uni-frankfurt.de/pdf/diss_uli.pdf</a><br>
 * <a href="http://www.cc.gatech.edu/~bader/papers/ShortestPaths-ALENEX2007.pdf">http://www.cc.gatech.edu/~bader/papers/ShortestPaths-ALENEX2007.pdf</a><br>
 * <a href="http://www.dis.uniroma1.it/challenge9/papers/madduri.pdf">http://www.dis.uniroma1.it/challenge9/papers/madduri.pdf</a>
 */
public class ShortestPathDeltaStepping extends Algorithm<ShortestPathDeltaStepping, ShortestPathDeltaStepping> {

    // distance array
    private final AtomicLongArray distance;
    // bucket impl
    private Buckets buckets;
    private final Graph graph;
    // collections of runnables containing either heavy edge relax-operations or light edge relax-ops.
    private Collection<Runnable> light, heavy;
    // list of futures of light and heavy edge relax-operations
    private Collection<Future<?>> futures;

    private final int startNode;
    // delta parameter
    private final double delta;
    private final int nodeCount;
    // scaled delta
    private final int iDelta;

    private ExecutorService executorService;

    // multiplier used to scale an double to int
    private final double multiplier = 100_000D; // double type is intended

    public ShortestPathDeltaStepping(Graph graph, long startNode, double delta, ProgressTracker progressTracker) {
        this.progressTracker = progressTracker;
        this.graph = graph;
        this.startNode = Math.toIntExact(graph.toMappedNodeId(startNode));
        this.delta = delta;
        this.iDelta = Math.toIntExact(Math.round(multiplier * delta));
        if (iDelta <= 0) {
            throw new IllegalArgumentException("Choose a higher delta value");
        }

        nodeCount = Math.toIntExact(graph.nodeCount());
        distance = new AtomicLongArray(nodeCount);
        buckets = new Buckets(nodeCount);
        heavy = new ArrayDeque<>(1024);
        light = new ArrayDeque<>(1024);
        futures = new ArrayDeque<>(128);
    }

    /**
     * Set Executor-service to enable concurrent evaluation.
     *
     * @param executorService the executor service or null do disable concurrent eval.
     * @return itself for method chaining
     */
    public ShortestPathDeltaStepping withExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
        return this;
    }

    @Override
    public ShortestPathDeltaStepping compute() {
        progressTracker.beginSubTask();

        // reset
        for (int i = 0; i < nodeCount; i++) {
            distance.set(i, Long.MAX_VALUE);
        }
        buckets.reset();

        // basically assign start node to bucket 0
        relax(startNode, 0);

        // as long as the bucket contains any value
        while (!buckets.isEmpty() && running()) {
            // reset temporary arrays
            light.clear();
            heavy.clear();

            // get next bucket index
            final int phase = buckets.nextNonEmptyBucket();

            // for each node in bucket
            buckets.forEachInBucket(phase, node -> {
                // relax each outgoing light edge
                graph.forEachRelationship(node, 0.0D, longToIntConsumer((sourceNodeId, targetNodeId, cost) -> {
                    final long iCost = Math.round(cost * multiplier + distance.get(sourceNodeId));
                    if (cost <= delta) { // determine if light or heavy edge
                        light.add(() -> relax(targetNodeId, iCost));
                    } else {
                        heavy.add(() -> relax(targetNodeId, iCost));
                    }
                    return true;
                }));
                return true;
            });
            ParallelUtil.run(light, executorService, futures);
            ParallelUtil.run(heavy, executorService, futures);

            progressTracker.logProgress();
        }

        progressTracker.endSubTask();

        return this;
    }

    /**
     * get downscaled sum of distance
     *
     * @param nodeId the mapped node-id
     * @return the overall distance from source to nodeId
     */
    private double get(int nodeId) {
        long distance = this.distance.get(nodeId);
        return distance == Long.MAX_VALUE ? Double.POSITIVE_INFINITY : distance / multiplier;
    }

    /**
     * compare and set. tries to store the new calculated costs
     * as long as no other thread has already written a value
     * smaller then cost. if another thread writes a value bigger
     * then cost the loop tries again otherwise the function
     * terminates.
     *
     * @param nodeId
     * @param cost
     */
    private void compareAndSet(int nodeId, long cost) {
        boolean stored = false;
        while (!stored) {
            long oldC = distance.get(nodeId);
            if (cost < oldC) {
                stored = distance.compareAndSet(nodeId, oldC, cost);
            } else {
                break;
            }
        }
    }

    /**
     * relax() sets the summed cost in {@link ShortestPathDeltaStepping#distance}
     * if they are smaller then the current cost - like dijkstra. If so it also
     * calculates the next bucket index for the node and assigns it.
     *
     * @param nodeId node id
     * @param cost   the summed cost
     */
    private void relax(int nodeId, long cost) {
        if (cost < distance.get(nodeId)) {
            int bucketIndex = Math.toIntExact((cost / iDelta)); // calculate bucket index
            buckets.set(nodeId, bucketIndex);
            compareAndSet(nodeId, cost);
        }
    }

    /**
     * scale down integer representation to double[]
     *
     * @return mapped-id to costSum array
     */
    public double[] getShortestPaths() {
        double[] d = new double[nodeCount];
        for (int i = nodeCount - 1; i >= 0; i--) {
            d[i] = get(i);
        }
        return d;
    }

    /**
     * stream the results
     *
     * @return Stream of results containing neo4j-NodeId and Sum of Costs of the shortest path
     */
    public Stream<DeltaSteppingResult> resultStream() {
        return IntStream.range(0, nodeCount)
                .mapToObj(node ->
                        new DeltaSteppingResult(graph.toOriginalNodeId(node), get(node)));
    }

    @Override
    public ShortestPathDeltaStepping me() {
        return this;
    }

    @Override
    public void release() {
        buckets = null;
        light = null;
        heavy = null;
        futures = null;
    }

    /**
     * Basic result DTO
     */
    public static class DeltaSteppingResult {

        /**
         * the neo4j node id
         */
        public final long nodeId;
        /**
         * minimum distance from startNode to nodeId
         */
        public final double distance;

        public DeltaSteppingResult(long nodeId, double distance) {
            this.nodeId = nodeId;
            this.distance = distance;
        }

        @Override
        public String toString() {
            return "DeltaSteppingResult{" +
                   "nodeId=" + nodeId +
                   ", distance=" + distance +
                   '}';
        }
    }
}
