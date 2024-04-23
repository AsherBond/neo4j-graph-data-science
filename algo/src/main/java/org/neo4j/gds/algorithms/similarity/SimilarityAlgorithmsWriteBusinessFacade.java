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

import org.neo4j.gds.algorithms.AlgorithmComputationResult;
import org.neo4j.gds.algorithms.RelationshipWriteResult;
import org.neo4j.gds.algorithms.runner.AlgorithmRunner;
import org.neo4j.gds.algorithms.similarity.specificfields.SimilaritySpecificFields;
import org.neo4j.gds.algorithms.similarity.specificfields.SimilaritySpecificFieldsWithDistribution;
import org.neo4j.gds.config.AlgoBaseConfig;
import org.neo4j.gds.config.ArrowConnectionInfo;
import org.neo4j.gds.config.WriteRelationshipConfig;
import org.neo4j.gds.core.utils.ProgressTimer;
import org.neo4j.gds.similarity.SimilarityGraphResult;
import org.neo4j.gds.similarity.filterednodesim.FilteredNodeSimilarityWriteConfig;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.neo4j.gds.algorithms.similarity.SimilarityResultCompanion.NODE_SIMILARITY_SPECIFIC_FIELDS_SUPPLIER;

public class SimilarityAlgorithmsWriteBusinessFacade {

    private final SimilarityAlgorithmsFacade similarityAlgorithmsFacade;
    private final WriteRelationshipService writeRelationshipService;

    public SimilarityAlgorithmsWriteBusinessFacade(
        SimilarityAlgorithmsFacade similarityAlgorithmsFacade,
        WriteRelationshipService writeRelationshipService
    ) {
        this.similarityAlgorithmsFacade = similarityAlgorithmsFacade;
        this.writeRelationshipService = writeRelationshipService;
    }

    public RelationshipWriteResult<SimilaritySpecificFieldsWithDistribution> filteredNodeSimilarity(
        String graphName,
        FilteredNodeSimilarityWriteConfig configuration,
        boolean computeSimilarityDistribution
    ) {
        // 1. Run the algorithm and time the execution
        var intermediateResult = AlgorithmRunner.runWithTiming(
            () -> similarityAlgorithmsFacade.filteredNodeSimilarity(graphName, configuration)
        );
        var algorithmResult = intermediateResult.algorithmResult;

        return write(
            algorithmResult,
            configuration,
            result -> result.graphResult(),
            NODE_SIMILARITY_SPECIFIC_FIELDS_SUPPLIER,
            intermediateResult.computeMilliseconds,
            () -> SimilaritySpecificFieldsWithDistribution.EMPTY,
            computeSimilarityDistribution,
            "FilteredNodeSimilarityWrite",
            configuration.writeProperty(),
            configuration.writeRelationshipType(),
            configuration.arrowConnectionInfo()
        );
    }

    <RESULT, ASF extends SimilaritySpecificFields, CONFIG extends AlgoBaseConfig & WriteRelationshipConfig> RelationshipWriteResult<ASF> write(
        AlgorithmComputationResult<RESULT> algorithmResult,
        CONFIG configuration,
        Function<RESULT, SimilarityGraphResult> similarityGraphResultSupplier,
        SpecificFieldsWithSimilarityStatisticsSupplier<RESULT, ASF> specificFieldsSupplier,
        long computeMilliseconds,
        Supplier<ASF> emptyASFSupplier,
        boolean shouldComputeSimilarityDistribution,
        String taskName,
        String writeProperty,
        String writeRelationshipType,
        Optional<ArrowConnectionInfo> arrowConnectionInfo
    ) {

        return algorithmResult.result().map(result -> {

            SimilarityGraphResult similarityGraphResult;

            var similarityGraphCreationMillis = new AtomicLong();
            try (ProgressTimer ignored = ProgressTimer.start(similarityGraphCreationMillis::set)) {
                similarityGraphResult = similarityGraphResultSupplier.apply(result);
            }

            var similarityGraph = similarityGraphResult.similarityGraph();

            var graphStore = algorithmResult.graphStore();
            var rootIdMap = similarityGraphResult.isTopKGraph()
                ? similarityGraph
                : graphStore.nodes();


            var similarityDistributionBuilder = SimilaritySummaryBuilder.of(shouldComputeSimilarityDistribution);
            var resultStore = configuration.resolveResultStore(graphStore.resultStore());
            var writeResult = writeRelationshipService.write(
                writeRelationshipType,
                writeProperty,
                similarityGraph,
                graphStore,
                rootIdMap,
                taskName,
                configuration.typedWriteConcurrency(),
                arrowConnectionInfo,
                resultStore,
                similarityDistributionBuilder.similarityConsumer()
            );

            var similaritySummary = similarityDistributionBuilder.similaritySummary();

            var specificFields = specificFieldsSupplier.specificFields(result, similaritySummary);

            return RelationshipWriteResult.<ASF>builder()
                .computeMillis(computeMilliseconds)
                .writeMillis(writeResult.writeMilliseconds() + similarityGraphCreationMillis.get())
                .relationshipsWritten(writeResult.relationshipsWritten())
                .algorithmSpecificFields(specificFields)
                .configuration(configuration)
                .build();

        }).orElseGet(() -> RelationshipWriteResult.empty(emptyASFSupplier.get(), configuration));

    }
}
