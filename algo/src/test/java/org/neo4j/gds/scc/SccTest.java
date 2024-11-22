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
package org.neo4j.gds.scc;

import org.junit.jupiter.api.Test;
import org.neo4j.gds.collections.ha.HugeLongArray;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.extension.GdlExtension;
import org.neo4j.gds.extension.GdlGraph;
import org.neo4j.gds.extension.IdFunction;
import org.neo4j.gds.extension.Inject;
import org.neo4j.gds.extension.TestGraph;
import org.neo4j.gds.termination.TerminationFlag;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@GdlExtension
class SccTest {

    @GdlGraph
    private static final String DB_CYPHER =
        "CREATE" +
        "  (a:Node)" +
        ", (b:Node)" +
        ", (c:Node)" +
        ", (d:Node)" +
        ", (e:Node)" +
        ", (f:Node)" +
        ", (g:Node)" +
        ", (h:Node)" +
        ", (i:Node)" +

        ", (a)-[:TYPE {cost: 5}]->(b)" +
        ", (b)-[:TYPE {cost: 5}]->(c)" +
        ", (c)-[:TYPE {cost: 5}]->(a)" +

        ", (d)-[:TYPE {cost: 2}]->(e)" +
        ", (e)-[:TYPE {cost: 2}]->(f)" +
        ", (f)-[:TYPE {cost: 2}]->(d)" +

        ", (a)-[:TYPE {cost: 2}]->(d)" +

        ", (g)-[:TYPE {cost: 3}]->(h)" +
        ", (h)-[:TYPE {cost: 3}]->(i)" +
        ", (i)-[:TYPE {cost: 3}]->(g)";

    @Inject
    private TestGraph graph;

    @Test
    void testDirect() {
        var scc = new Scc(graph, ProgressTracker.NULL_TRACKER, TerminationFlag.RUNNING_TRUE);
        var components = scc.compute();

        assertCC(components);

        HashMap<Long, Long> componentsMap = new HashMap<>();
        for (long nodeId = 0; nodeId < components.size(); ++nodeId) {
            long componentId = components.get(nodeId);
            long componentValue = componentsMap.getOrDefault(componentId, 0L);
            componentsMap.put(componentId, 1 + componentValue);
        }

        long max = 0;
        long min = Long.MAX_VALUE;
        for (var entry : componentsMap.entrySet()) {
            min = Math.min(entry.getValue(), min);
            max = Math.max(entry.getValue(), max);
        }

        assertThat(componentsMap.keySet().size()).isEqualTo(3L);
        assertThat(min).isEqualTo(3L);
        assertThat(max).isEqualTo(3L);

    }

    @Test
    void testHugeIterativeScc() {
        Scc algo = new Scc(graph, ProgressTracker.NULL_TRACKER, TerminationFlag.RUNNING_TRUE);
        HugeLongArray components = algo.compute();
        assertCC(components);
    }

    private void assertCC(HugeLongArray components) {
        IdFunction idFunction = graph::toMappedNodeId;

        assertBelongSameComponent(components, List.of(
            idFunction.of("a"),
            idFunction.of("b"),
            idFunction.of("c")
        ));
        assertBelongSameComponent(components, List.of(
            idFunction.of("d"),
            idFunction.of("e"),
            idFunction.of("f")
        ));
        assertBelongSameComponent(components, List.of(
            idFunction.of("g"),
            idFunction.of("h"),
            idFunction.of("i")
        ));
    }

    private void assertBelongSameComponent(HugeLongArray components, List<Long> nodes) {
        // check if all belong to same set
        final long component = components.get(nodes.get(0));
        for (long node : nodes) {
            assertThat(components.get(node)).isEqualTo(component);
        }

        // check no other element belongs to this set
        for (long node = 0; node < components.size(); node++) {
            if (nodes.contains(node)) {
                continue;
            }
            assertThat(components.get(node)).isNotEqualTo(component);
        }
    }

}
