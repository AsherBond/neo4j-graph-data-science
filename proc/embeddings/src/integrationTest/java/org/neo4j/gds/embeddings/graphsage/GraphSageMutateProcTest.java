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
package org.neo4j.gds.embeddings.graphsage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.gds.BaseProcTest;
import org.neo4j.gds.GdsCypher;
import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.NodeProjection;
import org.neo4j.gds.NodeProjections;
import org.neo4j.gds.Orientation;
import org.neo4j.gds.PropertyMapping;
import org.neo4j.gds.RelationshipProjection;
import org.neo4j.gds.RelationshipProjections;
import org.neo4j.gds.StoreLoaderWithConfigBuilder;
import org.neo4j.gds.catalog.GraphProjectProc;
import org.neo4j.gds.core.loading.GraphStoreCatalog;
import org.neo4j.gds.extension.Neo4jGraph;
import org.neo4j.gds.extension.Neo4jModelCatalogExtension;
import org.neo4j.gds.projection.GraphProjectFromStoreConfig;
import org.neo4j.gds.projection.GraphProjectFromStoreConfigImpl;
import org.neo4j.gds.utils.StringJoining;
import org.neo4j.graphdb.QueryExecutionException;
import org.neo4j.graphdb.Result;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.gds.ElementProjection.PROJECT_ALL;

@Neo4jModelCatalogExtension
class GraphSageMutateProcTest extends BaseProcTest {

    @Neo4jGraph
    private static final String DB_CYPHER =
        "CREATE" +
        "  (a:King{ name: 'A', age: 20, birth_year: 200, death_year: 300 })" +
        ", (b:King{ name: 'B', age: 12, birth_year: 232, death_year: 300 })" +
        ", (c:King{ name: 'C', age: 67, birth_year: 212, death_year: 300 })" +
        ", (d:King{ name: 'D', age: 78, birth_year: 245, death_year: 300 })" +
        ", (e:King{ name: 'E', age: 32, birth_year: 256, death_year: 300 })" +
        ", (f:King{ name: 'F', age: 32, birth_year: 214, death_year: 300 })" +
        ", (g:King{ name: 'G', age: 35, birth_year: 214, death_year: 300 })" +
        ", (h:King{ name: 'H', age: 56, birth_year: 253, death_year: 300 })" +
        ", (i:King{ name: 'I', age: 62, birth_year: 267, death_year: 300 })" +
        ", (j:King{ name: 'J', age: 44, birth_year: 289, death_year: 300 })" +
        ", (k:King{ name: 'K', age: 89, birth_year: 211, death_year: 300 })" +
        ", (l:King{ name: 'L', age: 99, birth_year: 201, death_year: 300 })" +
        ", (m:King{ name: 'M', age: 99, birth_year: 201, death_year: 300 })" +
        ", (n:King{ name: 'N', age: 99, birth_year: 201, death_year: 300 })" +
        ", (o:King{ name: 'O', age: 99, birth_year: 201, death_year: 300 })" +
        ", (a)-[:REL {weight: 1.0}]->(b)" +
        ", (a)-[:REL {weight: 5.0}]->(c)" +
        ", (b)-[:REL {weight: 42.0}]->(c)" +
        ", (b)-[:REL {weight: 10.0}]->(d)" +
        ", (c)-[:REL {weight: 62.0}]->(e)" +
        ", (d)-[:REL {weight: 1.0}]->(e)" +
        ", (d)-[:REL {weight: 1.0}]->(f)" +
        ", (e)-[:REL {weight: 1.0}]->(f)" +
        ", (e)-[:REL {weight: 4.0}]->(g)" +
        ", (h)-[:REL {weight: 1.0}]->(i)" +
        ", (i)-[:REL {weight: -1.0}]->(j)" +
        ", (j)-[:REL {weight: 1.0}]->(k)" +
        ", (j)-[:REL {weight: -10.0}]->(l)" +
        ", (k)-[:REL {weight: 1.0}]->(l)";

    static String graphName = "embeddingsGraph";

    static String modelName = "graphSageModel";

    @BeforeEach
    void setup() throws Exception {
        registerProcedures(
            GraphProjectProc.class,
            GraphSageMutateProc.class,
            GraphSageTrainProc.class
        );

        String query = GdsCypher.call(graphName)
            .graphProject()
            .withNodeLabel("King")
            .withNodeProperty(PropertyMapping.of("age", 1.0))
            .withNodeProperty(PropertyMapping.of("birth_year", 1.0))
            .withNodeProperty(PropertyMapping.of("death_year", 1.0))
            .withRelationshipType(
                "R",
                RelationshipProjection.of(
                    "*",
                    Orientation.UNDIRECTED
                )
            )
            .withRelationshipProperty("weight")
            .yields();

        runQuery(query);
    }

