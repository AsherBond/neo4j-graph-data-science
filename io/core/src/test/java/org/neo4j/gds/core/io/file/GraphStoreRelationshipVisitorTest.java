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
package org.neo4j.gds.core.io.file;

import org.junit.jupiter.api.Test;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.api.DatabaseId;
import org.neo4j.gds.api.DatabaseInfo;
import org.neo4j.gds.api.DatabaseInfo.DatabaseLocation;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.schema.MutableGraphSchema;
import org.neo4j.gds.api.schema.MutableNodeSchema;
import org.neo4j.gds.core.concurrency.Concurrency;
import org.neo4j.gds.core.io.GraphStoreRelationshipVisitor;
import org.neo4j.gds.core.loading.Capabilities.WriteMode;
import org.neo4j.gds.core.loading.GraphStoreBuilder;
import org.neo4j.gds.core.loading.ImmutableNodes;
import org.neo4j.gds.core.loading.ImmutableStaticCapabilities;
import org.neo4j.gds.core.loading.construction.GraphFactory;
import org.neo4j.gds.core.loading.construction.RelationshipsBuilder;
import org.neo4j.gds.core.loading.construction.RelationshipsBuilderBuilder;
import org.neo4j.gds.extension.GdlExtension;
import org.neo4j.gds.extension.GdlGraph;
import org.neo4j.gds.extension.IdFunction;
import org.neo4j.gds.extension.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.gds.TestSupport.assertGraphEquals;
import static org.neo4j.gds.TestSupport.fromGdl;

@GdlExtension
class GraphStoreRelationshipVisitorTest {

    @GdlGraph
    static String DB_CYPHER = "CREATE (a:A)-[:R {p: 1.23}]->(b:A)-[:R1 {r: 1337}]->(c:B)-[:R1 {r: 42}]->(a)-[:R2]->(b)";

    @Inject
    private GraphStore graphStore;

    @Inject
    private Graph graph;

    @GdlGraph(graphNamePrefix = "multipleProps")
    static String MULTI_PROPS_CYPHER = "(a)-[:R {p: 42.0D, r: 13.37D}]->(b)";

    @Inject
    private Graph multiplePropsGraph;

    @Inject
    private IdFunction multiplePropsIdFunction;

    @Test
    void shouldAddRelationshipsToRelationshipBuilder() {
        var relationshipSchema = graphStore.schema().relationshipSchema();

        Map<String, RelationshipsBuilder> relationshipBuildersByType = new HashMap<>();
        Supplier<RelationshipsBuilderBuilder> relationshipBuilderSupplier = () -> GraphFactory
            .initRelationshipsBuilder()
            .concurrency(new Concurrency(1))
            .nodes(graph);
        var relationshipVisitor = new GraphStoreRelationshipVisitor(
            relationshipSchema,
            relationshipBuilderSupplier,
            relationshipBuildersByType,
            List.of()
        );

        var relationshipTypeR = RelationshipType.of("R");
        var relationshipTypeR1 = RelationshipType.of("R1");
        var relationshipTypeR2 = RelationshipType.of("R2");
        graph.forEachNode(nodeId -> {
            visitRelationshipType(relationshipVisitor, nodeId, relationshipTypeR, Optional.of("p"));
            visitRelationshipType(relationshipVisitor, nodeId, relationshipTypeR1, Optional.of("r"));
            visitRelationshipType(relationshipVisitor, nodeId, relationshipTypeR2, Optional.empty());
            return true;
        });

        var actualGraph = createGraph(graph, relationshipBuildersByType, 4L);
        assertGraphEquals(graph, actualGraph);
    }

