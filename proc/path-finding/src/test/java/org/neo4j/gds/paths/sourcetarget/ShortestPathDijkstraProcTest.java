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
package org.neo4j.gds.paths.sourcetarget;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.neo4j.gds.paths.ShortestPathBaseConfig;
import org.neo4j.gds.paths.dijkstra.Dijkstra;
import org.neo4j.gds.paths.dijkstra.DijkstraResult;
import org.neo4j.graphalgo.AlgoBaseProcTest;
import org.neo4j.graphalgo.BaseProcTest;
import org.neo4j.graphalgo.GdsCypher;
import org.neo4j.graphalgo.HeapControlTest;
import org.neo4j.graphalgo.MemoryEstimateTest;
import org.neo4j.graphalgo.RelationshipWeightConfigTest;
import org.neo4j.graphalgo.SourceNodeConfigTest;
import org.neo4j.graphalgo.TargetNodeConfigTest;
import org.neo4j.graphalgo.catalog.GraphCreateProc;
import org.neo4j.graphalgo.core.CypherMapWrapper;
import org.neo4j.graphalgo.core.loading.GraphStoreCatalog;
import org.neo4j.graphalgo.extension.Neo4jGraph;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.gds.paths.ShortestPathBaseConfig.SOURCE_NODE_KEY;
import static org.neo4j.gds.paths.ShortestPathBaseConfig.TARGET_NODE_KEY;

abstract class ShortestPathDijkstraProcTest<CONFIG extends ShortestPathBaseConfig> extends BaseProcTest implements
    AlgoBaseProcTest<Dijkstra, CONFIG, DijkstraResult>,
    MemoryEstimateTest<Dijkstra, CONFIG, DijkstraResult>,
    HeapControlTest<Dijkstra, CONFIG, DijkstraResult>,
    RelationshipWeightConfigTest<Dijkstra, CONFIG, DijkstraResult>,
    SourceNodeConfigTest<Dijkstra, CONFIG, DijkstraResult>,
    TargetNodeConfigTest<Dijkstra, CONFIG, DijkstraResult> {

    protected static final String GRAPH_NAME = "graph";
    long idA, idC, idD, idE, idF;
    static long[] ids0;
    static double[] costs0;

    @Neo4jGraph
    private static final String DB_CYPHER = "CREATE" +
           "  (:Offset)" +
           ", (a:Label)" +
           ", (b:Label)" +
           ", (c:Label)" +
           ", (d:Label)" +
           ", (e:Label)" +
           ", (f:Label)" +
           ", (a)-[:TYPE {cost: 4}]->(b)" +
           ", (a)-[:TYPE {cost: 2}]->(c)" +
           ", (b)-[:TYPE {cost: 5}]->(c)" +
           ", (b)-[:TYPE {cost: 10}]->(d)" +
           ", (c)-[:TYPE {cost: 3}]->(e)" +
           ", (d)-[:TYPE {cost: 11}]->(f)" +
           ", (e)-[:TYPE {cost: 4}]->(d)";

    @BeforeEach
    void setup() throws Exception {
        registerProcedures(
            getProcedureClazz(),
            GraphCreateProc.class
        );

        idA = idFunction.of("a");
        idC = idFunction.of("c");
        idD = idFunction.of("d");
        idE = idFunction.of("e");
        idF = idFunction.of("f");

        ids0 = new long[]{idA, idC, idE, idD, idF};
        costs0 = new double[]{0.0, 2.0, 5.0, 9.0, 20.0};

        runQuery(GdsCypher.call()
            .withNodeLabel("Label")
            .withAnyRelationshipType()
            .withRelationshipProperty("cost")
            .graphCreate(GRAPH_NAME)
            .yields());
    }

    @AfterEach
    void teardown() {
        GraphStoreCatalog.removeAllLoadedGraphs();
    }

    @Override
    public GraphDatabaseAPI graphDb() {
        return db;
    }

    @Override
    public CypherMapWrapper createMinimalConfig(CypherMapWrapper mapWrapper) {
        return mapWrapper
            .withNumber(SOURCE_NODE_KEY, idFunction.of("a"))
            .withNumber(TARGET_NODE_KEY, idFunction.of("f"));
    }

    @Override
    public void assertResultEquals(DijkstraResult result1, DijkstraResult result2) {
        assertEquals(result1.pathSet(), result2.pathSet());
    }
}
