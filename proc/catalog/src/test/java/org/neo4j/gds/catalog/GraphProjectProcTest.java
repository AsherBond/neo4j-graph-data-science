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
package org.neo4j.gds.catalog;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.gds.BaseProcTest;
import org.neo4j.gds.GdsCypher;
import org.neo4j.gds.Orientation;
import org.neo4j.gds.PropertyMapping;
import org.neo4j.gds.PropertyMappings;
import org.neo4j.gds.RelationshipProjection;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.api.DatabaseId;
import org.neo4j.gds.api.DefaultValue;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.compat.GraphDatabaseApiProxy;
import org.neo4j.gds.config.ConcurrencyConfig;
import org.neo4j.gds.core.Aggregation;
import org.neo4j.gds.core.concurrency.DefaultPool;
import org.neo4j.gds.core.concurrency.ParallelUtil;
import org.neo4j.gds.core.loading.GraphStoreCatalog;
import org.neo4j.gds.core.utils.progress.JobId;
import org.neo4j.gds.core.utils.progress.TaskStore;
import org.neo4j.gds.core.utils.progress.UserTask;
import org.neo4j.gds.core.utils.progress.tasks.Status;
import org.neo4j.gds.test.TestProc;
import org.neo4j.gds.utils.StringJoining;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.isA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.gds.ElementProjection.PROPERTIES_KEY;
import static org.neo4j.gds.NodeProjection.LABEL_KEY;
import static org.neo4j.gds.RelationshipProjection.AGGREGATION_KEY;
import static org.neo4j.gds.RelationshipProjection.INDEX_INVERSE_KEY;
import static org.neo4j.gds.RelationshipProjection.ORIENTATION_KEY;
import static org.neo4j.gds.RelationshipProjection.TYPE_KEY;
import static org.neo4j.gds.TestSupport.assertGraphEquals;
import static org.neo4j.gds.TestSupport.fromGdl;
import static org.neo4j.gds.TestSupport.getCypherAggregation;
import static org.neo4j.gds.legacycypherprojection.GraphProjectFromCypherConfig.ALL_NODES_QUERY;
import static org.neo4j.gds.legacycypherprojection.GraphProjectFromCypherConfig.ALL_RELATIONSHIPS_QUERY;
import static org.neo4j.gds.legacycypherprojection.GraphProjectFromCypherConfig.NODE_QUERY_KEY;
import static org.neo4j.gds.legacycypherprojection.GraphProjectFromCypherConfig.RELATIONSHIP_QUERY_KEY;
import static org.neo4j.gds.projection.GraphProjectFromStoreConfig.NODE_PROJECTION_KEY;
import static org.neo4j.gds.projection.GraphProjectFromStoreConfig.RELATIONSHIP_PROJECTION_KEY;
import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

class GraphProjectProcTest extends BaseProcTest {

    private static final String DB_CYPHER = "CREATE (:A {age: 2})-[:REL {weight: 55}]->(:A)";
    private static final String newLine = System.lineSeparator();

    @BeforeEach
    void setup() throws Exception {
        registerProcedures(GraphProjectProc.class, TestProc.class);
        runQuery(DB_CYPHER);
    }

    @AfterEach
    void tearDown() {
        GraphStoreCatalog.removeAllLoadedGraphs();
    }

    @Test
    void listProperties() {
        var cypherProjection =
            "CALL gds.graph.project.cypher('g', " +
            "  'RETURN 0 AS id, [1.0, 2.0] AS list', " +
            "  'RETURN 0 AS source, 1 AS target LIMIT 0'" +
            ") YIELD nodeCount";

        assertCypherResult(cypherProjection, List.of(
            Map.of("nodeCount", 1L)
        ));
    }

    @Test
    void createNativeProjection() {
        String graphName = "name";

        assertCypherResult(
            "CALL gds.graph.project($name, 'A', 'REL')",
            Map.of("name", graphName),
            List.of(Map.of(
                "graphName", graphName,
                NODE_PROJECTION_KEY, Map.of(
                    "A", Map.of(
                        LABEL_KEY, "A",
                        PROPERTIES_KEY, emptyMap()
                    )
                ),
                RELATIONSHIP_PROJECTION_KEY, Map.of(
                    "REL", Map.of(
                        TYPE_KEY, "REL",
                        ORIENTATION_KEY, Orientation.NATURAL.name(),
                        AGGREGATION_KEY, Aggregation.DEFAULT.name(),
                        INDEX_INVERSE_KEY, false,
                        PROPERTIES_KEY, emptyMap()
                    )
                ),
                "nodeCount", 2L,
                "relationshipCount", 1L,
                "projectMillis", instanceOf(Long.class)
            ))
        );

        assertGraphExists(graphName);
    }

    @Test
    void createCypherProjection() {
        String graphName = "name";

        assertCypherResult(
            "CALL gds.graph.project.cypher($name, $nodeQuery, $relationshipQuery)",
            Map.of("name", graphName, "nodeQuery", ALL_NODES_QUERY, "relationshipQuery", ALL_RELATIONSHIPS_QUERY),
            singletonList(Map.of(
                "graphName", graphName,
                NODE_QUERY_KEY, ALL_NODES_QUERY,
                RELATIONSHIP_QUERY_KEY, ALL_RELATIONSHIPS_QUERY,
                "nodeCount", 2L,
                "relationshipCount", 1L,
                "projectMillis", instanceOf(Long.class)
            ))
        );

        assertGraphExists(graphName);
    }

    @Test
    void createCypherProjectionWithRelationshipTypes() {
        String name = "name";

        String relationshipQuery = "MATCH (a)-[r:REL]->(b) RETURN id(a) AS source, id(b) AS target, type(r) AS type";

        assertCypherResult(
            "CALL gds.graph.project.cypher($name, $nodeQuery, $relationshipQuery, {validateRelationships: false})",
            Map.of("name", name, "nodeQuery", ALL_NODES_QUERY, "relationshipQuery", relationshipQuery),
            singletonList(Map.of(
                "graphName", name,
                NODE_QUERY_KEY, ALL_NODES_QUERY,
                RELATIONSHIP_QUERY_KEY, relationshipQuery,
                "nodeCount", 2L,
                "relationshipCount", 1L,
                "projectMillis", instanceOf(Long.class)
            ))
        );

        assertGraphExists(name);
    }

    @Test
    void createCypherProjectionWithParameters() {
        String graphName = "name";

        String nodeQuery = "MATCH (n) WHERE n.age = $age RETURN id(n) AS id";

        assertCypherResult(
            "CALL gds.graph.project.cypher($name, $nodeQuery, $relationshipQuery, { parameters: { age: 2 }, validateRelationships: false})",
            Map.of(
                "name",
                graphName,
                "nodeQuery",
                nodeQuery,
                "relationshipQuery",
                ALL_RELATIONSHIPS_QUERY,
                "validateRelationships",
                false
            ),
            singletonList(Map.of(
                "graphName", graphName,
                NODE_QUERY_KEY, nodeQuery,
                RELATIONSHIP_QUERY_KEY, ALL_RELATIONSHIPS_QUERY,
                "nodeCount", 1L,
                "relationshipCount", 0L,
                "projectMillis", instanceOf(Long.class)
            ))
        );

        assertGraphExists(graphName);
    }


