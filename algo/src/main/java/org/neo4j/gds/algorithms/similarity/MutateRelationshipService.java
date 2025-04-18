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
package org.neo4j.gds.algorithms.similarity;

import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.applications.algorithms.machinery.SingleTypeRelationshipsProducer;
import org.neo4j.gds.applications.algorithms.metadata.RelationshipsWritten;
import org.neo4j.gds.core.loading.SingleTypeRelationships;
import org.neo4j.gds.logging.Log;

import java.util.Optional;

public class MutateRelationshipService {
    private final Log log;

    public MutateRelationshipService(Log log) {
        this.log = log;
    }

    public RelationshipsWritten mutate(
        GraphStore graphStore,
        String mutateRelationshipType,
        String mutateProperty,
        SingleTypeRelationshipsProducer singleTypeRelationshipsProducer
    ) {
        var resultRelationships = singleTypeRelationshipsProducer.createRelationships(
            mutateRelationshipType,
            Optional.of(mutateProperty)
        );

        return mutate(graphStore,resultRelationships);
    }

    public RelationshipsWritten mutate(
        GraphStore graphStore,
        String mutateRelationshipType,
        SingleTypeRelationshipsProducer singleTypeRelationshipsProducer
    ) {
        var resultRelationships = singleTypeRelationshipsProducer.createRelationships(
            mutateRelationshipType,
            Optional.empty()
        );

       return mutate(graphStore,resultRelationships);
    }

    public RelationshipsWritten mutate(
        GraphStore graphStore,
        SingleTypeRelationships singleTypeRelationships
    ) {

        log.info("Updating in-memory graph store");

        graphStore.addRelationshipType(singleTypeRelationships);

        return new RelationshipsWritten(singleTypeRelationships.topology().elementCount());
    }
}
