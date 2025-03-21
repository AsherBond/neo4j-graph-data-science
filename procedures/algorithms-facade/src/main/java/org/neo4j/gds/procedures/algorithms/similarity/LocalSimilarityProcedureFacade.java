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
package org.neo4j.gds.procedures.algorithms.similarity;

import org.neo4j.gds.api.GraphName;
import org.neo4j.gds.api.ProcedureReturnColumns;
import org.neo4j.gds.applications.ApplicationsFacade;
import org.neo4j.gds.applications.algorithms.machinery.MemoryEstimateResult;
import org.neo4j.gds.applications.algorithms.similarity.SimilarityAlgorithmsEstimationModeBusinessFacade;
import org.neo4j.gds.applications.algorithms.similarity.SimilarityAlgorithmsStatsModeBusinessFacade;
import org.neo4j.gds.applications.algorithms.similarity.SimilarityAlgorithmsStreamModeBusinessFacade;
import org.neo4j.gds.applications.algorithms.similarity.SimilarityAlgorithmsWriteModeBusinessFacade;
import org.neo4j.gds.procedures.algorithms.configuration.UserSpecificConfigurationParser;
import org.neo4j.gds.procedures.algorithms.similarity.stubs.LocalFilteredKnnMutateStub;
import org.neo4j.gds.procedures.algorithms.similarity.stubs.LocalFilteredNodeSimilarityMutateStub;
import org.neo4j.gds.procedures.algorithms.similarity.stubs.LocalKnnMutateStub;
import org.neo4j.gds.procedures.algorithms.similarity.stubs.LocalNodeSimilarityMutateStub;
import org.neo4j.gds.procedures.algorithms.similarity.stubs.SimilarityStubs;
import org.neo4j.gds.procedures.algorithms.stubs.GenericStub;
import org.neo4j.gds.similarity.filteredknn.FilteredKnnStatsConfig;
import org.neo4j.gds.similarity.filteredknn.FilteredKnnStreamConfig;
import org.neo4j.gds.similarity.filteredknn.FilteredKnnWriteConfig;
import org.neo4j.gds.similarity.filterednodesim.FilteredNodeSimilarityStatsConfig;
import org.neo4j.gds.similarity.filterednodesim.FilteredNodeSimilarityStreamConfig;
import org.neo4j.gds.similarity.filterednodesim.FilteredNodeSimilarityWriteConfig;
import org.neo4j.gds.similarity.knn.KnnStatsConfig;
import org.neo4j.gds.similarity.knn.KnnStreamConfig;
import org.neo4j.gds.similarity.knn.KnnWriteConfig;
import org.neo4j.gds.similarity.nodesim.NodeSimilarityStatsConfig;
import org.neo4j.gds.similarity.nodesim.NodeSimilarityStreamConfig;
import org.neo4j.gds.similarity.nodesim.NodeSimilarityWriteConfig;

import java.util.Map;
import java.util.stream.Stream;

public final class LocalSimilarityProcedureFacade implements SimilarityProcedureFacade {
    private final ProcedureReturnColumns procedureReturnColumns;

    private final SimilarityAlgorithmsEstimationModeBusinessFacade estimationModeBusinessFacade;
    private final SimilarityAlgorithmsStatsModeBusinessFacade statsModeBusinessFacade;
    private final SimilarityAlgorithmsStreamModeBusinessFacade streamModeBusinessFacade;
    private final SimilarityAlgorithmsWriteModeBusinessFacade writeModeBusinessFacade;

    //stubs
    private final SimilarityStubs stubs;

    private final UserSpecificConfigurationParser configurationParser;

    private LocalSimilarityProcedureFacade(
        ProcedureReturnColumns procedureReturnColumns,
        SimilarityAlgorithmsEstimationModeBusinessFacade estimationModeBusinessFacade,
        SimilarityAlgorithmsStatsModeBusinessFacade statsModeBusinessFacade,
        SimilarityAlgorithmsStreamModeBusinessFacade streamModeBusinessFacade,
        SimilarityAlgorithmsWriteModeBusinessFacade writeModeBusinessFacade,
        SimilarityStubs stubs,
        UserSpecificConfigurationParser configurationParser
    ) {
        this.procedureReturnColumns = procedureReturnColumns;
        this.estimationModeBusinessFacade = estimationModeBusinessFacade;
        this.statsModeBusinessFacade = statsModeBusinessFacade;
        this.streamModeBusinessFacade = streamModeBusinessFacade;
        this.writeModeBusinessFacade = writeModeBusinessFacade;
        this.stubs = stubs;
        this.configurationParser = configurationParser;
    }