    @Test
    void cypherProjectionShouldFailOnInvalidRelationships() {
        var query = "CALL gds.graph.project.cypher(" +
                    "   'g'," +
                    "   'UNWIND [1,2,3] as i RETURN i as id'," +
                    "   'UNWIND [1,2,3] as i return i as source, i+1 as target'" +
                    ")";

        assertThatThrownBy(() -> runQuery(query))
            .rootCause()
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Failed to load a relationship because its target-node");
    }

    @Test
    void nodeProjectionWithAsterisk() {
        String query = "CALL gds.graph.project('g', '*', 'REL') YIELD nodeCount";

        runQuery("CREATE (), (:B), (:C:D:E)");
        assertCypherResult(query, singletonList(
            Map.of("nodeCount", 5L)
        ));

        assertGraphExists("g");
    }

    @Test
    void relationshipProjectionWithAsterisk() {
        String query = "CALL gds.graph.project('g', 'A', '*') YIELD relationshipCount";

        runQuery("CREATE (:A)-[:R]->(:A), (:B:A)-[:T]->(:A:B), (cde:C:D:E)-[:SELF]->(cde)");
        assertCypherResult(query, singletonList(
            Map.of("relationshipCount", 3L)
        ));

        assertGraphExists("g");
    }

    @ParameterizedTest(name = "{0}, nodeProjection = {1}")
    @MethodSource("nodeProjectionVariants")
    void nodeProjectionVariants(String description, Object nodeProjection, Map<String, Object> desugaredNodeProjection) {
        String name = "g";

        assertCypherResult(
            "CALL gds.graph.project($name, $nodeProjection, '*')",
            Map.of("name", name, "nodeProjection", nodeProjection),
            singletonList(Map.of(
                "graphName", name,
                NODE_PROJECTION_KEY, desugaredNodeProjection,
                RELATIONSHIP_PROJECTION_KEY, isA(Map.class),
                "nodeCount", 2L,
                "relationshipCount", 1L,
                "projectMillis", instanceOf(Long.class)
            ))
        );

        assertGraphExists(name);
    }

    @ParameterizedTest(name = "properties = {0}")
    @MethodSource(value = "nodeProperties")
    void nodeProjectionWithProperties(Object properties, Map<String, Object> expectedProperties) {
        String name = "g";
        Map<String, Object> nodeProjection = Map.of("B", Map.of(LABEL_KEY, "A", PROPERTIES_KEY, properties));
        Map<String, Object> expectedNodeProjection = Map.of("B", Map.of(LABEL_KEY, "A", PROPERTIES_KEY, expectedProperties));

        assertCypherResult(
            "CALL gds.graph.project($name, $nodeProjection, '*')",
            Map.of("name", name, "nodeProjection", nodeProjection),
            singletonList(Map.of(
                "graphName", name,
                NODE_PROJECTION_KEY, expectedNodeProjection,
                RELATIONSHIP_PROJECTION_KEY, isA(Map.class),
                "nodeCount", 2L,
                "relationshipCount", 1L,
                "projectMillis", instanceOf(Long.class)
            ))
        );

        assertGraphExists(name);
        assertThat(findLoadedGraph(name).availableNodeProperties(), contains(expectedProperties.keySet().toArray()));
    }

    @Test
    void nodeQueryWithProperties() {
        String name = "g";

        Map<String, Object> expectedProperties = Map.of("age", Map.of("property", "age", "defaultValue", Double.NaN));
        String nodeQuery = "RETURN 0 AS id, 1 AS age";
        String relationshipQuery = "RETURN 0 AS source, 0 AS target";

        assertCypherResult(
            "CALL gds.graph.project.cypher($name, $nodeQuery, $relationshipQuery)",
            Map.of("name", name,
                "nodeQuery", nodeQuery,
                "relationshipQuery", relationshipQuery
            ),
            singletonList(Map.of(
                "graphName", name,
                NODE_QUERY_KEY, nodeQuery,
                RELATIONSHIP_QUERY_KEY, relationshipQuery,
                "nodeCount", 1L,
                "relationshipCount", 1L,
                "projectMillis", instanceOf(Long.class)
            ))
        );

        assertGraphExists(name);
        Graph graph = findLoadedGraph(name);
        assertThat(graph.availableNodeProperties(), contains(expectedProperties.keySet().toArray()));
    }

    @Test
    void nodeQueryWithQueryProperties() {
        String name = "g";

        String nodeQuery = "MATCH (n) RETURN id(n) AS id, n.age AS age";

        assertCypherResult(
            "CALL gds.graph.project.cypher($name, $nodeQuery, $relationshipQuery)",
            Map.of("name", name, "nodeQuery", nodeQuery, "relationshipQuery", ALL_RELATIONSHIPS_QUERY),
            singletonList(Map.of(
                "graphName", name,
                NODE_QUERY_KEY, nodeQuery,
                RELATIONSHIP_QUERY_KEY, ALL_RELATIONSHIPS_QUERY,
                "nodeCount", 2L,
                "relationshipCount", 1L,
                "projectMillis", instanceOf(Long.class)
            ))
        );

        assertGraphExists(name);
    }

    @ParameterizedTest(name = "{0}, relProjection = {1}")
    @MethodSource("relationshipProjectionVariants")
    void relationshipProjectionVariants(String description, Object relProjection, Map<String, Object> desugaredRelProjection) {
        String name = "g";
        // We assume that a single projection projects exactly one relationship.
        long expectedRelationshipCount = desugaredRelProjection.size();

        assertCypherResult(
            "CALL gds.graph.project($name, '*', $relProjection)",
            Map.of("name", name, "relProjection", relProjection),
            singletonList(Map.of(
                "graphName", name,
                NODE_PROJECTION_KEY, isA(Map.class),
                RELATIONSHIP_PROJECTION_KEY, desugaredRelProjection,
                "nodeCount", 2L,
                "relationshipCount", expectedRelationshipCount,
                "projectMillis", instanceOf(Long.class)
            ))
        );

        assertGraphExists(name);
    }

    @ParameterizedTest(name = "projection={0}")
    @MethodSource("relationshipOrientations")
    void relationshipProjectionOrientations(String orientation) {
        String name = "g";

        Long expectedRelationshipCount = orientation.equals("UNDIRECTED") ? 2L : 1L;

        String graphCreate = GdsCypher.call(name)
            .graphProject()
            .withAnyLabel()
            .withRelationshipType(
                "B",
                RelationshipProjection.builder()
                    .type("REL")
                    .orientation(Orientation.parse(orientation))
                    .build()
            )
            .yields();

        assertCypherResult(
            graphCreate,
            singletonList(Map.of(
                "graphName", name,
                NODE_PROJECTION_KEY, isA(Map.class),
                RELATIONSHIP_PROJECTION_KEY, Map.of("B", Map.of(
                    "type", "REL",
                    ORIENTATION_KEY, orientation,
                    INDEX_INVERSE_KEY, false,
                    PROPERTIES_KEY, emptyMap(),
                    AGGREGATION_KEY,
                    Aggregation.DEFAULT.name()
                )),
                "nodeCount", 2L,
                "relationshipCount", expectedRelationshipCount,
                "projectMillis", instanceOf(Long.class)
            ))
        );

        assertGraphExists(name);
    }

