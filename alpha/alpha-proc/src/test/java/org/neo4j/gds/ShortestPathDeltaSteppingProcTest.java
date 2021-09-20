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
package org.neo4j.gds;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.gds.core.TransactionContext;
import org.neo4j.gds.core.utils.progress.GlobalTaskStore;
import org.neo4j.gds.core.utils.progress.TaskRegistry;
import org.neo4j.gds.core.utils.progress.tasks.Task;
import org.neo4j.gds.core.write.NativeNodePropertyExporter;
import org.neo4j.gds.impl.walking.VirtualNode;
import org.neo4j.gds.shortestpath.ShortestPathDeltaSteppingProc;

import java.util.Map;
import java.util.function.DoubleConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.AdditionalMatchers.eq;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


/**         5     5      5
 *      (A)---(B)---(C)----.
 *    5/ 2    2     2     2 \
 *  (S)---(G)---(H)---(I)---(X)
 *    3\    3     3     3   /
 *      (D)---(E)---(F)----°
 *
 * S->X: {S,G,H,I,X}:8, {S,D,E,F,X}:12, {S,A,B,C,X}:20
 */
final class ShortestPathDeltaSteppingProcTest extends BaseProcTest {

    private static final String DB_CYPHER =
            "CREATE" +
            "  (s:Node {name: 's'})" +
            ", (a:Node {name: 'a'})" +
            ", (b:Node {name: 'b'})" +
            ", (c:Node {name: 'c'})" +
            ", (d:Node {name: 'd'})" +
            ", (e:Node {name: 'e'})" +
            ", (f:Node {name: 'f'})" +
            ", (g:Node {name: 'g'})" +
            ", (h:Node {name: 'h'})" +
            ", (i:Node {name: 'i'})" +
            ", (x:Node {name: 'x'})" +

            ", (x)-[:TYPE {cost: 5}]->(s)" + // creates cycle

            ", (s)-[:TYPE {cost: 5}]->(a)" + // line 1
            ", (a)-[:TYPE {cost: 5}]->(b)" +
            ", (b)-[:TYPE {cost: 5}]->(c)" +
            ", (c)-[:TYPE {cost: 5}]->(x)" +

            ", (s)-[:TYPE {cost: 3}]->(d)" + // line 2
            ", (d)-[:TYPE {cost: 3}]->(e)" +
            ", (e)-[:TYPE {cost: 3}]->(f)" +
            ", (f)-[:TYPE {cost: 3}]->(x)" +

            ", (s)-[:TYPE {cost: 2}]->(g)" + // line 3
            ", (g)-[:TYPE {cost: 2}]->(h)" +
            ", (h)-[:TYPE {cost: 2}]->(i)" +
            ", (i)-[:TYPE {cost: 2}]->(x)";

    @BeforeEach
    void setup() throws Exception {
        registerProcedures(ShortestPathDeltaSteppingProc.class);
        runQuery(DB_CYPHER);
    }

    @Test
    void testResultStream() {
        final DoubleConsumer consumer = mock(DoubleConsumer.class);

        final String cypher =
                "MATCH(n:Node {name:'s'}) " +
                "WITH n CALL gds.alpha.shortestPath.deltaStepping.stream({" +
                "   nodeProjection: '*', " +
                "   relationshipProjection: '*', " +
                "   relationshipProperties: 'cost', " +
                "   startNode: n, " +
                "   delta: 3.0," +
                "   relationshipWeightProperty: 'cost'" +
                "}) " +
                "YIELD nodeId, distance RETURN nodeId, distance";

        runQueryWithRowConsumer(cypher, row -> {
            double distance = row.getNumber("distance").doubleValue();
            consumer.accept(distance);
        });

        verify(consumer, times(11)).accept(anyDouble());
        verify(consumer, times(1)).accept(eq(8D, 0.1D));
    }

    @Test
    void testIncomingResultStream() {
        final DoubleConsumer consumer = mock(DoubleConsumer.class);

        final String cypher =
            "MATCH(n:Node {name: 's'}) " +
            "WITH n CALL gds.alpha.shortestPath.deltaStepping.stream({" +
            "   nodeProjection: '*', " +
            "   relationshipProjection: {" +
            "       TYPE: {" +
            "         type: 'TYPE'," +
            "         orientation: 'REVERSE'," +
            "         properties: 'cost'" +
            "       }" +
            "   }," +
            "   startNode: n, " +
            "   delta: 3.0," +
            "   relationshipWeightProperty: 'cost'" +
            "}) " +
            "YIELD nodeId, distance RETURN nodeId, distance";

        runQueryWithRowConsumer(cypher, row -> {
            double distance = row.getNumber("distance").doubleValue();
            consumer.accept(distance);
        });

        verify(consumer, times(11)).accept(anyDouble());
        verify(consumer, times(1)).accept(eq(8D, 0.1D));
    }