    public static SimilarityProcedureFacade create(
        ApplicationsFacade applicationsFacade,
        GenericStub genericStub,
        ProcedureReturnColumns procedureReturnColumns,
        UserSpecificConfigurationParser configurationParser
    ) {
        var filteredKnnMutateStub = new LocalFilteredKnnMutateStub(
            genericStub,
            applicationsFacade.similarity().estimate(),
            applicationsFacade.similarity().mutate(),
            procedureReturnColumns
        );

        var filteredNodeSimilarityMutateStub = new LocalFilteredNodeSimilarityMutateStub(
            genericStub,
            applicationsFacade.similarity().estimate(),
            applicationsFacade.similarity().mutate(),
            procedureReturnColumns
        );

        var knnMutateStub = new LocalKnnMutateStub(
            genericStub,
            applicationsFacade.similarity().estimate(),
            applicationsFacade.similarity().mutate(),
            procedureReturnColumns
        );

        var nodeSimilarityMutateStub = new LocalNodeSimilarityMutateStub(
            genericStub,
            applicationsFacade.similarity().estimate(),
            applicationsFacade.similarity().mutate(),
            procedureReturnColumns
        );

        var stubs = new SimilarityStubs(
            filteredKnnMutateStub,
            filteredNodeSimilarityMutateStub,
            knnMutateStub,
            nodeSimilarityMutateStub
        );

        return new LocalSimilarityProcedureFacade(
            procedureReturnColumns,
            applicationsFacade.similarity().estimate(),
            applicationsFacade.similarity().stats(),
            applicationsFacade.similarity().stream(),
            applicationsFacade.similarity().write(),
            stubs,
            configurationParser
        );
    }


    @Override
    public SimilarityStubs similarityStubs() {
        return stubs;
    }

    @Override
    public Stream<KnnStatsResult> filteredKnnStats(String graphName, Map<String, Object> rawConfiguration) {
        var shouldComputeSimilarityDistribution = procedureReturnColumns.contains("similarityDistribution");

        var configuration = configurationParser.parseConfiguration(
            rawConfiguration,
            FilteredKnnStatsConfig::of
        );
        var resultBuilder = new FilteredKnnResultBuilderForStatsMode(
            configuration,
            shouldComputeSimilarityDistribution
        );

        return statsModeBusinessFacade.filteredKnn(
            GraphName.parse(graphName),
            configuration,
            resultBuilder
        );
    }