    @ParameterizedTest(name = "properties = {0}")
    @MethodSource(value = "relationshipProperties")
    void relationshipProjectionWithProperties(Object properties, Map<String, Object> expectedProperties) {
        String name = "g";

        String graphCreate = GdsCypher.call(name)
            .graphProject()
            .withAnyLabel()
            .withRelationshipType(
                "B", RelationshipProjection.builder()
                    .type("REL")
                    .properties(PropertyMappings.fromObject(properties))
                    .build()
            )
            .yields();

        assertCypherResult(graphCreate,
            singletonList(Map.of(
                "graphName", name,
                NODE_PROJECTION_KEY, isA(Map.class),
                RELATIONSHIP_PROJECTION_KEY, Map.of(
                    "B",
                    Map.of("type", "REL",
                        ORIENTATION_KEY, "NATURAL",
                        AGGREGATION_KEY, "DEFAULT",
                        INDEX_INVERSE_KEY, false,
                        PROPERTIES_KEY, expectedProperties
                    )
                ),
                "nodeCount", 2L,
                "relationshipCount", 1L,
                "projectMillis", instanceOf(Long.class)
            ))
        );

        Graph graph = GraphStoreCatalog.get("", DatabaseId.of(db.databaseName()), name).graphStore().getUnion();
        assertGraphEquals(fromGdl("()-[:B {w:55}]->()"), graph);
    }

    @Test
    void relationshipQueryAndQueryProperties() {
        String name = "g";

        String relationshipQuery = "MATCH (s)-[r]->(t) RETURN id(s) AS source, id(t) AS target, r.weight AS weight";

        assertCypherResult(
            "CALL gds.graph.project.cypher($name, $nodeQuery, $relationshipQuery)",
            Map.of("name", name, "nodeQuery", ALL_NODES_QUERY, "relationshipQuery", relationshipQuery),
            singletonList(Map.of(
                "graphName", name,
                NODE_QUERY_KEY, ALL_NODES_QUERY,
                RELATIONSHIP_QUERY_KEY, relationshipQuery,
                "nodeCount", 2L,
                "relationshipCount", 1L,
                "projectMillis", instanceOf(Long.class)
            ))
        );

        assertGraphExists(name);
        Graph graph = GraphStoreCatalog.get("", DatabaseId.of(db.databaseName()), name).graphStore().getUnion();
        assertGraphEquals(fromGdl("()-[{w: 55}]->()"), graph);
    }

    @ParameterizedTest(name = "aggregation={0}")
    @CsvSource({"DEFAULT, 55", "NONE, 55", "MAX, 55", "MIN, 55", "SINGLE, 55", "SUM, 55", "COUNT, 1"})
    void relationshipProjectionPropertyPropagateAggregations(Aggregation aggregationParam, int expectedValue) {
        String aggregation = aggregationParam.toString();
        String name = "g";

        Map<String, Object> relationshipProjection = Map.of("B", Map.of(
            "type", "REL",
            "aggregation", aggregation,
            "properties", Map.of("weight", emptyMap())
        ));

        Map<String, Object> relationshipProperties = Map.of("foo", Map.of(
            "property", "weight",
            "aggregation", Optional.of(aggregation)
                .filter(a1 -> a1.equals("NONE"))
                .orElse("MAX")
        ));

        assertCypherResult(
            "CALL gds.graph.project($name, '*', $relationshipProjection, { relationshipProperties: $relationshipProperties })",
            Map.of(
                "name",
                name,
                "relationshipProjection",
                relationshipProjection,
                "relationshipProperties",
                relationshipProperties
            ),
            singletonList(Map.of(
                "graphName", name,
                NODE_PROJECTION_KEY, isA(Map.class),
                RELATIONSHIP_PROJECTION_KEY, Map.of(
                    "B", Map.of(
                        "type", "REL",
                        ORIENTATION_KEY, "NATURAL",
                        AGGREGATION_KEY, aggregation,
                        INDEX_INVERSE_KEY, false,
                        PROPERTIES_KEY, Map.of(
                            "weight", mapWithNulls(
                                "property", "weight",
                                AGGREGATION_KEY, aggregation,
                                "defaultValue", null
                            ),
                            "foo", mapWithNulls(
                                "property", "weight",
                                AGGREGATION_KEY, Optional.of(aggregation)
                                    .filter(a -> a.equals("NONE"))
                                    .orElse("MAX"),
                                "defaultValue", null
                            )
                        )
                    )
                ),
                "nodeCount", 2L,
                "relationshipCount", 1L,
                "projectMillis", instanceOf(Long.class)
            ))
        );

        assertGraphExists(name);
        Graph weightGraph = relPropertyGraph(name, RelationshipType.of("B"), "weight");
        Graph fooGraph = relPropertyGraph(name, RelationshipType.of("B"), "foo");
        assertGraphEquals(fromGdl(formatWithLocale("(a)-[:B {w: %d}]->()", expectedValue)), weightGraph);
        assertGraphEquals(fromGdl("(a)-[:B {w: 55}]->()"), fooGraph);
    }

    @ParameterizedTest(name = "aggregation={0}")
    @CsvSource({"DEFAULT, 55", "NONE, 55", "MAX, 55", "MIN, 55", "SINGLE, 55", "SUM, 55", "COUNT, 1"})
    void relationshipProjectionPropertyAggregations(Aggregation aggregationParam, int expectedValue) {
        String aggregation = aggregationParam.toString();
        String name = "g";

        Map<String, Object> relationshipProjection = Map.of("B", Map.of(
            "type", "REL",
            "aggregation", Aggregation.DEFAULT.name(),
            "properties", Map.of("weight", Map.of("aggregation", aggregation))
        ));

        assertCypherResult(
            "CALL gds.graph.project($name, '*', $relationshipProjection)",
            Map.of("name", name, "relationshipProjection", relationshipProjection),
            singletonList(Map.of(
                "graphName", name,
                NODE_PROJECTION_KEY, isA(Map.class),
                RELATIONSHIP_PROJECTION_KEY, Map.of("B", Map.of(
                        "type", "REL",
                        ORIENTATION_KEY, "NATURAL",
                        AGGREGATION_KEY, "DEFAULT",
                        INDEX_INVERSE_KEY, false,
                        PROPERTIES_KEY, Map.of("weight", mapWithNulls(
                                "property", "weight",
                                AGGREGATION_KEY, aggregation,
                                "defaultValue", null
                            )
                        )
                    )
                ),
                "nodeCount", 2L,
                "relationshipCount", 1L,
                "projectMillis", instanceOf(Long.class)
            ))
        );

        assertGraphExists(name);
        Graph graph = GraphStoreCatalog.get("", DatabaseId.of(db.databaseName()), name).graphStore().getUnion();
        assertGraphEquals(fromGdl(formatWithLocale("()-[:B {w: %d}]->()", expectedValue)), graph);
    }

