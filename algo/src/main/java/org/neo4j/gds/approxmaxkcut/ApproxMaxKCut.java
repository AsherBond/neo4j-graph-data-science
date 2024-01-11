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
package org.neo4j.gds.approxmaxkcut;

import org.neo4j.gds.Algorithm;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.approxmaxkcut.localsearch.LocalSearch;
import org.neo4j.gds.collections.ha.HugeByteArray;
import org.neo4j.gds.core.concurrency.AtomicDouble;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;

import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLongArray;

/*
 * Implements a parallelized version of a GRASP (optionally with VNS) maximum k-cut approximation algorithm.
 *
 * A serial version of the algorithm with a slightly different construction phase is outlined in [1] as GRASP(+VNS) for
 * k = 2, and is known as FES02G(V) in [2] which benchmarks it against a lot of other algorithms, also for k = 2.
 *
 * TODO: Add the path-relinking heuristic for possibly slightly better results when running single-threaded (basically
 *  making the algorithm GRASP+VNS+PR in [1] and FES02GVP in [2]).
 *
 * [1]: Festa et al. Randomized Heuristics for the Max-Cut Problem, 2002.
 * [2]: Dunning et al. What Works Best When? A Systematic Evaluation of Heuristics for Max-Cut and QUBO, 2018.
 */
public class ApproxMaxKCut extends Algorithm<ApproxMaxKCutResult> {
    public static final String APPROX_MAX_K_CUT_DESCRIPTION = "Approximate Maximum k-cut maps each node into one of k disjoint communities trying to maximize the sum of weights of relationships between these communities.";

    private static final Comparator MINIMIZING = (lhs, rhs) -> lhs < rhs;
    private static final Comparator MAXIMIZING = (lhs, rhs) -> lhs > rhs;

    private final Graph graph;
    private final SplittableRandom random;
    private final Comparator comparator;
    private final PlaceNodesRandomly placeNodesRandomly;
    private final LocalSearch localSearch;
    private final HugeByteArray[] candidateSolutions;
    private final AtomicDouble[] costs;
    private final int vnsMaxNeighborhoodOrder;
    private final List<Long> minCommunitySizes;
    private final byte k;
    private final int iterations;
    private VariableNeighborhoodSearch variableNeighborhoodSearch;
    private AtomicLongArray currentCardinalities;

    public ApproxMaxKCut(
        Graph graph,
        ExecutorService executor,
        byte k,
        int iterations,
        int vnsMaxNeighborhoodOrder,
        int concurrency,
        int minBatchSize,
        Optional<Long> randomSeed,
        List<Long> minCommunitySizes,
        boolean hasRelationshipWeightProperty,
        boolean minimize,
        ProgressTracker progressTracker
    ) {
        super(progressTracker);
        this.random = new SplittableRandom(randomSeed.orElseGet(() -> new SplittableRandom().nextLong()));
        this.graph = graph;
        this.comparator = minimize ? MINIMIZING : MAXIMIZING;

        this.k = k;
        this.iterations = iterations;
        this.minCommunitySizes = minCommunitySizes;
        this.vnsMaxNeighborhoodOrder = vnsMaxNeighborhoodOrder;
        // We allocate two arrays in order to be able to compare results between iterations "GRASP style".
        this.candidateSolutions = new HugeByteArray[]{
            HugeByteArray.newArray(graph.nodeCount()),
            HugeByteArray.newArray(graph.nodeCount())
        };

        this.costs = new AtomicDouble[]{
            new AtomicDouble(),
            new AtomicDouble(),
        };
        costs[0].set(minimize ? Double.MAX_VALUE : Double.MIN_VALUE);
        costs[1].set(minimize ? Double.MAX_VALUE : Double.MIN_VALUE);

        this.currentCardinalities = new AtomicLongArray(k);

        this.placeNodesRandomly = new PlaceNodesRandomly(
            concurrency,
            k,
            minCommunitySizes,
            minBatchSize,
            random,
            graph,
            executor,
            progressTracker
        );
        this.localSearch = new LocalSearch(
            graph,
            comparator,
            concurrency,
            k,
            minCommunitySizes,
            minBatchSize,
            hasRelationshipWeightProperty,
            executor,
            progressTracker
        );
    }

    @FunctionalInterface
    public interface Comparator {
        boolean compare(double lhs, double rhs);
    }

    @Override
    public ApproxMaxKCutResult compute() {
        // Keep track of which candidate solution is currently being used and which is best.
        byte currIdx = 0, bestIdx = 1;

        progressTracker.beginSubTask();

        if (vnsMaxNeighborhoodOrder > 0) {
            this.variableNeighborhoodSearch = new VariableNeighborhoodSearch(
                graph,
                random,
                comparator,
                vnsMaxNeighborhoodOrder,
                minCommunitySizes,
                k,
                localSearch,
                candidateSolutions,
                costs,
                progressTracker
            );
        }

        for (int i = 1; (i <= iterations) && terminationFlag.running(); i++) {
            var currCandidateSolution = candidateSolutions[currIdx];
            var currCost = costs[currIdx];

            placeNodesRandomly.compute(currCandidateSolution, currentCardinalities);

            if (!terminationFlag.running()) break;

            if (vnsMaxNeighborhoodOrder > 0) {
                currentCardinalities = variableNeighborhoodSearch.compute(
                    currIdx,
                    currentCardinalities,
                    terminationFlag::running
                );
            } else {
                localSearch.compute(
                    currCandidateSolution,
                    currCost,
                    currentCardinalities,
                    terminationFlag::running
                );
            }

            // Store the newly computed candidate solution if it was better than the previous. Then reuse the previous data
            // structures to make a new solution candidate if we are doing more iterations.
            if (comparator.compare(currCost.get(), costs[bestIdx].get())) {
                var tmp = bestIdx;
                bestIdx = currIdx;
                currIdx = tmp;
            }
        }

        progressTracker.endSubTask();

        return ApproxMaxKCutResult.of(candidateSolutions[bestIdx], costs[bestIdx].get());
    }

}
