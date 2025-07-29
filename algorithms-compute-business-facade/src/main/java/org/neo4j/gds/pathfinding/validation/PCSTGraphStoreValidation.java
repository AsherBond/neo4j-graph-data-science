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
package org.neo4j.gds.pathfinding.validation;

import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.nodeproperties.ValueType;
import org.neo4j.gds.core.loading.validation.GraphStoreValidation;
import org.neo4j.gds.utils.StringFormatting;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

public class PCSTGraphStoreValidation extends GraphStoreValidation {

    private final String prizeProperty;

    public PCSTGraphStoreValidation(String prizeProperty) {
        this.prizeProperty = prizeProperty;
    }

    @Override
    protected void validateAlgorithmRequirements(
        GraphStore graphStore,
        Collection<NodeLabel> selectedLabels,
        Collection<RelationshipType> selectedRelationshipTypes
    ) {
        if (!graphStore.schema().filterRelationshipTypes(Set.copyOf(selectedRelationshipTypes)).isUndirected()) {
            throw new IllegalArgumentException(formatWithLocale(
                "Prize-collecting Steineer requires relationship projections to be UNDIRECTED. " +
                    "Selected relationships `%s` are not all undirected.",
                selectedRelationshipTypes.stream().map(RelationshipType::name).collect(Collectors.toSet())
            ));
        }

        if (!graphStore.nodePropertyKeys().contains(prizeProperty)) {
            throw new IllegalArgumentException(
                StringFormatting.formatWithLocale(
                    "Prize node property value type [%s] not found in the graph.",
                    prizeProperty
                )
            );
        }
        var valueType = graphStore.nodeProperty(prizeProperty).valueType();
        if (valueType == ValueType.DOUBLE) {
            return;
        }
        throw new IllegalArgumentException(
            StringFormatting.formatWithLocale(
                "Unsupported node property value type [%s]. Value type required: [%s]",
                valueType,
                ValueType.DOUBLE
            )
        );
    }
}