    @ParameterizedTest(name = "aggregation={0}")
    @EnumSource(Aggregation.class)
    void relationshipProjectionPropertyAggregationsNativeVsCypher(Aggregation aggregationParam) {
        String aggregation = aggregationParam.toString();

        runQuery(
            " CREATE (p:Person)-[:KNOWS]->(k:Person)," +
            " (p)-[:KNOWS {weight: 1}]->(m:Person)," +
            " (m)-[:KNOWS {weight: 2}]->(m)," +
            " (p)-[:KNOWS {weight: 3}]->(p)," +
            " (p)-[:KNOWS {weight: 4}]->(k)," +
            " (p)-[:KNOWS {weight: -2}]->(k)," +
            " (p)-[:KNOWS {weight: 5}]->(k)"
        );

        String standard = "standard";
        String cypher = "cypher";

        String graphCreateStandard = GdsCypher.call(standard)
            .graphProject()
            .withAnyLabel()
            .withRelationshipType(
                "KNOWS",
                RelationshipProjection.builder()
                    .type("KNOWS")
                    .orientation(Orientation.NATURAL)
                    .addProperty("weight", "weight", DefaultValue.of(Double.NaN), Aggregation.parse(aggregation))
                    .build()
            )
            .yields();

        AtomicInteger standardNodeCount = new AtomicInteger();
        AtomicInteger standardRelCount = new AtomicInteger();
        runQueryWithRowConsumer(graphCreateStandard,
            row -> {
                standardNodeCount.set(row.getNumber("nodeCount").intValue());
                standardRelCount.set(row.getNumber("relationshipCount").intValue());
            }
        );

        AtomicInteger cypherNodeCount = new AtomicInteger();
        AtomicInteger cypherRelCount = new AtomicInteger();

        runQueryWithRowConsumer(
            "CALL gds.graph.project.cypher($name, $nodeQuery, $relationshipQuery)",
            Map.of("name", cypher,
                "nodeQuery", ALL_NODES_QUERY,
                "relationshipQuery", formatWithLocale(
                    "MATCH (s)-[r:KNOWS]->(t) RETURN id(s) AS source, id(t) AS target, %s AS weight",
                    getCypherAggregation(aggregation, "r.weight")
                )
            ),
            row -> {
                cypherNodeCount.set(row.getNumber("nodeCount").intValue());
                cypherRelCount.set(row.getNumber("relationshipCount").intValue());
            }
        );

        assertEquals(standardNodeCount.get(), cypherNodeCount.get());

        int relationshipCountCypher = standardRelCount.get();
        int relationshipsStandard = cypherRelCount.get();

        assertTrue(relationshipsStandard > 0);
        assertTrue(relationshipCountCypher > 0);
        assertEquals(
            relationshipsStandard,
            relationshipCountCypher,
            formatWithLocale(
                "Expected %d relationships using `gds.graph.project` to be equal to %d relationships when using `gds.graph.project.cypher`",
                relationshipsStandard,
                relationshipCountCypher
            )
        );
    }

    @Test
    void defaultRelationshipProjectionProperty() {
        assertCypherResult(
            "CALL gds.graph.project('testGraph', '*', $relationshipProjection)",
            singletonMap("relationshipProjection", Map.of(
                "REL", Map.of("properties", Map.of("weight", Map.of("aggregation", "SINGLE"))
                ))),
            singletonList(Map.of(
                "graphName", "testGraph",
                NODE_PROJECTION_KEY, isA(Map.class),
                RELATIONSHIP_PROJECTION_KEY, Map.of(
                    "REL", Map.of(
                        "type", "REL",
                        ORIENTATION_KEY, "NATURAL",
                        AGGREGATION_KEY, "DEFAULT",
                        INDEX_INVERSE_KEY, false,
                        PROPERTIES_KEY, Map.of(
                            "weight", mapWithNulls(
                                "property", "weight",
                                AGGREGATION_KEY, "SINGLE",
                                "defaultValue", null
                            )
                        )
                    )
                ),
                "nodeCount", 2L,
                "relationshipCount", 1L,
                "projectMillis", instanceOf(Long.class)
            )));
    }

    @Test
    void defaultNodeProjectionProperty() {
        assertCypherResult(
            "CALL gds.graph.project('testGraph', $nodeProjection, '*')",
            singletonMap("nodeProjection", Map.of(
                "A", Map.of("properties", Map.of("age", Map.of("defaultValue", 1)))
            )),
            singletonList(Map.of(
                "graphName", "testGraph",
                NODE_PROJECTION_KEY, Map.of(
                    "A", Map.of(
                        "label", "A",
                        "properties", Map.of(
                            "age", Map.of(
                                "defaultValue", 1,
                                "property", "age"
                            )
                        )

                    )
                ),
                RELATIONSHIP_PROJECTION_KEY, isA(Map.class),
                "nodeCount", 2L,
                "relationshipCount", 1L,
                "projectMillis", instanceOf(Long.class)
            )));
    }

    @Test
    void defaultArrayNodeProjectionProperty() {
        clearDb();
        var name = "g";
        runQuery("CREATE ({ratings: [1.0, 2.0]}), ()");
        runQuery(
            "CALL gds.graph.project($name, '*', '*', {nodeProperties: {ratings: {defaultValue: [5.0]}}}) YIELD nodeProjection",
            Map.of("name", name)
        );

        assertGraphExists(name);
        var graph = GraphStoreCatalog.get("", DatabaseId.of(db.databaseName()), name).graphStore().getUnion();
        assertGraphEquals(fromGdl("({ratings: [1.0, 2.0]}), ({ratings: [5.0]})"), graph);
    }

    @Test
    void failOnWrongDefaultValueTypeForArrayNodeProjectionProperty() {
        clearDb();
        runQuery("CREATE ({ratings: [1.0, 2.0]}), ()");
        assertError(
            "CALL gds.graph.project('g', '*', '*', {nodeProperties: {ratings: {defaultValue: 5.0}}}) YIELD nodeProjection",
            "Expected type of default value to be `double[]`. But got `Double`."
        );
    }

    @Test
    void failOnWrongDefaultValueTypeForDoubleNodeProjectionProperty() {
        clearDb();
        runQuery("CREATE ({ratings: 1.0}), ()");
        assertError(
            "CALL gds.graph.project('g', '*', '*', {nodeProperties: {ratings: {defaultValue: [5.0]}}}) YIELD nodeProjection",
            "Expected type of default value to be `Double`. But got `double[]`."
        );
    }

    @Test
    void loadGraphWithSaturatedThreadPool() {
        // ensure that we don't drop task that can't be scheduled while importing a graph.

        // TODO: ensure parallel running via batch-size
        String query = "CALL gds.graph.project('g', '*', '*')";

        List<Future<?>> futures = new ArrayList<>();
        // block all available threads
        for (int i = 0; i < ConcurrencyConfig.DEFAULT_CONCURRENCY; i++) {
            futures.add(
                DefaultPool.INSTANCE.submit(() -> LockSupport.parkNanos(Duration.ofSeconds(1).toNanos()))
            );
        }

        try {
            runQueryWithRowConsumer(query,
                row -> {
                    assertEquals(2, row.getNumber("nodeCount").intValue());
                    assertEquals(1, row.getNumber("relationshipCount").intValue());
                }
            );
        } finally {
            ParallelUtil.awaitTermination(futures);
        }
    }