    @Test
    void testMutation() {
        var trainQuery = GdsCypher.call(graphName)
            .algo("gds.beta.graphSage")
            .trainMode()
            .addParameter("sampleSizes", List.of(2, 4))
            .addParameter("featureProperties", List.of("age", "birth_year", "death_year"))
            .addParameter("embeddingDimension", 16)
            .addParameter("activationFunction", "SIGMOID")
            .addParameter("aggregator", "mean")
            .addParameter("modelName", modelName)
            .yields();

        runQuery(trainQuery);

        var mutatePropertyKey = "embedding";
        var query = GdsCypher.call("embeddingsGraph")
            .algo("gds.beta.graphSage")
            .mutateMode()
            .addParameter("mutateProperty", mutatePropertyKey)
            .addParameter("modelName", modelName)
            .yields();

        var graphStore = GraphStoreCatalog.get(getUsername(), db.databaseName(), graphName).graphStore();

        var rowCount = runQueryWithRowConsumer(query, row -> {
            assertThat(row.get("nodeCount")).isEqualTo(graphStore.nodeCount());
            assertThat(row.get("nodePropertiesWritten")).isEqualTo(graphStore.nodeCount());
            assertThat(row.get("preProcessingMillis")).isNotEqualTo(-1L);
            assertThat(row.get("computeMillis")).isNotEqualTo(-1L);
            assertThat(row.get("mutateMillis")).isNotEqualTo(-1L);
            assertThat(row.get("configuration")).isInstanceOf(Map.class);
        });

        assertThat(rowCount)
            .as("`mutate` mode should always return one row")
            .isEqualTo(1);

        var embeddingNodePropertyValues = graphStore.nodeProperty(mutatePropertyKey).values();
        graphStore.nodes().forEachNode(nodeId -> {
            assertEquals(16, embeddingNodePropertyValues.doubleArrayValue(nodeId).length);
            return true;
        });
    }


    @ParameterizedTest(name = "Graph Properties: {2} - Algo Properties: {1}")
    @MethodSource("missingNodeProperties")
    void shouldFailOnMissingNodeProperties(
        GraphProjectFromStoreConfig config,
        List<String> nodeProperties,
        List<String> graphProperties,
        List<String> label
    ) {
        String trainQuery = GdsCypher.call(graphName)
            .algo("gds.beta.graphSage")
            .trainMode()
            .addParameter("sampleSizes", List.of(2, 4))
            .addParameter("featureProperties", List.of("age", "birth_year", "death_year"))
            .addParameter("embeddingDimension", 16)
            .addParameter("activationFunction", "SIGMOID")
            .addParameter("aggregator", "mean")
            .addParameter("modelName", modelName)
            .yields();

        runQuery(trainQuery);

        var graphStore = new StoreLoaderWithConfigBuilder()
            .databaseService(db)
            .graphProjectConfig(config)
            .build()
            .graphStore();
        GraphStoreCatalog.set(config, graphStore);

        String query = GdsCypher.call(config.graphName())
            .algo("gds.beta.graphSage")
            .mutateMode()
            .addParameter("concurrency", 1)
            .addParameter("modelName", modelName)
            .addParameter("mutateProperty", modelName)
            .yields();

        assertThatThrownBy(() -> runQuery(query))
            .isInstanceOf(QueryExecutionException.class)
            .hasRootCauseInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("The feature properties %s are not present for all requested labels.", StringJoining.join(nodeProperties))
            .hasMessageContaining("Requested labels: %s", StringJoining.join(label))
            .hasMessageContaining("Properties available on all requested labels: %s", StringJoining.join(graphProperties));
    }

    private static Stream<Arguments> missingNodeProperties() {
        return Stream.of(
            Arguments.of(
                GraphProjectFromStoreConfigImpl.builder()
                    .username("")
                    .graphName("implicitWeightedGraph")
                    .nodeProjections(NodeProjections.single(
                        NodeLabel.of("King"),
                        NodeProjection.builder()
                            .label("King")
                            .addProperty(
                                PropertyMapping.of("age")
                            ).build()
                    ))
                    .relationshipProjections(RelationshipProjections.fromString("REL")
                    ).build(),
                List.of("birth_year", "death_year"),
                List.of("age"),
                List.of("King")
            ),
            Arguments.of(
                GraphProjectFromStoreConfigImpl.builder()
                    .username("")
                    .graphName("implicitWeightedGraph")
                    .nodeProjections(NodeProjections.single(
                        NodeLabel.of("King"),
                        NodeProjection.builder()
                            .label("King")
                            .addProperties(
                                PropertyMapping.of("age"),
                                PropertyMapping.of("birth_year")
                            ).build()
                    ))
                    .relationshipProjections(RelationshipProjections.fromString("REL")
                    ).build(),
                List.of("death_year"),
                List.of("age", "birth_year"),
                List.of("King")
            ),
            Arguments.of(
                GraphProjectFromStoreConfigImpl.builder()
                    .graphName("")
                    .username("")
                    .nodeProjections(NodeProjections.fromString(PROJECT_ALL))
                    .relationshipProjections(RelationshipProjections.fromString(PROJECT_ALL))
                    .build(),
                List.of("age", "birth_year", "death_year"),
                List.of(),
                List.of("__ALL__")
            )
        );
    }

    @Test
    void shouldEstimateMemory() {
        var trainQuery = GdsCypher.call(graphName)
            .algo("gds.beta.graphSage")
            .trainMode()
            .addParameter("sampleSizes", List.of(2, 4))
            .addParameter("featureProperties", List.of("age", "birth_year", "death_year"))
            .addParameter("embeddingDimension", 16)
            .addParameter("activationFunction", "SIGMOID")
            .addParameter("aggregator", "mean")
            .addParameter("modelName", modelName)
            .yields();

        runQuery(trainQuery);

        var mutatePropertyKey = "embedding";
        var query = GdsCypher.call("embeddingsGraph")
            .algo("gds.beta.graphSage")
            .mutateEstimation()
            .addParameter("mutateProperty", mutatePropertyKey)
            .addParameter("modelName", modelName)
            .yields("requiredMemory");

        assertThat(runQuery(query, Result::resultAsString)).isEqualTo("+-------------------------+\n" +
            "| requiredMemory          |\n" +
            "+-------------------------+\n" +
            "| \"[233 KiB ... 237 KiB]\" |\n" +
            "+-------------------------+\n" +
            "1 row\n");


    }

}
