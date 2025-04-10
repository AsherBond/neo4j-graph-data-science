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
package org.neo4j.gds.modularityoptimization;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.gds.CommunityHelper;
import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.TestProgressTracker;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.properties.nodes.NodePropertyValues;
import org.neo4j.gds.assertj.Extractors;
import org.neo4j.gds.collections.ha.HugeDoubleArray;
import org.neo4j.gds.collections.ha.HugeLongArray;
import org.neo4j.gds.collections.haa.HugeAtomicDoubleArray;
import org.neo4j.gds.core.GraphDimensions;
import org.neo4j.gds.core.ImmutableGraphDimensions;
import org.neo4j.gds.core.concurrency.Concurrency;
import org.neo4j.gds.core.concurrency.DefaultPool;
import org.neo4j.gds.core.utils.logging.LoggerForProgressTrackingAdapter;
import org.neo4j.gds.core.utils.progress.EmptyTaskRegistryFactory;
import org.neo4j.gds.extension.GdlExtension;
import org.neo4j.gds.extension.GdlGraph;
import org.neo4j.gds.extension.IdFunction;
import org.neo4j.gds.extension.Inject;
import org.neo4j.gds.extension.TestGraph;
import org.neo4j.gds.logging.GdsTestLog;
import org.neo4j.gds.logging.Log;
import org.neo4j.gds.mem.Estimate;
import org.neo4j.gds.mem.MemoryEstimations;
import org.neo4j.gds.mem.MemoryRange;
import org.neo4j.gds.mem.MemoryTree;
import org.neo4j.gds.termination.TerminationFlag;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.neo4j.gds.TestSupport.ids;
import static org.neo4j.gds.compat.TestLog.INFO;
import static org.neo4j.gds.core.ProcedureConstants.TOLERANCE_DEFAULT;
import static org.neo4j.gds.modularityoptimization.ModularityOptimization.K1COLORING_MAX_ITERATIONS;

@GdlExtension
class ModularityOptimizationWithoutOrientationTest {

    private static final String[][] EXPECTED_SEED_COMMUNITIES = {
        new String[]{"a", "b"},
        new String[]{"c", "e"},
        new String[]{"d", "f"}
    };

    @GdlGraph
    private static final String DB_CYPHER =
        "CREATE" +
        "  (a:Node {seed1:  1,  seed2: 2121})" +
        ", (b:Node {seed1: 5})" +
        ", (c:Node {seed1:  2,  seed2: 4242})" +
        ", (d:Node {seed1:  3,  seed2: 3333})" +
        ", (e:Node {seed1:  2,  seed2: 4242})" +
        ", (f:Node {seed1:  3,  seed2: 3333})" +

        ", (a)-[:TYPE_OUT {weight: 0.01}]->(b)" +
        ", (a)-[:TYPE_OUT {weight: 5.0}]->(e)" +
        ", (a)-[:TYPE_OUT {weight: 5.0}]->(f)" +
        ", (b)-[:TYPE_OUT {weight: 5.0}]->(c)" +
        ", (b)-[:TYPE_OUT {weight: 5.0}]->(d)" +
        ", (c)-[:TYPE_OUT {weight: 0.01}]->(e)" +
        ", (f)-[:TYPE_OUT {weight: 0.01}]->(d)" +

        ", (a)<-[:TYPE_OUT {weight: 0.01}]-(b)" +
        ", (a)<-[:TYPE_OUT {weight: 5.0}]-(e)" +
        ", (a)<-[:TYPE_OUT {weight: 5.0}]-(f)" +
        ", (b)<-[:TYPE_OUT {weight: 5.0}]-(c)" +
        ", (b)<-[:TYPE_OUT {weight: 5.0}]-(d)" +
        ", (c)<-[:TYPE_OUT {weight: 0.01}]-(e)" +
        ", (f)<-[:TYPE_OUT {weight: 0.01}]-(d)";

    @Inject
    private TestGraph graph;

    @Inject
    private GraphStore graphStore;

    @Inject
    private IdFunction idFunction;

    IdFunction mappedId = name -> graphStore.nodes().toMappedNodeId(idFunction.of(name));

    @Test
    void testUnweighted() {
        var graph = unweightedGraph();

        var pmo = compute(graph, 3, null, new Concurrency(1), 10_000);

        assertEquals(0.12244, pmo.modularity(), 0.001);
        CommunityHelper.assertCommunities(
            getCommunityIds(graph.nodeCount(), pmo),
            ids(mappedId, "a", "b", "c", "e"),
            ids(mappedId, "d", "f")
        );
        assertTrue(pmo.ranIterations() <= 3);
    }

    @Test
    void testWeighted() {
        var pmo = compute(graph, 3, null, new Concurrency(3), 2);

        assertEquals(0.4985, pmo.modularity(), 0.001);
        CommunityHelper.assertCommunities(
            getCommunityIds(graph.nodeCount(), pmo),
            ids(mappedId, "a", "e", "f"),
            ids(mappedId, "b", "c", "d")
        );
        assertTrue(pmo.ranIterations() <= 3);
    }

    @Test
    void testSeedingWithBiggerSeedValues() {
        var graph = unweightedGraph();

        var pmo = compute(
            graph,
            10, graph.nodeProperties("seed2"),
            new Concurrency(1),
            100
        );

        long[] actualCommunities = getCommunityIds(graph.nodeCount(), pmo);
        assertEquals(0.0816, pmo.modularity(), 0.001);
        CommunityHelper.assertCommunities(actualCommunities, ids(mappedId, EXPECTED_SEED_COMMUNITIES));

        assertThat(actualCommunities)
            // this is a new color based on the largest seed value and the original id
            .matches(communities -> communities[0] == 5580L)
            .matches(communities -> communities[1] == 5580L)
            // these are the seed communities
            .matches(communities -> communities[2] == 4242)
            .matches(communities -> communities[3] == 3333);

        assertThat(pmo.ranIterations()).isLessThanOrEqualTo(3);
    }