    @Test
    void loadMultipleNodeProperties() {
        String testGraph =
            "CREATE" +
            "  (a: Node { foo: 42, bar: 13.37 })" +
            ", (b: Node { foo: 43, bar: 13.38 })" +
            ", (c: Node { foo: 44, bar: 13.39 })" +
            ", (d: Node { foo: 45 })";

        // TODO: test create.cypher
        runQuery(testGraph, Collections.emptyMap());
        String query = GdsCypher
            .call("g")
            .graphProject()
            .withNodeLabel("Node")
            .withAnyRelationshipType()
            .withNodeProperty("fooProp", "foo")
            .withNodeProperty(PropertyMapping.of("barProp", "bar", 19.84))
            .yields("nodeCount");

        runQuery(query, Map.of());

        Graph graph = GraphStoreCatalog.get("", DatabaseId.of(db.databaseName()), "g").graphStore().getUnion();
        Graph expected = fromGdl("(:Node { fooProp: 42, barProp: 13.37D })" +
                                 "(:Node { fooProp: 43, barProp: 13.38D })" +
                                 "(:Node { fooProp: 44, barProp: 13.39D })" +
                                 "(:Node { fooProp: 45, barProp: 19.84D })");
        assertGraphEquals(expected, graph);
    }

    @Test
    void loadMultipleRelationshipProperties() {
        String testGraph =
            "CREATE" +
            "  (a: Node)" +
            ", (b: Node)" +
            ", (a)-[:TYPE_1 { weight: 42.1, cost: 1 }]->(b)" +
            ", (a)-[:TYPE_1 { weight: 43.2, cost: 2 }]->(b)" +
            ", (a)-[:TYPE_2 { weight: 44.3, cost: 3 }]->(b)" +
            ", (a)-[:TYPE_2 { weight: 45.4, cost: 4 }]->(b)";

        runQuery(testGraph, Collections.emptyMap());

        String query = GdsCypher
            .call("aggGraph")
            .graphProject()
            .withNodeLabel("Node")
            .withRelationshipProperty(PropertyMapping.of("sumWeight", "weight", DefaultValue.of(1.0), Aggregation.SUM))
            .withRelationshipProperty(PropertyMapping.of("minWeight", "weight", Aggregation.MIN))
            .withRelationshipProperty(PropertyMapping.of("maxCost", "cost", Aggregation.MAX))
            .withRelationshipType("TYPE_1")
            .yields("relationshipProjection");

        runQueryWithRowConsumer(query, row -> {
            Map<String, Object> relationshipProjections = (Map<String, Object>) row.get("relationshipProjection");
            Map<String, Object> type1Projection = (Map<String, Object>) relationshipProjections.get("TYPE_1");
            Map<String, Object> relProperties = (Map<String, Object>) type1Projection.get("properties");
            assertEquals(3, relProperties.size());

            Map<String, Object> sumWeightParams = (Map<String, Object>) relProperties.get("sumWeight");
            Map<String, Object> minWeightParams = (Map<String, Object>) relProperties.get("minWeight");
            Map<String, Object> maxCostParams = (Map<String, Object>) relProperties.get("maxCost");

            assertEquals("weight", sumWeightParams.get("property").toString());
            assertEquals("SUM", sumWeightParams.get("aggregation").toString());
            assertEquals(1.0, sumWeightParams.get("defaultValue"));

            assertEquals("weight", minWeightParams.get("property").toString());
            assertEquals("MIN", minWeightParams.get("aggregation").toString());

            assertEquals("cost", maxCostParams.get("property").toString());
            assertEquals("MAX", maxCostParams.get("aggregation").toString());
        });

        Graph actual = GraphStoreCatalog.get("", DatabaseId.of(db.databaseName()), "aggGraph").graphStore().getUnion();
        Graph expected = fromGdl("(a:Node)-[:TYPE_1 {w:85.3D}]->(b:Node),(a)-[:TYPE_1 {w:42.1D}]->(b),(a)-[:TYPE_1 {w:2.0D}]->(b)");
        assertGraphEquals(expected, actual);
    }

    @ParameterizedTest
    @CsvSource({"SUM, 1337", "MIN, 1337", "MAX, 1337", "COUNT, 1"})
    void loadAndAggregateWithMissingPropertiesAndNanDefault(Aggregation aggregation, int expectedWeight) {
        String testGraph =
            "CREATE" +
            "  (a: Node)" +
            ", (b: Node)" +
            ", (a)-[:TYPE {property: 1337}]->(b)" +   // property hit, value != default
            ", (a)-[:TYPE]->(b)" +                    // no properties at all
            ", (a)-[:TYPE {otherProperty: 42}]->(b)"; // different property
        runQuery(testGraph);

        String query = GdsCypher
            .call("g")
            .graphProject()
            .withNodeLabel("Node")
            .withRelationshipProperty("agg", "property", DefaultValue.of(Double.NaN), aggregation)
            .withRelationshipType("TYPE")
            .yields();
        runQuery(query);

        Graph actual = relPropertyGraph("g", RelationshipType.of("TYPE"), "agg");
        Graph expected = fromGdl(formatWithLocale("(a:Node)-[:TYPE {w:%d}]->(b:Node)", expectedWeight));
        assertGraphEquals(expected, actual);
    }

    @ParameterizedTest
    @CsvSource({"SUM, NaN", "MIN, NaN", "MAX, NaN", "COUNT, 2"})
    void loadAndAggregateWithMissingPropertiesAndExplicitNanValue(Aggregation aggregation, double expectedWeight) {
        String testGraph =
            "CREATE" +
            "  (a: Node)" +
            ", (b: Node)" +
            ", (a)-[:TYPE {property: 1337}]->(b)" +     // property hit, value != default
            ", (a)-[:TYPE]->(b)" +                      // no properties at all
            ", (a)-[:TYPE {otherProperty: 42}]->(b)" +  // different property
            ", (a)-[:TYPE {property: 0.0 / 0.0}]->(b)"; // property hit, value == NaN

        runQuery(testGraph, Collections.emptyMap());

        String query = GdsCypher
            .call("g")
            .graphProject()
            .withNodeLabel("Node")
            .withRelationshipProperty("agg", "property", DefaultValue.of(Double.NaN), aggregation)
            .withRelationshipType("TYPE")
            .yields();
        runQuery(query);

        Graph actual = relPropertyGraph("g", RelationshipType.of("TYPE"), "agg");
        Graph expected = fromGdl(formatWithLocale("(a:Node)-[:TYPE {w:%g}]->(b:Node)", expectedWeight));
        assertGraphEquals(expected, actual);
    }

    @ParameterizedTest
    @CsvSource({"SUM, 1421", "MIN, 42", "MAX, 1337", "COUNT, 1"})
    void loadAndAggregateWithMissingPropertiesWithNonNanDefault(Aggregation aggregation, int expectedWeight) {
        String testGraph =
            "CREATE" +
            "  (a: Node)" +
            ", (b: Node)" +
            ", (a)-[:TYPE {property: 1337}]->(b)" +   // property hit, value != default
            ", (a)-[:TYPE]->(b)" +                    // no properties at all
            ", (a)-[:TYPE {otherProperty: 42}]->(b)"; // different property

        runQuery(testGraph, Collections.emptyMap());

        String query = GdsCypher
            .call("g")
            .graphProject()
            .withNodeLabel("Node")
            .withRelationshipProperty("agg", "property", DefaultValue.of(42), aggregation)
            .withRelationshipType("TYPE")
            .yields();
        runQuery(query);

        Graph actual = relPropertyGraph("g", RelationshipType.of("TYPE"), "agg");
        Graph expected = fromGdl(formatWithLocale("(a:Node)-[:TYPE {w:%d}]->(b:Node)", expectedWeight));
        assertGraphEquals(expected, actual);
    }