    @Test
    void shouldBuildRelationshipsWithMultipleProperties() {
        GraphStoreRelationshipVisitor.Builder relationshipVisitorBuilder = new GraphStoreRelationshipVisitor.Builder();
        Map<String, RelationshipsBuilder> relationshipBuildersByType = new ConcurrentHashMap<>();
        var relationshipVisitor = relationshipVisitorBuilder
            .withNodes(multiplePropsGraph)
            .withRelationshipSchema(multiplePropsGraph.schema().relationshipSchema())
            .withRelationshipBuildersToTypeResultMap(relationshipBuildersByType)
            .withInverseIndexedRelationshipTypes(List.of())
            .withAllocationTracker()
            .withConcurrency(new Concurrency(1))
            .build();

        relationshipVisitor.type("R");
        relationshipVisitor.startId(multiplePropsIdFunction.of("a"));
        relationshipVisitor.endId(multiplePropsIdFunction.of("b"));
        relationshipVisitor.property("p", 42.0D, false);
        relationshipVisitor.property("r", 13.37D, false);
        relationshipVisitor.endOfEntity();

        var actualGraph = createGraph(multiplePropsGraph, relationshipBuildersByType, 1L);
        assertGraphEquals(multiplePropsGraph, actualGraph);
    }

    @Test
    void shouldBuildRelationshipsWithInverseIndex() {
        var expectedGraph = fromGdl("(a)-[R]->(b)");

        GraphStoreRelationshipVisitor.Builder relationshipVisitorBuilder = new GraphStoreRelationshipVisitor.Builder();
        Map<String, RelationshipsBuilder> relationshipBuildersByType = new ConcurrentHashMap<>();
        var relationshipVisitor = relationshipVisitorBuilder
            .withNodes(expectedGraph)
            .withRelationshipSchema(expectedGraph.schema().relationshipSchema())
            .withRelationshipBuildersToTypeResultMap(relationshipBuildersByType)
            .withInverseIndexedRelationshipTypes(List.of(RelationshipType.of("R")))
            .withAllocationTracker()
            .withConcurrency(new Concurrency(1))
            .build();

        relationshipVisitor.type("R");
        relationshipVisitor.startId(expectedGraph.toOriginalNodeId("a"));
        relationshipVisitor.endId(expectedGraph.toOriginalNodeId("b"));
        relationshipVisitor.endOfEntity();

        assertThat(relationshipBuildersByType.get("R").build().inverseTopology()).isPresent();
    }

    private Graph createGraph(
        Graph expectedGraph,
        Map<String, RelationshipsBuilder> relationshipBuildersByType,
        long expectedImportedRelationshipsCount
    ) {
        var actualRelationships = FileToGraphStoreImporter.relationshipImportResult(relationshipBuildersByType);
        var actualRelationshipCount = actualRelationships
            .importResults()
            .values()
            .stream()
            .mapToLong(r -> r.topology().elementCount())
            .sum();

        assertThat(actualRelationshipCount).isEqualTo(expectedImportedRelationshipsCount);

        var nodes = ImmutableNodes.builder()
            .idMap(expectedGraph)
            .schema(MutableNodeSchema.from(expectedGraph.schema().nodeSchema()))
            .build();

        return new GraphStoreBuilder()
            .schema(MutableGraphSchema.from(expectedGraph.schema()))
            .capabilities(ImmutableStaticCapabilities.of(WriteMode.LOCAL))
            .nodes(nodes)
            .relationshipImportResult(actualRelationships)
            .databaseInfo(DatabaseInfo.of(DatabaseId.random(), DatabaseLocation.LOCAL))
            .concurrency(new Concurrency(1))
            .build()
            .getUnion();
    }

    private void visitRelationshipType(
        GraphStoreRelationshipVisitor relationshipVisitor,
        long nodeId,
        RelationshipType relationshipType,
        Optional<String> relationshipPropertyKey
    ) {
        graph
            .relationshipTypeFilteredGraph(Set.of(relationshipType))
            .forEachRelationship(nodeId, 0.0, (source, target, propertyValue) -> {
                relationshipVisitor.startId(graph.toOriginalNodeId(source));
                relationshipVisitor.endId(graph.toOriginalNodeId(target));
                relationshipPropertyKey
                    .ifPresent(propertyKey -> relationshipVisitor.property(propertyKey, propertyValue, false));
                relationshipVisitor.type(relationshipType.name());
                relationshipVisitor.endOfEntity();
                return true;
            });
    }
}
