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
import org.neo4j.gds.algorithms.similarity.MutateRelationshipService;
import org.neo4j.gds.api.GraphName;
import org.neo4j.gds.applications.algorithms.machinery.AlgorithmProcessingTemplateConvenience;
import org.neo4j.gds.applications.algorithms.machinery.ResultBuilder;
import org.neo4j.gds.applications.algorithms.metadata.RelationshipsWritten;
import org.neo4j.gds.similarity.filteredknn.FilteredKnnMutateConfig;
import org.neo4j.gds.similarity.filteredknn.FilteredKnnResult;
import org.neo4j.gds.similarity.filterednodesim.FilteredNodeSimilarityMutateConfig;
import org.neo4j.gds.similarity.knn.KnnMutateConfig;
import org.neo4j.gds.similarity.knn.KnnResult;
import org.neo4j.gds.similarity.nodesim.NodeSimilarityMutateConfig;
import org.neo4j.gds.similarity.nodesim.NodeSimilarityResult;

import java.util.Map;

import static org.neo4j.gds.applications.algorithms.machinery.AlgorithmLabel.FilteredKNN;
import static org.neo4j.gds.applications.algorithms.machinery.AlgorithmLabel.FilteredNodeSimilarity;
import static org.neo4j.gds.applications.algorithms.machinery.AlgorithmLabel.KNN;
import static org.neo4j.gds.applications.algorithms.machinery.AlgorithmLabel.NodeSimilarity;

public class SimilarityAlgorithmsMutateModeBusinessFacade {
    private final SimilarityAlgorithmsEstimationModeBusinessFacade estimationFacade;
    private final SimilarityAlgorithmsBusinessFacade similarityAlgorithms;
    private final AlgorithmProcessingTemplateConvenience algorithmProcessingTemplateConvenience;
    private final MutateRelationshipService mutateRelationshipService;

    public SimilarityAlgorithmsMutateModeBusinessFacade(
        SimilarityAlgorithmsEstimationModeBusinessFacade estimationFacade,
        SimilarityAlgorithmsBusinessFacade similarityAlgorithms,
        AlgorithmProcessingTemplateConvenience algorithmProcessingTemplateConvenience,
        MutateRelationshipService mutateRelationshipService
    ) {
        this.estimationFacade = estimationFacade;
        this.similarityAlgorithms = similarityAlgorithms;
        this.algorithmProcessingTemplateConvenience = algorithmProcessingTemplateConvenience;
        this.mutateRelationshipService = mutateRelationshipService;
    }

    public <RESULT> RESULT filteredKnn(
        GraphName graphName,
        FilteredKnnMutateConfig configuration,
        ResultBuilder<FilteredKnnMutateConfig, FilteredKnnResult, RESULT, Pair<RelationshipsWritten, Map<String, Object>>> resultBuilder,
        boolean shouldComputeSimilarityDistribution
    ) {
        var mutateStep = FilteredKnnMutateStep.create(
            mutateRelationshipService,
            configuration,
            shouldComputeSimilarityDistribution
        );

        return algorithmProcessingTemplateConvenience.processRegularAlgorithmInMutateMode(
            graphName,
            configuration,
            FilteredKNN,
            () -> estimationFacade.filteredKnn(configuration),
            (graph, __) -> similarityAlgorithms.filteredKnn(graph, configuration),
            mutateStep,
            resultBuilder
        );
    }

    public <RESULT> RESULT filteredNodeSimilarity(
        GraphName graphName,
        FilteredNodeSimilarityMutateConfig configuration,
        ResultBuilder<FilteredNodeSimilarityMutateConfig, NodeSimilarityResult, RESULT, Pair<RelationshipsWritten, Map<String, Object>>> resultBuilder,
        boolean shouldComputeSimilarityDistribution
    ) {
        var mutateStep = FilteredNodeSimilarityMutateStep.create(
            mutateRelationshipService,
            configuration,
            shouldComputeSimilarityDistribution
        );

        return algorithmProcessingTemplateConvenience.processRegularAlgorithmInMutateMode(
            graphName,
            configuration,
            FilteredNodeSimilarity,
            () -> estimationFacade.filteredNodeSimilarity(configuration),
            (graph, __) -> similarityAlgorithms.filteredNodeSimilarity(graph, configuration),
            mutateStep,
            resultBuilder
        );
    }

    public <RESULT> RESULT knn(
        GraphName graphName,
        KnnMutateConfig configuration,
        ResultBuilder<KnnMutateConfig, KnnResult, RESULT, Pair<RelationshipsWritten, Map<String, Object>>> resultBuilder,
        boolean shouldComputeSimilarityDistribution
    ) {
        var mutateStep = KnnMutateStep.create(mutateRelationshipService,
            configuration,
            shouldComputeSimilarityDistribution
        );

        return algorithmProcessingTemplateConvenience.processRegularAlgorithmInMutateMode(
            graphName,
            configuration,
            KNN,
            () -> estimationFacade.knn(configuration),
            (graph, __) -> similarityAlgorithms.knn(graph, configuration),
            mutateStep,
            resultBuilder
        );
    }

    public <RESULT> RESULT nodeSimilarity(
        GraphName graphName,
        NodeSimilarityMutateConfig configuration,
        ResultBuilder<NodeSimilarityMutateConfig, NodeSimilarityResult, RESULT, Pair<RelationshipsWritten, Map<String, Object>>> resultBuilder,
        boolean shouldComputeSimilarityDistribution
    ) {
        var mutateStep = NodeSimilarityMutateStep.create(mutateRelationshipService,
            configuration,
            shouldComputeSimilarityDistribution
        );

        return algorithmProcessingTemplateConvenience.processRegularAlgorithmInMutateMode(
            graphName,
            configuration,
            NodeSimilarity,
            () -> estimationFacade.nodeSimilarity(configuration),
            (graph, __) -> similarityAlgorithms.nodeSimilarity(graph, configuration),
            mutateStep,
            resultBuilder
        );
    }
}
