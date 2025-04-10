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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.neo4j.gds.CommunityAlgorithmTasks;
import org.neo4j.gds.CommunityHelper;
import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.Orientation;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.TestProgressTracker;
import org.neo4j.gds.TestProgressTrackerHelper;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.properties.nodes.NodePropertyValues;
import org.neo4j.gds.assertj.Extractors;
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
import org.neo4j.gds.termination.TerminationFlag;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.gds.TestSupport.ids;
import static org.neo4j.gds.compat.TestLog.INFO;
import static org.neo4j.gds.core.ProcedureConstants.TOLERANCE_DEFAULT;
import static org.neo4j.gds.modularityoptimization.ModularityOptimization.K1COLORING_MAX_ITERATIONS;

@GdlExtension
class ModularityOptimizationTest {

    private static final String[][] EXPECTED_SEED_COMMUNITIES = {
        new String[]{"a", "b"},
        new String[]{"c", "e"},
        new String[]{"d", "f"}
    };

    @GdlGraph(orientation = Orientation.UNDIRECTED, idOffset = 0)
    private static final String DB_CYPHER =
        "CREATE" +
        "  (a:Node {seed1:  1,  seed2: 21})" +
        ", (b:Node {seed1: 5})" +
        ", (c:Node {seed1:  2,  seed2: 42})" +
        ", (d:Node {seed1:  3,  seed2: 33})" +
        ", (e:Node {seed1:  2,  seed2: 42})" +
        ", (f:Node {seed1:  3,  seed2: 33})" +

        ", (a)-[:TYPE_OUT {weight: 0.01}]->(b)" +
        ", (a)-[:TYPE_OUT {weight: 5.0}]->(e)" +
        ", (a)-[:TYPE_OUT {weight: 5.0}]->(f)" +
        ", (b)-[:TYPE_OUT {weight: 5.0}]->(c)" +
        ", (b)-[:TYPE_OUT {weight: 5.0}]->(d)" +
        ", (c)-[:TYPE_OUT {weight: 0.01}]->(e)" +
        ", (f)-[:TYPE_OUT {weight: 0.01}]->(d)";

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
            ids(graph::toMappedNodeId, "a", "e", "f"),
            ids(graph::toMappedNodeId, "b", "c", "d")
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
        assertThat(actualCommunities).containsExactlyInAnyOrder(43, 42, 33, 43, 42, 33);
        assertTrue(pmo.ranIterations() <= 3);
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

    @GdlExtension
    @Nested
    class ProgressTrackingTest {

        @GdlGraph(orientation = Orientation.UNDIRECTED)
        private static final String GRAPH =
            """
                CREATE
                  (a:Node {seed1:  1,  seed2: 21}),
                  (b:Node {seed1: 5}),
                  (c:Node {seed1:  2,  seed2: 42}),
                  (d:Node {seed1:  3,  seed2: 33}),
                  (e:Node {seed1:  2,  seed2: 42}),
                  (f:Node {seed1:  3,  seed2: 33}),
                  (a)-[:TYPE_OUT {weight: 0.01}]->(b),
                  (a)-[:TYPE_OUT {weight: 5.0}]->(e),
                  (a)-[:TYPE_OUT {weight: 5.0}]->(f),
                  (b)-[:TYPE_OUT {weight: 5.0}]->(c),
                  (b)-[:TYPE_OUT {weight: 5.0}]->(d),
                  (c)-[:TYPE_OUT {weight: 0.01}]->(e),
                  (f)-[:TYPE_OUT {weight: 0.01}]->(d)
            """;

        @Inject
        private TestGraph graph;

        @Test
        void shouldLogProgress() {
            var concurrency = new Concurrency(3);
            var parameters = new ModularityOptimizationParameters(
                concurrency,
                K1COLORING_MAX_ITERATIONS,
                2,
                0.0001,
                Optional.empty()
            );
            var progressTrackerWithLog = TestProgressTrackerHelper.create(
                new CommunityAlgorithmTasks().modularityOptimization(graph, parameters),
                new Concurrency(2)
            );

            var modularityOptimization = new ModularityOptimization(
                graph,
                parameters,
                DefaultPool.INSTANCE,
                progressTrackerWithLog.progressTracker(),
                TerminationFlag.RUNNING_TRUE
            );
            modularityOptimization.compute();

            var log = progressTrackerWithLog.log();
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

    }

}
