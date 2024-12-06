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
package org.neo4j.gds.pricesteiner;

import com.carrotsearch.hppc.BitSet;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.gds.Orientation;
import org.neo4j.gds.collections.ha.HugeDoubleArray;
import org.neo4j.gds.collections.ha.HugeLongArray;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.extension.GdlExtension;
import org.neo4j.gds.extension.GdlGraph;
import org.neo4j.gds.extension.Inject;
import org.neo4j.gds.extension.TestGraph;
import org.neo4j.gds.termination.TerminationFlag;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.LongToDoubleFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;


@GdlExtension
class StrongPruningTest {

    @GdlExtension
    @Nested
    class FirstGraph {
        @GdlGraph(orientation = Orientation.UNDIRECTED)
        private static final String DB_CYPHER =
            "CREATE " +
                "  (a1:node)," +
                "  (a2:node)," +
                "  (a3:node)," +
                "  (a4:node)," +
                "  (a5:node)," +
                "  (a6:node)," +
                "  (a7:node)," +
                "  (a8:node)," +
                "  (a9:node)," +

                "(a2)-[:R{w:0}]->(a1)," +
                "(a1)-[:R{w:100}]->(a3)," +
                "(a2)-[:R{w:8}]->(a4)," +
                "(a2)-[:R{w:100}]->(a5)," +
                "(a4)-[:R{w:3}]->(a6)," +
                "(a4)-[:R{w:4}]->(a7)," +
                "(a5)-[:R{w:100}]->(a8)," +
                "(a5)-[:R{w:100}]->(a9)";

        @Inject
        private TestGraph graph;

        @Test
        void shouldPruneCorrectly(){
            var bitSet = new BitSet(graph.nodeCount());
            for (int i = 0; i < graph.nodeCount(); ++i) {
                bitSet.set(i);
            }

            var strongPruning = new StrongPruning(
                new TreeStructure(
                    graph,
                    null,
                    graph.nodeCount()
                ),
                bitSet,
                (x) -> 2,
                ProgressTracker.NULL_TRACKER,
                TerminationFlag.RUNNING_TRUE
            );

            var parents=HugeLongArray.newArray(graph.nodeCount());
            Function<Integer,Long> a =  (v) -> graph.toMappedNodeId("a"+v);
            parents.set(a.apply(2), PrizeSteinerTreeResult.ROOT);
            parents.set(a.apply(4),a.apply(2));
            parents.set(a.apply(6),a.apply(4));
            parents.set(a.apply(7), PrizeSteinerTreeResult.ROOT);
            parents.set(a.apply(1), PrizeSteinerTreeResult.ROOT);
            parents.set(a.apply(3),a.apply(1));
            parents.set(a.apply(5), PrizeSteinerTreeResult.ROOT);
            parents.set(a.apply(8),a.apply(5));
            parents.set(a.apply(9), PrizeSteinerTreeResult.ROOT);

            strongPruning.pruneUnnecessarySubTrees(a.apply(5),HugeLongArray.newArray(graph.nodeCount()),parents);
            assertThat(parents.get(a.apply(1))).isEqualTo(PrizeSteinerTreeResult.PRUNED);
            assertThat(parents.get(a.apply(2))).isEqualTo(PrizeSteinerTreeResult.PRUNED);
            assertThat(parents.get(a.apply(3))).isEqualTo(PrizeSteinerTreeResult.PRUNED);
            assertThat(parents.get(a.apply(4))).isEqualTo(PrizeSteinerTreeResult.PRUNED);
            assertThat(parents.get(a.apply(5))).isEqualTo(PrizeSteinerTreeResult.ROOT);
            assertThat(parents.get(a.apply(6))).isEqualTo(PrizeSteinerTreeResult.PRUNED);
            assertThat(parents.get(a.apply(7))).isEqualTo(PrizeSteinerTreeResult.PRUNED);
            assertThat(parents.get(a.apply(8))).isEqualTo(a.apply(5));
            assertThat(parents.get(a.apply(9))).isEqualTo(PrizeSteinerTreeResult.PRUNED);

        }