    @Test
    void testSeeding() {
        var graph = unweightedGraph();

        var pmo = compute(
            graph,
            10, graph.nodeProperties("seed1"),
            new Concurrency(1),
            100
        );

        long[] actualCommunities = getCommunityIds(graph.nodeCount(), pmo);
        assertEquals(0.0816, pmo.modularity(), 0.001);
        CommunityHelper.assertCommunities(actualCommunities, ids(mappedId, EXPECTED_SEED_COMMUNITIES));
        assertTrue(actualCommunities[0] == 4 && actualCommunities[2] == 2 || actualCommunities[3] == 3);
        assertTrue(pmo.ranIterations() <= 3);
    }

    private long[] getCommunityIds(long nodeCount, ModularityOptimizationResult pmo) {
        long[] communityIds = new long[(int) nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            communityIds[i] = pmo.communityId(i);
        }
        return communityIds;
    }

    @Test
    void testLogging() {
        var log = new GdsTestLog();

        compute(graph, K1COLORING_MAX_ITERATIONS, null, new Concurrency(3), 2, log);

        assertThat(log.getMessages(INFO))
            .extracting(Extractors.removingThreadId())
            .contains(
                "ModularityOptimization :: Start",
                "ModularityOptimization :: initialization :: K1Coloring :: color nodes 1 of 5 :: Start",
                "ModularityOptimization :: initialization :: K1Coloring :: color nodes 1 of 5 :: Finished",
                "ModularityOptimization :: initialization :: K1Coloring :: validate nodes 1 of 5 :: Start",
                "ModularityOptimization :: initialization :: K1Coloring :: validate nodes 1 of 5 :: Finished",
                "ModularityOptimization :: compute modularity :: optimizeForColor 1 of 5 :: Start",
                "ModularityOptimization :: compute modularity :: optimizeForColor 1 of 5 :: Finished",
                "ModularityOptimization :: Finished"
            );
    }

    @ParameterizedTest
    @MethodSource("memoryEstimationTuples")
    void testMemoryEstimation(int concurrencyValue, long min, long max) {
        var concurrency = new Concurrency(concurrencyValue);
        GraphDimensions dimensions = ImmutableGraphDimensions.builder().nodeCount(100_000L).build();
        MemoryTree memoryTree = MemoryEstimations.builder(ModularityOptimization.class)
            .perNode("currentCommunities", HugeLongArray::memoryEstimation)
            .perNode("nextCommunities", HugeLongArray::memoryEstimation)
            .perNode("cumulativeNodeWeights", HugeDoubleArray::memoryEstimation)
            .perNode("nodeCommunityInfluences", HugeDoubleArray::memoryEstimation)
            .perNode("communityWeights", HugeAtomicDoubleArray::memoryEstimation)
            .perNode("colorsUsed", Estimate::sizeOfBitset)
            .perNode("colors", HugeLongArray::memoryEstimation)
            .rangePerNode(
                "reversedSeedCommunityMapping", (nodeCount) ->
                    MemoryRange.of(0, HugeLongArray.memoryEstimation(nodeCount))
            )
            .perNode("communityWeightUpdates", HugeAtomicDoubleArray::memoryEstimation)
            .perThread("ModularityOptimizationTask", MemoryEstimations.builder()
                .rangePerNode(
                    "communityInfluences",
                    (nodeCount) -> MemoryRange.of(
                        Estimate.sizeOfLongDoubleHashMap(50),
                        Estimate.sizeOfLongDoubleHashMap(Math.max(50, nodeCount))
                    )
                )
                .build()
            )
            .build()
            .estimate(dimensions, concurrency);
        assertEquals(min, memoryTree.memoryUsage().min);
        assertEquals(max, memoryTree.memoryUsage().max);
    }

    static Stream<Arguments> memoryEstimationTuples() {
        return Stream.of(
            arguments(1, 5614032, 8413064),
            arguments(4, 5617320, 14413328),
            arguments(42, 5658968, 90416672)
        );
    }

    @NotNull
    private ModularityOptimizationResult compute(
        Graph graph,
        int maxIterations,
        NodePropertyValues properties,
        Concurrency concurrency,
        int minBatchSize
    ) {
        return compute(graph, maxIterations, properties, concurrency, minBatchSize, new GdsTestLog());
    }

    @NotNull
    private ModularityOptimizationResult compute(
        Graph graph,
        int maxIterations,
        NodePropertyValues properties,
        Concurrency concurrency,
        int minBatchSize,
        Log log
    ) {
        var progressTask = ModularityOptimizationProgressTrackerTaskCreator.progressTask(
            graph.nodeCount(),
            graph.relationshipCount(),
            maxIterations
        );
        var progressTracker = new TestProgressTracker(progressTask, new LoggerForProgressTrackingAdapter(log), concurrency, EmptyTaskRegistryFactory.INSTANCE);

        return new ModularityOptimization(
            graph,
            maxIterations,
            TOLERANCE_DEFAULT,
            properties,
            concurrency,
            minBatchSize,
            DefaultPool.INSTANCE,
            progressTracker,
            TerminationFlag.RUNNING_TRUE
        ).compute();
    }

    private Graph unweightedGraph() {
        return graphStore.getGraph(
            NodeLabel.listOf("Node"),
            RelationshipType.listOf("TYPE_OUT"),
            Optional.empty()
        );
    }
}