    @Test
    void loadWithRelationshipCountAggregation() {
        String testGraph =
            "CREATE" +
            "  (a: Node)" +
            ", (b: Node)" +
            ", (a)-[:TYPE_1 {foo: 1337}]->(b)" +
            ", (a)-[:TYPE_1 {foo: 1337}]->(b)" +
            ", (a)-[:TYPE_2 {foo: 1337}]->(b)" +
            ", (a)-[:TYPE_2 {foo: 1337}]->(b)";

        runQuery(testGraph, Collections.emptyMap());

        String query = GdsCypher
            .call("countGraph")
            .graphProject()
            .withNodeLabel("Node")
            .withRelationshipProperty("count", "*", DefaultValue.of(42), Aggregation.COUNT)
            .withRelationshipType("TYPE_1")
            .yields("relationshipProjection");

        runQueryWithRowConsumer(query, row -> {
            Map<String, Object> relationshipProjections = (Map<String, Object>) row.get("relationshipProjection");
            Map<String, Object> type1Projection = (Map<String, Object>) relationshipProjections.get("TYPE_1");
            Map<String, Object> relProperties = (Map<String, Object>) type1Projection.get("properties");
            assertEquals(1, relProperties.size());

            Map<String, Object> countParams = (Map<String, Object>) relProperties.get("count");

            assertEquals("*", countParams.get("property").toString());
            assertEquals("COUNT", countParams.get("aggregation").toString());
            assertEquals(42L, countParams.get("defaultValue"));

        });

        Graph actual = relPropertyGraph("countGraph", RelationshipType.of("TYPE_1"), "count");
        Graph expected = fromGdl("(a:Node)-[:TYPE_1 {w:2.0D}]->(b:Node)");
        assertGraphEquals(expected, actual);
    }

    @Test
    void loadWithRelationshipPropertyCountAggregation() {
        String testGraph =
            "CREATE" +
            "  (a: Node)" +
            ", (b: Node)" +
            ", (a)-[:TYPE {foo: 1337}]->(b)" +  // property hit, value != default
            ", (a)-[:TYPE]->(b)" +              // no properties at all
            ", (a)-[:TYPE {bar: 42}]->(b)" +    // different property
            ", (a)-[:TYPE {foo: 42}]->(b)";     // property hit, value == default

        runQuery(testGraph, Collections.emptyMap());

        String query = GdsCypher
            .call("countGraph")
            .graphProject()
            .withNodeLabel("Node")
            .withRelationshipProperty("count", "foo", DefaultValue.of(42L), Aggregation.COUNT)
            .withRelationshipType("TYPE")
            .yields("relationshipProjection");

        runQueryWithRowConsumer(query, row -> {
            Map<String, Object> relationshipProjections = (Map<String, Object>) row.get("relationshipProjection");
            Map<String, Object> type1Projection = (Map<String, Object>) relationshipProjections.get("TYPE");
            Map<String, Object> relProperties = (Map<String, Object>) type1Projection.get("properties");
            assertEquals(1, relProperties.size());

            Map<String, Object> countParams = (Map<String, Object>) relProperties.get("count");

            assertEquals("foo", countParams.get("property").toString());
            assertEquals("COUNT", countParams.get("aggregation").toString());
            assertEquals(42L, countParams.get("defaultValue"));

        });

        Graph actual = relPropertyGraph("countGraph", RelationshipType.of("TYPE"), "count");
        Graph expected = fromGdl("(a:Node)-[:TYPE {w:2.0D}]->(b:Node)");
        assertGraphEquals(expected, actual);
    }

    @Test
    void loadWithRelationshipCountAggregationAndAnotherAggregation() {
        String testGraph =
            "CREATE" +
            "  (a: Node)" +
            ", (b: Node)" +
            ", (a)-[:TYPE_1 {foo: 1337}]->(b)" +
            ", (a)-[:TYPE_1 {foo: 1337}]->(b)" +
            ", (a)-[:TYPE_2 {foo: 1337}]->(b)" +
            ", (a)-[:TYPE_2 {foo: 1337}]->(b)";

        runQuery(testGraph, Collections.emptyMap());

        String query = GdsCypher
            .call("countGraph")
            .graphProject()
            .withNodeLabel("Node")
            .withRelationshipProperty("count", "*", DefaultValue.of(42), Aggregation.COUNT)
            .withRelationshipProperty("foo", "foo", DefaultValue.of(42), Aggregation.SUM)
            .withRelationshipType("TYPE_1")
            .yields("relationshipProjection");

        runQueryWithRowConsumer(query, row -> {
            Map<String, Object> relationshipProjections = (Map<String, Object>) row.get("relationshipProjection");
            Map<String, Object> type1Projection = (Map<String, Object>) relationshipProjections.get("TYPE_1");
            Map<String, Object> relProperties = (Map<String, Object>) type1Projection.get("properties");
            assertEquals(2, relProperties.size());

            Map<String, Object> countParams = (Map<String, Object>) relProperties.get("count");
            assertEquals("*", countParams.get("property").toString());
            assertEquals("COUNT", countParams.get("aggregation").toString());
            assertEquals(42L, countParams.get("defaultValue"));

            Map<String, Object> fooParams = (Map<String, Object>) relProperties.get("foo");
            assertEquals("foo", fooParams.get("property").toString());
            assertEquals("SUM", fooParams.get("aggregation").toString());
            assertEquals(42L, fooParams.get("defaultValue"));

        });

        Graph countActual = relPropertyGraph("countGraph", RelationshipType.of("TYPE_1"), "count");
        Graph countExpected = fromGdl("(a:Node)-[:TYPE_1 {w:2.0D}]->(b:Node)");
        assertGraphEquals(countExpected, countActual);

        Graph fooActual = relPropertyGraph("countGraph", RelationshipType.of("TYPE_1"), "foo");
        Graph fooExpected = fromGdl("(a:Node)-[:TYPE_1 {w:2674.0D}]->(b:Node)");
        assertGraphEquals(fooExpected, fooActual);
    }

    // Failure cases

    @ParameterizedTest(name = "projections: {0}")
    @ValueSource(strings = {"'*', {}", "{}, '*'", "'', '*'", "'*', ''"})
    void failsOnEmptyProjection(String projection) {
        String query = "CALL gds.graph.project('g', " + projection + ")";

        assertErrorRegex(
            query,
            ".*An empty ((node)|(relationship)) projection was given; at least one ((node label)|(relationship type)) must be projected."
        );

        assertGraphDoesNotExist("g");
    }

    @Test
    void failsOnBothEmptyProjection() {
        String query = "CALL gds.graph.project('g','','')";

        String expectedMsg = "Multiple errors in configuration arguments:" + newLine +
                             "\t\t\t\tAn empty node projection was given; at least one node label must be projected." + newLine +
                             "\t\t\t\tAn empty relationship projection was given; at least one relationship type must be projected.";

        assertError(
            query,
            expectedMsg
        );

        assertGraphDoesNotExist("g");
    }

    @Test
    void failsOnInvalidPropertyKey() {
        String name = "g";

        String graphCreate =
            "CALL gds.graph.project(" +
            "$name, " +
            "{" +
            "  A: {" +
            "    label: 'A'," +
            "    properties: {" +
            "      property: 'invalid'" +
            "    }" +
            "  }" +
            "}," +
            "'*')";

        assertError(
            graphCreate,
            Map.of("name", name),
            "Node properties not found: 'invalid'"
        );

        assertGraphDoesNotExist(name);
    }