    @Override
    public Stream<MemoryEstimateResult> filteredKnnStatsEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        var result = estimationModeBusinessFacade.filteredKnn(
            configurationParser.parseConfiguration(algorithmConfiguration, FilteredKnnStatsConfig::of),
            graphNameOrConfiguration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<SimilarityStreamResult> filteredKnnStream(String graphName, Map<String, Object> configuration) {
        var resultBuilder = new FilteredKnnResultBuilderForStreamMode();

        return streamModeBusinessFacade.filteredKnn(
            GraphName.parse(graphName),
            configurationParser.parseConfiguration(configuration, FilteredKnnStreamConfig::of),
            resultBuilder
        );

    }

    @Override
    public Stream<MemoryEstimateResult> filteredKnnStreamEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        var result = estimationModeBusinessFacade.filteredKnn(
            configurationParser.parseConfiguration(algorithmConfiguration, FilteredKnnStreamConfig::of),
            graphNameOrConfiguration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<KnnMutateResult> filteredKnnMutate(String graphName, Map<String, Object> configuration) {
        return stubs.filteredKnn().execute(graphName,configuration);
    }

    @Override
    public Stream<MemoryEstimateResult> filteredKnnMutateEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return stubs.filteredKnn().estimate(graphNameOrConfiguration,algorithmConfiguration);
    }

    @Override
    public Stream<KnnWriteResult> filteredKnnWrite(String graphNameAsString, Map<String, Object> rawConfiguration) {
        var shouldComputeSimilarityDistribution = procedureReturnColumns.contains("similarityDistribution");
        var resultBuilder = new FilteredKnnResultBuilderForWriteMode();

        return writeModeBusinessFacade.filteredKnn(
            GraphName.parse(graphNameAsString),
            configurationParser.parseConfiguration(rawConfiguration, FilteredKnnWriteConfig::of),
            resultBuilder,
            shouldComputeSimilarityDistribution
        );
    }

    @Override
    public Stream<MemoryEstimateResult> filteredKnnWriteEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        var result = estimationModeBusinessFacade.filteredKnn(
            configurationParser.parseConfiguration(algorithmConfiguration, FilteredKnnWriteConfig::of),
            graphNameOrConfiguration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<SimilarityStatsResult> filteredNodeSimilarityStats(
        String graphName,
        Map<String, Object> rawConfiguration
    ) {
        var shouldComputeSimilarityDistribution = procedureReturnColumns.contains("similarityDistribution");

        var configuration = configurationParser.parseConfiguration(
            rawConfiguration,
            FilteredNodeSimilarityStatsConfig::of
        );
        var resultBuilder = new FilteredNodeSimilarityResultBuilderForStatsMode(
            configuration,
            shouldComputeSimilarityDistribution
        );

        return statsModeBusinessFacade.filteredNodeSimilarity(
            GraphName.parse(graphName),
            configuration,
            resultBuilder
        );
    }

    @Override
    public Stream<MemoryEstimateResult> filteredNodeSimilarityStatsEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        var result = estimationModeBusinessFacade.filteredNodeSimilarity(
            configurationParser.parseConfiguration(algorithmConfiguration, FilteredNodeSimilarityStatsConfig::of),
            graphNameOrConfiguration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<SimilarityStreamResult> filteredNodeSimilarityStream(
        String graphName,
        Map<String, Object> configuration
    ) {
        var resultBuilder = new FilteredNodeSimilarityResultBuilderForStreamMode();

        return streamModeBusinessFacade.filteredNodeSimilarity(
            GraphName.parse(graphName),
            configurationParser.parseConfiguration(configuration, FilteredNodeSimilarityStreamConfig::of),
            resultBuilder
        );
    }

    @Override
    public Stream<MemoryEstimateResult> filteredNodeSimilarityStreamEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        var result = estimationModeBusinessFacade.filteredNodeSimilarity(
            configurationParser.parseConfiguration(algorithmConfiguration, FilteredNodeSimilarityStreamConfig::of),
            graphNameOrConfiguration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<SimilarityMutateResult> filteredNodeSimilarityMutate(
        String graphName,
        Map<String, Object> configuration
    ) {
        return  stubs.filteredNodeSimilarity().execute(graphName,configuration);
    }

    @Override
    public Stream<MemoryEstimateResult> filteredNodeSimilarityMutateEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return stubs.filteredNodeSimilarity().estimate(graphNameOrConfiguration,algorithmConfiguration);
    }

    @Override
    public Stream<SimilarityWriteResult> filteredNodeSimilarityWrite(
        String graphNameAsString,
        Map<String, Object> rawConfiguration
    ) {
        var resultBuilder = new FilteredNodeSimilarityResultBuilderForWriteMode();

        var shouldComputeSimilarityDistribution = procedureReturnColumns.contains("similarityDistribution");

        return writeModeBusinessFacade.filteredNodeSimilarity(
            GraphName.parse(graphNameAsString),
            configurationParser.parseConfiguration(rawConfiguration, FilteredNodeSimilarityWriteConfig::of),
            resultBuilder,
            shouldComputeSimilarityDistribution
        );
    }

    @Override
    public Stream<MemoryEstimateResult> filteredNodeSimilarityWriteEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        var result = estimationModeBusinessFacade.filteredNodeSimilarity(
            configurationParser.parseConfiguration(algorithmConfiguration, FilteredNodeSimilarityWriteConfig::of),
            graphNameOrConfiguration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<KnnStatsResult> knnStats(
        String graphName,
        Map<String, Object> rawConfiguration
    ) {
        var shouldComputeSimilarityDistribution = procedureReturnColumns.contains("similarityDistribution");
        var configuration = configurationParser.parseConfiguration(rawConfiguration, KnnStatsConfig::of);
        var resultBuilder = new KnnResultBuilderForStatsMode(configuration, shouldComputeSimilarityDistribution);

        return statsModeBusinessFacade.knn(
            GraphName.parse(graphName),
            configuration,
            resultBuilder
        );
    }

    @Override
    public Stream<MemoryEstimateResult> knnStatsEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        var result = estimationModeBusinessFacade.knn(
            configurationParser.parseConfiguration(algorithmConfiguration, KnnStatsConfig::of),
            graphNameOrConfiguration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<SimilarityStreamResult> knnStream(
        String graphName,
        Map<String, Object> configuration
    ) {
        var resultBuilder = new KnnResultBuilderForStreamMode();

        return streamModeBusinessFacade.knn(
            GraphName.parse(graphName),
            configurationParser.parseConfiguration(configuration, KnnStreamConfig::of),
            resultBuilder
        );
    }

    @Override
    public Stream<MemoryEstimateResult> knnStreamEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        var result = estimationModeBusinessFacade.knn(
            configurationParser.parseConfiguration(algorithmConfiguration, KnnStreamConfig::of),
            graphNameOrConfiguration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<KnnMutateResult> knnMutate(String graphNameAsString, Map<String, Object> rawConfiguration) {
        return stubs.knn().execute(graphNameAsString,rawConfiguration);
    }

    @Override
    public Stream<MemoryEstimateResult> knnMutateEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return stubs.knn().estimate(graphNameOrConfiguration,algorithmConfiguration);
    }

    @Override
    public Stream<KnnWriteResult> knnWrite(
        String graphNameAsString,
        Map<String, Object> rawConfiguration
    ) {
        var resultBuilder = new KnnResultBuilderForWriteMode();

        var shouldComputeSimilarityDistribution = procedureReturnColumns.contains("similarityDistribution");

        return writeModeBusinessFacade.knn(
            GraphName.parse(graphNameAsString),
            configurationParser.parseConfiguration(rawConfiguration, KnnWriteConfig::of),
            resultBuilder,
            shouldComputeSimilarityDistribution
        );
    }

    @Override
    public Stream<MemoryEstimateResult> knnWriteEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        var result = estimationModeBusinessFacade.knn(
            configurationParser.parseConfiguration(algorithmConfiguration, KnnWriteConfig::of),
            graphNameOrConfiguration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<SimilarityMutateResult> nodeSimilarityMutate(String graphName, Map<String, Object> configuration) {
        return stubs.nodeSimilarity().execute(graphName,configuration);
    }

    @Override
    public Stream<MemoryEstimateResult> nodeSimilarityMutateEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return stubs.nodeSimilarity().estimate(graphNameOrConfiguration,algorithmConfiguration);
    }


    @Override
    public Stream<SimilarityStatsResult> nodeSimilarityStats(String graphName, Map<String, Object> rawConfiguration) {
        var shouldComputeSimilarityDistribution = procedureReturnColumns.contains("similarityDistribution");

        var configuration = configurationParser.parseConfiguration(
            rawConfiguration,
            NodeSimilarityStatsConfig::of
        );
        var resultBuilder = new NodeSimilarityResultBuilderForStatsMode(
            configuration,
            shouldComputeSimilarityDistribution
        );

        return statsModeBusinessFacade.nodeSimilarity(
            GraphName.parse(graphName),
            configuration,
            resultBuilder
        );
    }

    @Override
    public Stream<MemoryEstimateResult> nodeSimilarityStatsEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        var result = estimationModeBusinessFacade.nodeSimilarity(
            configurationParser.parseConfiguration(algorithmConfiguration, NodeSimilarityStatsConfig::of),
            graphNameOrConfiguration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<SimilarityStreamResult> nodeSimilarityStream(String graphName, Map<String, Object> configuration) {
        var resultBuilder = new NodeSimilarityResultBuilderForStreamMode();

        return streamModeBusinessFacade.nodeSimilarity(
            GraphName.parse(graphName),
            configurationParser.parseConfiguration(configuration, NodeSimilarityStreamConfig::of),
            resultBuilder
        );
    }

    @Override
    public Stream<MemoryEstimateResult> nodeSimilarityStreamEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        var result = estimationModeBusinessFacade.nodeSimilarity(
            configurationParser.parseConfiguration(algorithmConfiguration, NodeSimilarityStreamConfig::of),
            graphNameOrConfiguration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<SimilarityWriteResult> nodeSimilarityWrite(
        String graphNameAsString,
        Map<String, Object> rawConfiguration
    ) {
        var resultBuilder = new NodeSimilarityResultBuilderForWriteMode();
        var shouldComputeSimilarityDistribution = procedureReturnColumns.contains("similarityDistribution");

        var parsedConfiguration = configurationParser.parseConfiguration(
            rawConfiguration,
            NodeSimilarityWriteConfig::of
        );

        return writeModeBusinessFacade.nodeSimilarity(
            GraphName.parse(graphNameAsString),
            parsedConfiguration,
            resultBuilder,
            shouldComputeSimilarityDistribution
        );
    }

    @Override
    public Stream<MemoryEstimateResult> nodeSimilarityWriteEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {

        var parsedConfiguration = configurationParser.parseConfiguration(
            algorithmConfiguration,
            NodeSimilarityWriteConfig::of
        );

        var memoryEstimateResult = estimationModeBusinessFacade.nodeSimilarity(
            parsedConfiguration,
            graphNameOrConfiguration
        );

        return Stream.of(memoryEstimateResult);
    }
}
