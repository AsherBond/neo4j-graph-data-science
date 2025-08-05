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
package org.neo4j.gds.procedures.algorithms.pathfinding;

import org.neo4j.gds.api.CloseableResourceRegistry;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.IdMap;
import org.neo4j.gds.results.ResultTransformer;
import org.neo4j.graphdb.RelationshipType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class RandomWalkStreamResultTransformer implements ResultTransformer<Stream<long[]>, Stream<RandomWalkStreamResult>> {

    private static final String RELATIONSHIP_TYPE_NAME = "NEXT";
    private final Graph graph;
    private final CloseableResourceRegistry closeableResourceRegistry;
    private final PathFactoryFacade pathFactoryFacade;

    public RandomWalkStreamResultTransformer(
        Graph graph,
        CloseableResourceRegistry closeableResourceRegistry,
        PathFactoryFacade pathFactoryFacade
    ) {
        this.graph = graph;
        this.closeableResourceRegistry = closeableResourceRegistry;
        this.pathFactoryFacade = pathFactoryFacade;
    }

    @Override
    public Stream<RandomWalkStreamResult> apply(Stream<long[]> streamOfLongArrays) {

        var resultStream = streamOfLongArrays.map(nodes -> {
            var translatedNodes = translateInternalToNeoIds(nodes, graph);
            var path = pathFactoryFacade.createPath(translatedNodes, RelationshipType.withName(RELATIONSHIP_TYPE_NAME));
            return new RandomWalkStreamResult(translatedNodes, path);
        });

        closeableResourceRegistry.register(resultStream);

        return resultStream;
    }

    private List<Long> translateInternalToNeoIds(long[] nodes, IdMap idMap) {
        var translatedNodes = new ArrayList<Long>(nodes.length);
        for (int i = 0; i < nodes.length; i++) {
            translatedNodes.add(i, idMap.toOriginalNodeId(nodes[i]));
        }
        return translatedNodes;
    }

}