    @Test
    void failsOnInvalidAggregationCombinations() {
        String query =
            "CALL gds.graph.project('g', '*', " +
            "{" +
            "    B: {" +
            "        type: 'REL'," +
            "        orientation: 'NATURAL'," +
            "        aggregation: 'NONE'," +
            "        properties: {" +
            "            weight: {" +
            "                aggregation: 'NONE'" +
            "            }" +
            "        }" +
            "    } " +
            "}, " +
            "{" +
            "    relationshipProperties: {" +
            "        foo: {" +
            "            property: 'weight'," +
            "            aggregation: 'MAX'" +
            "        }" +
            "    }" +
            "})";

        assertError(query, "Conflicting relationship property aggregations, it is not allowed to mix `NONE` with aggregations.");

        assertGraphDoesNotExist("g");
    }

    @Test
    void failsOnMissingStarPropertyForCountAggregation() {
        String query =
            "CALL gds.graph.project('g', '*', " +
            "{" +
            "    B: {" +
            "        type: 'REL'," +
            "        properties: {" +
            "            count: {" +
            "                aggregation: 'COUNT'" +
            "            }" +
            "        }" +
            "    } " +
            "})";

        assertError(query, "Relationship properties not found: 'count' (if you meant to count parallel relationships, use `property:'*'`).");

        assertGraphDoesNotExist("g");
    }

    @Test
    void failsOnMissingPropertyForCountAggregation() {
        String query =
            "CALL gds.graph.project('g', '*', " +
            "{" +
            "    B: {" +
            "        type: 'REL'," +
            "        properties: {" +
            "            count: {" +
            "                property: 'foo'," +
            "                aggregation: 'COUNT'" +
            "            }" +
            "        }" +
            "    } " +
            "})";

        assertError(query, "Relationship properties not found: 'foo' (if you meant to count parallel relationships, use `property:'*'`).");

        assertGraphDoesNotExist("g");
    }

    @Test
    void failsOnInvalidNeoLabel() {
        String name = "g";
        Map<String, Object> nodeProjection = Map.of("A", Map.of(LABEL_KEY, "INVALID"));

        assertError(
            "CALL gds.graph.project($name, $nodeProjection, '*')",
            Map.of("name", name, "nodeProjection", nodeProjection),
            "Invalid node projection, one or more labels not found: 'INVALID'"
        );

        assertGraphDoesNotExist(name);
    }

    @Test
    void failsOnNodeQueryWithNoResult() {
        assertError(
            "CALL gds.graph.project.cypher(" +
            "  'not_exist'," +
            "  'MATCH (n:NotExist) RETURN id(n) AS id'," +
            "  'MATCH (n)-->(m) RETURN id(n) AS source, id(m) AS target'" +
            ")",
            "Node-Query returned no nodes"
        );
    }

    @Test
    void failsOnInvalidAggregation() {
        Map<String, Object> relProjection = Map.of("A", Map.of(TYPE_KEY, "REL", AGGREGATION_KEY, "INVALID"));

        assertError(
            "CALL gds.graph.project('g', '*', $relProjection)",
            Map.of("relProjection", relProjection),
            "Aggregation `INVALID` is not supported."
        );
    }

    @Test
    void failsOnInvalidOrientation() {
        Map<String, Object> relProjection = Map.of("A", Map.of(TYPE_KEY, "REL", ORIENTATION_KEY, "INVALID"));

        assertError(
            "CALL gds.graph.project('g', '*', $relProjection)",
            Map.of("relProjection", relProjection),
            "Orientation `INVALID` is not supported."
        );
    }

    @Test
    void failsOnInvalidProjection() {
        Map<String, Object> relProjection = Map.of("A", Map.of(TYPE_KEY, "REL", ORIENTATION_KEY, "INVALID"));

        assertError(
            "CALL gds.graph.project('g', '*', $relProjection)",
            Map.of("relProjection", relProjection),
            "Orientation `INVALID` is not supported."
        );
    }

    @Test
    void failsOnWriteQuery() {
        String writeQueryNodes = "CREATE (n) RETURN id(n) AS id";
        String writeQueryRelationships = "CREATE (n)-[r:R]->(m) RETURN id(n) AS source, id(m) AS target";
        String query = "CALL gds.graph.project.cypher('dragons', $nodeQuery, $relQuery)";

        assertError(
            query,
            Map.of("relQuery", ALL_RELATIONSHIPS_QUERY, "nodeQuery", writeQueryNodes),
            "Query must be read only. Query: "
        );

        assertError(
            query,
            Map.of("nodeQuery", ALL_NODES_QUERY, "relQuery", writeQueryRelationships),
            "Query must be read only. Query: "
        );
    }

    @Test
    void failsOnMissingIdColumn() {
        String query =
            "CALL gds.graph.project.cypher(" +
            "   'cypherGraph', " +
            "   'RETURN 1 AS foo', " +
            "   'RETURN 0 AS source, 1 AS target'" +
            ")";

        assertError(query, "Invalid node query, required column(s) not found: 'id' - did you specify 'AS id'?");
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = ';',
        value = {
            "RETURN 0 AS foo, 1 AS target;source",
            "RETURN 0 AS source, 1 AS foo;target",
            "RETURN 0 AS foo, 1 AS bar;source,target",
        })
    void failsOnMissingSourceAndTargetColumns(String returnClause, String missingColumns) {
        String query = formatWithLocale(
            "CALL gds.graph.project.cypher(" +
            "   'cypherGraph', " +
            "   'RETURN 1 AS id', " +
            "   '%s'" +
            ")", returnClause);

        var missingColumnsArray = Arrays.asList(missingColumns.split(","));
        assertError(query, formatWithLocale(
            "Invalid relationship query, required column(s) not found: '%s' - did you specify %s?",
            StringJoining.join(missingColumnsArray, "', '"),
            StringJoining.joinVerbose(missingColumnsArray.stream()
                .map(c -> "'AS " + c + "'")
                .collect(Collectors.toList()))
        ));
    }

    @Test
    void failsOnInvalidNeoType() {
        String name = "g";

        String graphCreateQuery =
            "CALL gds.graph.project(" +
            "   $name, " +
            "   '*'," +
            "   {" +
            "       REL: {" +
            "           type: 'INVALID'" +
            "       }" +
            "   }" +
            ")";

        assertError(
            graphCreateQuery,
            Map.of("name", name),
            "Invalid relationship projection, one or more relationship types not found: 'INVALID'"
        );

        assertGraphDoesNotExist(name);
    }

    @Test
    void failsForUndirectedAndIndexInverse() {
        String name = "g";

        assertThatThrownBy(() ->
            runQuery(
                "CALL gds.graph.project($name, '*', $relProjection)",
                Map.of(
                    "name",
                    name,
                    "relProjection",
                    Map.of("CONNECTS", Map.of("type", "REL", "orientation", "UNDIRECTED", "indexInverse", true))
                )
            )
        )
            .rootCause()
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("REL")
            .hasMessageContaining("cannot be UNDIRECTED and inverse indexed");

        assertGraphDoesNotExist(name);
    }