        @Test
        void shouldApplyDynamicProgrammingProperly() {
            var bitSet = new BitSet(graph.nodeCount());
            for (int i = 0; i < graph.nodeCount(); ++i) {
                bitSet.set(i);
            }

            HugeLongArray degrees = HugeLongArray.newArray(graph.nodeCount());
            degrees.setAll(v -> graph.degree(v));

            HugeDoubleArray prizes = HugeDoubleArray.newArray(graph.nodeCount());
            for (long u = 1; u <= 9; ++u) {
                prizes.set(graph.toMappedNodeId("a" + u), u);
            }
            prizes.set(graph.toMappedNodeId("a2"),10);

            var strongPruning = new StrongPruning(
                new TreeStructure(graph, degrees, graph.nodeCount()),
                bitSet,
                prizes::get,
                ProgressTracker.NULL_TRACKER,
                TerminationFlag.RUNNING_TRUE

            );

            strongPruning.performPruning();

            var resultTree = strongPruning.resultTree();
            assertThat(resultTree.parentArray().get(graph.toMappedNodeId("a1"))).isEqualTo(graph.toMappedNodeId("a2"));
            assertThat(resultTree.parentArray().get(graph.toMappedNodeId("a4"))).isEqualTo(graph.toMappedNodeId("a2"));
            assertThat(resultTree.parentArray().get(graph.toMappedNodeId("a6"))).isEqualTo(graph.toMappedNodeId("a4"));
            assertThat(resultTree.parentArray().get(graph.toMappedNodeId("a7"))).isEqualTo(graph.toMappedNodeId("a4"));
            assertThat(resultTree.relationshipToParentCost().get(graph.toMappedNodeId("a1"))).isEqualTo(0);
            assertThat(resultTree.relationshipToParentCost().get(graph.toMappedNodeId("a4"))).isEqualTo(8);
            assertThat(resultTree.relationshipToParentCost().get(graph.toMappedNodeId("a6"))).isEqualTo(3);
            assertThat(resultTree.relationshipToParentCost().get(graph.toMappedNodeId("a7"))).isEqualTo(4);

            assertThat(resultTree.parentArray().get(graph.toMappedNodeId("a5"))).isEqualTo(PrizeSteinerTreeResult.PRUNED);
            assertThat(resultTree.parentArray().get(graph.toMappedNodeId("a8"))).isEqualTo(PrizeSteinerTreeResult.PRUNED);
            assertThat(resultTree.parentArray().get(graph.toMappedNodeId("a9"))).isEqualTo(PrizeSteinerTreeResult.PRUNED);
            assertThat(resultTree.parentArray().get(graph.toMappedNodeId("a3"))).isEqualTo(PrizeSteinerTreeResult.PRUNED);

            assertThat(resultTree.effectiveNodeCount()).isEqualTo(5);
            assertThat(resultTree.sumOfPrizes()).isEqualTo(28.0);
            assertThat(resultTree.totalWeight()).isEqualTo(15.0);

        }
    }

    @GdlExtension
    @Nested
    class GraphWithTwoChildren {
        @GdlGraph(orientation = Orientation.UNDIRECTED)
        private static final String DB_CYPHER =
            "CREATE " +
                "  (a1:node)," +
                "  (a2:node)," +
                "  (a3:node)," +
                "(a1)-[:R{w:100}]->(a2)," +
                "(a1)-[:R{w:100}]->(a3)";


        @Inject
        private TestGraph graph;

        @ParameterizedTest
        @MethodSource("input")
        void shouldApplyDynamicProgramming(double prizeOf2, double prizeOf3, long[] expected) {

            var bitSet = new BitSet(graph.nodeCount());
            for (int i = 0; i < graph.nodeCount(); ++i) {
                bitSet.set(i);
            }

            HugeLongArray degrees = HugeLongArray.newArray(graph.nodeCount());
            degrees.setAll(v -> graph.degree(v));

            HugeDoubleArray prizes = HugeDoubleArray.newArray(graph.nodeCount());
            prizes.set(graph.toMappedNodeId("a2"), prizeOf2);
            prizes.set(graph.toMappedNodeId("a3"), prizeOf3);
            var strongPruning = new StrongPruning(
                new TreeStructure(graph, degrees, graph.nodeCount()),
                bitSet,
                prizes::get,
                ProgressTracker.NULL_TRACKER,
                TerminationFlag.RUNNING_TRUE

            );
            strongPruning.performPruning();

            assertThat(Arrays.stream(expected)
                .map(v -> graph.toMappedNodeId("a" + v))
                .filter(bitSet::get)
                .count()
            ).isEqualTo(expected.length);
        }

        static Stream<Arguments> input() {
            return Stream.of(
                arguments(1, 1, new long[]{1L}),
                arguments(105, 105, new long[]{1L, 2L, 3L}),
                arguments(50, 105, new long[]{1L, 3L}),
                arguments(105, 50, new long[]{1L, 2L})
            );
        }
    }