    @Test
    void testBothResultStream() {
        final DoubleConsumer consumer = mock(DoubleConsumer.class);

        final String cypher =
            "MATCH(n:Node {name: 's'}) " +
            "WITH n CALL gds.alpha.shortestPath.deltaStepping.stream({" +
            "   nodeProjection: '*', " +
            "   relationshipProjection: {" +
            "       TYPE: {" +
            "         type: 'TYPE'," +
            "         orientation: 'UNDIRECTED'," +
            "         properties: 'cost'" +
            "       }" +
            "   }," +
            "   startNode: n, " +
            "   delta: 3.0," +
            "   relationshipWeightProperty: 'cost'" +
            "}) " +
            "YIELD nodeId, distance RETURN nodeId, distance";

        runQueryWithRowConsumer(cypher, row -> {
            double distance = row.getNumber("distance").doubleValue();
            consumer.accept(distance);
        });

        verify(consumer, times(11)).accept(anyDouble());
        verify(consumer, times(1)).accept(eq(8D, 0.1D));
    }

    @Test
    void testWriteBack() {
        final String cypher =
            "MATCH(n:Node {name:'s'}) " +
            "WITH n CALL gds.alpha.shortestPath.deltaStepping.write({" +
            "   nodeProjection: '*'," +
            "   relationshipProjection: '*'," +
            "   relationshipProperties: 'cost', " +
            "   startNode: n, " +
            "   delta: 3.0," +
            "   relationshipWeightProperty: 'cost'," +
            "   writeProperty: 'sp'" +
            "}) " +
            "YIELD nodeCount, loadDuration, evalDuration, writeDuration RETURN nodeCount, loadDuration, evalDuration, writeDuration";

        runQueryWithRowConsumer(cypher, row -> {
            long writeDuration = row.getNumber("writeDuration").longValue();
            assertNotEquals(-1L, writeDuration);
        });

        final DoubleConsumer consumer = mock(DoubleConsumer.class);

        final String testCypher = "MATCH(n:Node) WHERE exists(n.sp) WITH n RETURN id(n) as id, n.sp as sp";

        runQueryWithRowConsumer(testCypher, row -> {
            double sp = row.getNumber("sp").doubleValue();
            consumer.accept(sp);
        });

        verify(consumer, times(11)).accept(anyDouble());
        verify(consumer, times(1)).accept(eq(8D, 0.1D));
    }

    @Test
    void testProgressTracking() {
        ProcedureRunner.applyOnProcedure(db, ShortestPathDeltaSteppingProc.class, proc -> {
            var taskStore = new GlobalTaskStore();

            proc.taskRegistryFactory = () -> new NonReleasingTaskRegistry(new TaskRegistry(getUsername(), taskStore));
            proc.nodePropertyExporterBuilder = new NativeNodePropertyExporter.Builder(
                TransactionContext.of(proc.api, proc.procedureTransaction)
            );

            proc.deltaStepping(
                Map.of(
                    "nodeProjection", "*",
                    "relationshipProjection", "*",
                    "writeProperty", "myProp",
                    "startNode", new VirtualNode(0, db),
                    "delta", 0.1
                ),
                Map.of()
            );

            assertThat(taskStore.taskStream().map(Task::description)).containsExactlyInAnyOrder(
                "Loading",
                "ShortestPathDeltaStepping",
                "ShortestPathDeltaStepping :: WriteNodeProperties"
            );
        });
    }

    @Test
    void failOnInvalidStartNode() {
        runQuery("CREATE (:Invalid)");

        final String query =
            "MATCH(n:Invalid) " +
            "WITH n CALL gds.alpha.shortestPath.deltaStepping.write({" +
            "   nodeProjection: 'Node'," +
            "   relationshipProjection: '*'," +
            "   startNode: n, " +
            "   delta: 3.0," +
            "   writeProperty: 'sp'" +
            "}) " +
            "YIELD nodeCount " +
            "RETURN nodeCount ";

        assertError(query, "startNode with id 11 was not loaded");
    }
}
