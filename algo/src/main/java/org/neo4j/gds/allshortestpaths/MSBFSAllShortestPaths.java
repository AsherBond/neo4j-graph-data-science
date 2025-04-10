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
package org.neo4j.gds.allshortestpaths;

import org.neo4j.gds.api.Graph;
import org.neo4j.gds.core.concurrency.Concurrency;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.msbfs.MultiSourceBFSAccessMethods;
import org.neo4j.gds.termination.TerminationFlag;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;

/**
 * AllShortestPaths:
 * <p>
 * multi-source parallel shortest path between each pair of nodes.
 * <p>
 * Due to the high memory footprint the result set would have we emit each result into
 * a blocking queue. The result stream takes elements from the queue while the workers
 * add elements to it.
 */
public class MSBFSAllShortestPaths extends MSBFSASPAlgorithm {
    private final BlockingQueue<AllShortestPathsStreamResult> resultQueue = new LinkedBlockingQueue<>();

    private final Graph graph;
    private final Concurrency concurrency;
    private final ExecutorService executorService;

    public MSBFSAllShortestPaths(
        Graph graph,
        Concurrency concurrency,
        ExecutorService executorService,
        TerminationFlag terminationFlag
    ) {
        super(ProgressTracker.NULL_TRACKER);
        this.graph = graph;
        this.concurrency = concurrency;
        this.executorService = executorService;

        this.terminationFlag = terminationFlag;
    }

    /**
     * the compute(..) method starts the computation and
     * returns a Stream of SP-Tuples (source, target, minDist)
     *
     * @return the result stream
     */
    @Override
    public Stream<AllShortestPathsStreamResult> compute() {
        progressTracker.beginSubTask();
        executorService.submit(new ShortestPathTask(concurrency, executorService));
        return AllShortestPathsStream.stream(resultQueue, progressTracker::endSubTask);
    }

    /**
     * Dijkstra Task. Takes one element of the counter at a time
     * and starts dijkstra on it. It starts emitting results to the
     * queue once all reachable nodes have been visited.
     */
    private final class ShortestPathTask implements Runnable {

        private final Concurrency concurrency;
        private final ExecutorService executorService;

        private ShortestPathTask(
                Concurrency concurrency,
                ExecutorService executorService) {
            this.concurrency = concurrency;
            this.executorService = executorService;
        }

        @Override
        public void run() {
            MultiSourceBFSAccessMethods.aggregatedNeighborProcessing(
                    graph.nodeCount(),
                    graph,
                    (target, distance, sources) -> {
                        while (sources.hasNext()) {
                            long source = sources.nextLong();
                            var result = new AllShortestPathsStreamResult(
                                graph.toOriginalNodeId(source),
                                graph.toOriginalNodeId(target),
                                distance
                            );
                            try {
                                resultQueue.put(result);
                            } catch (InterruptedException e) {
                                // notify JVM of the interrupt
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            }
                        }
                        progressTracker.logProgress();
                    },
                Optional.empty(),
                terminationFlag
            ).run(concurrency, executorService);

            resultQueue.add(AllShortestPathsStreamResult.DONE);
        }
    }
}