    @GdlExtension
    @Nested
    class GraphWhereRootIsNotTheOptimal {
        @GdlGraph(orientation = Orientation.UNDIRECTED)
        private static final String DB_CYPHER =
            "CREATE " +
                "  (a0:node)," +
                "  (a1:node)," +
                "  (a2:node)," +
                "  (a3:node)," +
                "(a0)-[:R{w:62}]->(a3)," +
                "(a3)-[:R{w:15}]->(a2)," +
                "(a1)-[:R{w:10}]->(a0),";


        @Inject
        private TestGraph graph;

        @Test
        void shouldApplyDynamicProgramming() {

            var bitSet = new BitSet(graph.nodeCount());
            for (int i = 0; i < graph.nodeCount(); ++i) {
                bitSet.set(i);
            }

            HugeLongArray degrees = HugeLongArray.newArray(graph.nodeCount());
            degrees.setAll(v -> graph.degree(v));


            var strongPruning = new StrongPruning(
                new TreeStructure(graph, degrees, graph.nodeCount()),
                bitSet,
                (v)->20d,
                ProgressTracker.NULL_TRACKER,
                TerminationFlag.RUNNING_TRUE
            );
            strongPruning.performPruning();

          var sp=strongPruning.resultTree();

          assertThat(sp.parentArray().get(graph.toMappedNodeId("a0"))).isEqualTo(PrizeSteinerTreeResult.ROOT);
        }

    }

    @Nested
    @GdlExtension
    class HouseGraph{

        @GdlGraph(orientation = Orientation.UNDIRECTED)
        private static final String DB_CYPHER =
            "CREATE " +
                "  (a0:node)," +
                "  (a1:node)," +
                "  (a2:node)," +
                "  (a3:node)," +
                "  (a4:node)," +
                "(a0)-[:R{w:10}]->(a1)," +
                "(a2)-[:R{w:62}]->(a4)," +
                "(a1)-[:R{w:54}]->(a4)," +
                "(a2)-[:R{w:15}]->(a3),";

        @Inject
        private  TestGraph graph;


        @Test
        void shouldFindOptimal(){
            HugeLongArray prizes = HugeLongArray.newArray(graph.nodeCount());
            var a0 = graph.toMappedNodeId("a0");
            var a1 = graph.toMappedNodeId("a1");
            var a2 = graph.toMappedNodeId("a2");
            var a3 = graph.toMappedNodeId("a3");
            var a4 = graph.toMappedNodeId("a4");

            prizes.set(a0,9);
            prizes.set(a1,60);
            prizes.set(a2,30);
            prizes.set(a3,10);
            prizes.set(a4,110);

            var bitSet = new BitSet(graph.nodeCount());
            for (int i = 0; i < graph.nodeCount(); ++i) {
                bitSet.set(i);
            }

            HugeLongArray degrees = HugeLongArray.newArray(graph.nodeCount());
            degrees.setAll(v -> graph.degree(v));
            var strongPruning = new StrongPruning(
                new TreeStructure(graph, degrees, graph.nodeCount()),
                bitSet,
                prizes::get,
                ProgressTracker.NULL_TRACKER,
                TerminationFlag.RUNNING_TRUE
            );
            strongPruning.performPruning();

            var sp=strongPruning.resultTree();
            var parents = sp.parentArray();
            var costs = sp.relationshipToParentCost();
            LongToDoubleFunction costSupplier =  e -> {
                if (parents.get(e) == PrizeSteinerTreeResult.ROOT )
                    return  0;
                if (parents.get(e) == PrizeSteinerTreeResult.PRUNED)
                    return  Long.MIN_VALUE;
                return costs.get(e);
            };

            assertThat(parents.get(a0)).isEqualTo(PrizeSteinerTreeResult.PRUNED);
            assertThat(parents.get(a2)).isEqualTo(PrizeSteinerTreeResult.PRUNED);
            assertThat(parents.get(a3)).isEqualTo(PrizeSteinerTreeResult.PRUNED);

            double sum=costSupplier.applyAsDouble(a1) +costSupplier.applyAsDouble(a4);
            assertThat(sum).isEqualTo(54);

            assertThat(sp.effectiveNodeCount()).isEqualTo(2);
            assertThat(sp.sumOfPrizes()).isEqualTo(170);
            assertThat(sp.totalWeight()).isEqualTo(54.0);

        }
    }


}
