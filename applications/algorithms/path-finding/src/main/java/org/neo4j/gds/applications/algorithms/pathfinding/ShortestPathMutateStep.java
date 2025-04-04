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
package org.neo4j.gds.applications.algorithms.pathfinding;

import org.neo4j.gds.algorithms.similarity.MutateRelationshipService;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.applications.algorithms.machinery.MutateStep;
import org.neo4j.gds.applications.algorithms.metadata.RelationshipsWritten;
import org.neo4j.gds.config.MutateRelationshipConfig;
import org.neo4j.gds.paths.dijkstra.PathFindingResult;

class ShortestPathMutateStep implements MutateStep<PathFindingResult, RelationshipsWritten> {
    private final MutateRelationshipConfig configuration;
    private final MutateRelationshipService mutateRelationshipService;

    ShortestPathMutateStep(MutateRelationshipService mutateRelationshipService, MutateRelationshipConfig configuration) {
        this.configuration = configuration;
        this.mutateRelationshipService = mutateRelationshipService;
    }

    @Override
    public RelationshipsWritten execute(
        Graph graph,
        GraphStore graphStore,
        PathFindingResult result
    ) {

        var singleTypeRelationshipsProducer = PathFindingSingleTypeRelationshipsFactory.fromPathFindingResult(
            result,
            graph
        );

        return mutateRelationshipService.mutate(
            graphStore,
            configuration.mutateRelationshipType(),
            singleTypeRelationshipsProducer
        );
    }
}