    @Test
    void cypherCreationShouldNotReturnProjections() {
        runQueryWithResultConsumer("CALL gds.graph.project.cypher('test', '*', '*')", result -> {
            assertFalse(result.columns().contains(NODE_PROJECTION_KEY));
            assertFalse(result.columns().contains(RELATIONSHIP_PROJECTION_KEY));
        });
    }

    @Test
    void failTasksOnFailure() {
        runQuery("CREATE ({prop: \"stringProp\"}), ()");
        var jobId = new JobId();
        assertThatThrownBy(() ->
            runQuery(
                "CALL gds.graph.project('g', '*', '*', {nodeProperties: \"prop\", jobId: $jobId}) YIELD nodeProjection",
                Map.of("jobId", jobId.value())))
            .isInstanceOf(Exception.class)
            .hasMessageContaining("Loading of values of type String is currently not supported");;

        var taskStore = GraphDatabaseApiProxy.resolveDependency(db, TaskStore.class);
        Assertions.assertThat(taskStore.ongoingTaskCount()).isZero();
        Assertions.assertThat(taskStore.query(jobId))
            .map(UserTask::task)
            .allSatisfy(task -> Assertions.assertThat(task.status()).isEqualTo(Status.FAILED));
    }

    private Graph relPropertyGraph(String graphName, RelationshipType relationshipType, String property) {
        return GraphStoreCatalog
            .get(getUsername(), DatabaseId.of(db.databaseName()), graphName)
            .graphStore()
            .getGraph(relationshipType, Optional.of(property));
    }

    // Arguments for parameterised tests

    static Stream<Arguments> nodeProjectionVariants() {
        return Stream.of(
            Arguments.of(
                "default neo label",
                singletonMap("A", emptyMap()),
                Map.of("A", Map.of(LABEL_KEY, "A", PROPERTIES_KEY, emptyMap()))
            ),
            Arguments.of(
                "aliased node label",
                Map.of("B", Map.of(LABEL_KEY, "A")),
                Map.of("B", Map.of(LABEL_KEY, "A", PROPERTIES_KEY, emptyMap()))
            ),
            Arguments.of(
                "node projection as list",
                singletonList("A"),
                Map.of("A", Map.of(LABEL_KEY, "A", PROPERTIES_KEY, emptyMap()))
            )
        );
    }

    static Stream<Arguments> nodeProperties() {
        return Stream.of(
            Arguments.of(
                Map.of("age", Map.of("property", "age")),
                Map.of("age", mapWithNulls("property", "age", "defaultValue", null))
            ),
            Arguments.of(
                Map.of("weight", Map.of("property", "age", "defaultValue", 3D)),
                Map.of("weight", Map.of("property", "age", "defaultValue", 3D))
            ),
            Arguments.of(
                singletonList("age"),
                Map.of("age", mapWithNulls("property", "age", "defaultValue", null))
            ),
            Arguments.of(
                Map.of("weight", "age"),
                Map.of("weight", mapWithNulls("property", "age", "defaultValue", null))
            )
        );
    }

    static Stream<Arguments> relationshipProperties() {
        return Stream.of(
            Arguments.of(
                Map.of("weight", Map.of("property", "weight")),
                Map.of("weight", mapWithNulls(
                    "property", "weight",
                    "defaultValue", null,
                    AGGREGATION_KEY, "DEFAULT"
                ))
            ),
            Arguments.of(
                Map.of("score", Map.of(
                    "property", "weight",
                    "defaultValue", 3D
                )),
                Map.of("score", Map.of(
                        "property", "weight",
                        "defaultValue", 3D,
                        AGGREGATION_KEY, "DEFAULT"
                    )
                )
            ),
            Arguments.of(
                singletonList("weight"),
                Map.of("weight", mapWithNulls(
                    "property", "weight",
                    "defaultValue", null,
                    AGGREGATION_KEY, "DEFAULT"
                ))
            ),
            Arguments.of(
                Map.of("score", "weight"),
                Map.of("score", mapWithNulls(
                    "property", "weight",
                    "defaultValue", null,
                    AGGREGATION_KEY, "DEFAULT"
                ))
            )
        );
    }

    static Stream<Arguments> relationshipProjectionVariants() {
        return Stream.of(
            Arguments.of(
                "default neo type",
                singletonMap("REL", emptyMap()),
                Map.of(
                    "REL",
                    Map.of(
                        "type", "REL",
                        ORIENTATION_KEY, "NATURAL",
                        AGGREGATION_KEY, "DEFAULT",
                        INDEX_INVERSE_KEY, false,
                        PROPERTIES_KEY, emptyMap()
                    )
                )
            ),
            Arguments.of(
                "aliased rel type",
                Map.of("CONNECTS", Map.of("type", "REL")),
                Map.of(
                    "CONNECTS",
                    Map.of(
                        "type", "REL",
                        ORIENTATION_KEY, "NATURAL",
                        AGGREGATION_KEY, "DEFAULT",
                        INDEX_INVERSE_KEY, false,
                        PROPERTIES_KEY, emptyMap()
                    )
                )
            ),
            Arguments.of(
                "inverse indexed rel type",
                Map.of("CONNECTS", Map.of("type", "REL", "indexInverse", true)),
                Map.of(
                    "CONNECTS",
                    Map.of(
                        "type", "REL",
                        ORIENTATION_KEY, "NATURAL",
                        AGGREGATION_KEY, "DEFAULT",
                        INDEX_INVERSE_KEY, true,
                        PROPERTIES_KEY, emptyMap()
                    )
                )
            ),
            Arguments.of(
                "inverse indexed and non-index rel type",
                Map.of(
                    "CONNECTS", Map.of("type", "REL", "indexInverse", false),
                    "CONNECTS_INDEXED", Map.of("type", "REL", "indexInverse", true)
                ),
                Map.of(
                    "CONNECTS",
                    Map.of(
                        "type", "REL",
                        ORIENTATION_KEY, "NATURAL",
                        AGGREGATION_KEY, "DEFAULT",
                        INDEX_INVERSE_KEY, false,
                        PROPERTIES_KEY, emptyMap()
                    ),
                    "CONNECTS_INDEXED",
                    Map.of(
                        "type", "REL",
                        ORIENTATION_KEY, "NATURAL",
                        AGGREGATION_KEY, "DEFAULT",
                        INDEX_INVERSE_KEY, true,
                        PROPERTIES_KEY, emptyMap()
                    )
                )
            ),
            Arguments.of(
                "rel projection as list",
                singletonList("REL"),
                Map.of(
                    "REL",
                    Map.of(
                        "type", "REL",
                        ORIENTATION_KEY, "NATURAL",
                        AGGREGATION_KEY, "DEFAULT",
                        INDEX_INVERSE_KEY, false,
                        PROPERTIES_KEY, emptyMap()
                    )
                )
            )
        );
    }

    static Stream<String> relationshipOrientations() {
        return Stream.of(
            "NATURAL",
            "REVERSE",
            "UNDIRECTED"
        );
    }

    static Stream<String> invalidGraphNames() {
        return Stream.of("", "   ", "           ", "\r\n\t", null);
    }

    private static Map<String, Object> mapWithNulls(Object... objects) {
        var map = new HashMap<String, Object>();
        int i = 0;
        while (i < objects.length) {
            map.put((String) objects[i++], objects[i++]);
        }
        return map;
    }
}
