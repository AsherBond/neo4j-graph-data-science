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
package org.neo4j.gds.applications.algorithms.similarity;

import org.apache.commons.lang3.tuple.Pair;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.applications.algorithms.machinery.MutateRelationshipService;
import org.neo4j.gds.applications.algorithms.machinery.MutateStep;
import org.neo4j.gds.applications.algorithms.metadata.RelationshipsWritten;
import org.neo4j.gds.similarity.filteredknn.FilteredKnnMutateConfig;
import org.neo4j.gds.similarity.filteredknn.FilteredKnnResult;

import java.util.Map;

final class FilteredKnnMutateStep implements MutateStep<FilteredKnnResult, Pair<RelationshipsWritten, Map<String, Object>>> {
    private final SimilarityMutation similarityMutation;
    private final FilteredKnnMutateConfig configuration;
    private final boolean shouldComputeSimilarityDistribution;

    private FilteredKnnMutateStep(
        SimilarityMutation similarityMutation,
        FilteredKnnMutateConfig configuration,
        boolean shouldComputeSimilarityDistribution
    ) {
        this.similarityMutation = similarityMutation;
        this.configuration = configuration;
        this.shouldComputeSimilarityDistribution = shouldComputeSimilarityDistribution;
    }

    static FilteredKnnMutateStep create(
        MutateRelationshipService mutateRelationshipService,
        FilteredKnnMutateConfig configuration,
        boolean shouldComputeSimilarityDistribution
    ) {
        var similarityMutation = new SimilarityMutation(mutateRelationshipService);
        return new FilteredKnnMutateStep(similarityMutation, configuration, shouldComputeSimilarityDistribution);
    }

    @Override
    public Pair<RelationshipsWritten, Map<String, Object>> execute(
        Graph graph,
        GraphStore graphStore,
        FilteredKnnResult result
    ) {
        return similarityMutation.execute(
            graph,
            graphStore,
            configuration,
            configuration,
            result.similarityResultStream(),
            shouldComputeSimilarityDistribution
        );
    }
}
