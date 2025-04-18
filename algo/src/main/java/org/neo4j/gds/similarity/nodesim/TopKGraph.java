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
package org.neo4j.gds.similarity.nodesim;

import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.api.DefaultValue;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphAdapter;
import org.neo4j.gds.api.PropertyState;
import org.neo4j.gds.api.nodeproperties.ValueType;
import org.neo4j.gds.api.properties.relationships.RelationshipConsumer;
import org.neo4j.gds.api.properties.relationships.RelationshipWithPropertyConsumer;
import org.neo4j.gds.api.schema.Direction;
import org.neo4j.gds.api.schema.GraphSchema;
import org.neo4j.gds.api.schema.ImmutableMutableGraphSchema;
import org.neo4j.gds.api.schema.ImmutableRelationshipPropertySchema;
import org.neo4j.gds.api.schema.MutableRelationshipSchema;
import org.neo4j.gds.api.schema.MutableRelationshipSchemaEntry;
import org.neo4j.gds.core.Aggregation;

import java.util.Map;

public class TopKGraph extends GraphAdapter {

    private final TopKMap topKMap;

    public TopKGraph(Graph graph, TopKMap topKMap) {
        super(graph);
        this.topKMap = topKMap;
    }

    @Override
    public int degree(long nodeId) {
        TopKMap.TopKList topKList = topKMap.get(nodeId);
        return topKList != null ? topKList.size() : 0;
    }

    @Override
    public boolean hasRelationshipProperty() {
        return true;
    }

    @Override
    public GraphSchema schema() {
        var type = RelationshipType.of("SIMILAR");
        var relationshipSchema = new MutableRelationshipSchema(
            Map.of(
                type,
                new MutableRelationshipSchemaEntry(
                    type,
                    Direction.DIRECTED,
                    Map.of(
                        "similarity",
                        ImmutableRelationshipPropertySchema.of(
                            "similarity",
                            ValueType.DOUBLE,
                            DefaultValue.DEFAULT,
                            PropertyState.TRANSIENT,
                            Aggregation.NONE
                        )
                    )
                )
            )
        );

        return ImmutableMutableGraphSchema.builder()
            .from(graph.schema())
            .relationshipSchema(relationshipSchema)
            .build();
    }

    @Override
    public boolean isMultiGraph() {
        return super.isMultiGraph();
    }

    @Override
    public long relationshipCount() {
        return topKMap.similarityPairCount();
    }

    @Override
    public void forEachRelationship(long node1, RelationshipConsumer consumer) {
        TopKMap.TopKList topKList = topKMap.get(node1);
        if (topKList != null) {
            topKList.forEach((node2, similarity) -> consumer.accept(node1, node2));
        }
    }

    @Override
    public void forEachRelationship(long node1, double fallbackValue, RelationshipWithPropertyConsumer consumer) {
        TopKMap.TopKList topKList = topKMap.get(node1);
        if (topKList != null) {
            topKList.forEach((node2, similarity) -> consumer.accept(node1, node2, similarity));
        }
   }

    @Override
    public Graph concurrentCopy() {
        return this;
    }
}
