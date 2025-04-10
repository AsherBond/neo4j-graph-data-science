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
package org.neo4j.gds.procedures.algorithms.miscellaneous;

import org.neo4j.gds.algorithms.misc.ScaledPropertiesNodePropertyValues;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.applications.algorithms.machinery.StreamResultBuilder;
import org.neo4j.gds.scaleproperties.ScalePropertiesResult;

import java.util.Optional;
import java.util.stream.LongStream;
import java.util.stream.Stream;

class ScalePropertiesResultBuilderForStreamMode implements StreamResultBuilder<ScalePropertiesResult, ScalePropertiesStreamResult> {

    @Override
    public Stream<ScalePropertiesStreamResult> build(
        Graph graph,
        GraphStore graphStore,
        Optional<ScalePropertiesResult> result
    ) {
        if (result.isEmpty()) return Stream.empty();

        var scalePropertiesResult = result.get();

        var nodeProperties = new ScaledPropertiesNodePropertyValues(
            graph.nodeCount(),
            scalePropertiesResult.scaledProperties()
        );

        return LongStream
            .range(0, graph.nodeCount())
            .mapToObj(nodeId ->  ScalePropertiesStreamResult.create(
                graph.toOriginalNodeId(nodeId),
                nodeProperties.doubleArrayValue(nodeId)
            ));
    }
}
