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
package org.neo4j.gds.procedures.algorithms.embeddings;

import org.neo4j.gds.algorithms.embeddings.FloatEmbeddingNodePropertyValues;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.IdMap;
import org.neo4j.gds.applications.algorithms.machinery.StreamResultBuilder;
import org.neo4j.gds.embeddings.node2vec.Node2VecResult;

import java.util.Optional;
import java.util.stream.LongStream;
import java.util.stream.Stream;

class Node2VecResultBuilderForStreamMode implements StreamResultBuilder<Node2VecResult, DefaultNodeEmbeddingsStreamResult> {

    @Override
    public Stream<DefaultNodeEmbeddingsStreamResult> build(
        Graph graph,
        GraphStore graphStore,
        Optional<Node2VecResult> result
    ) {
        if (result.isEmpty()) return Stream.empty();

        var node2VecResult = result.get();

        var nodePropertyValues = new FloatEmbeddingNodePropertyValues(node2VecResult.embeddings());

        return LongStream
            .range(IdMap.START_NODE_ID, graph.nodeCount())
            .filter(nodePropertyValues::hasValue)
            .mapToObj(nodeId -> DefaultNodeEmbeddingsStreamResult.create(
                graph.toOriginalNodeId(nodeId),
                nodePropertyValues.floatArrayValue(nodeId)
            ));
    }
}
