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
package org.neo4j.gds.procedures.pipelines;

import org.neo4j.gds.api.GraphName;
import org.neo4j.gds.api.User;
import org.neo4j.gds.applications.algorithms.machinery.MemoryEstimateResult;
import org.neo4j.gds.ml.pipeline.PipelineCompanion;
import org.neo4j.gds.ml.pipeline.TrainingPipeline;
import org.neo4j.gds.ml.pipeline.linkPipeline.LinkPredictionTrainingPipeline;

import java.util.Map;
import java.util.stream.Stream;

public final class LocalLinkPredictionFacade implements LinkPredictionFacade {
    private final Configurer configurer;

    private final PipelineConfigurationParser pipelineConfigurationParser;
    private final PipelineApplications pipelineApplications;

    private LocalLinkPredictionFacade(
        Configurer configurer, PipelineConfigurationParser pipelineConfigurationParser,
        PipelineApplications pipelineApplications
    ) {
        this.configurer = configurer;
        this.pipelineConfigurationParser = pipelineConfigurationParser;
        this.pipelineApplications = pipelineApplications;
    }

    static LinkPredictionFacade create(
        User user,
        PipelineConfigurationParser pipelineConfigurationParser,
        PipelineApplications pipelineApplications,
        PipelineRepository pipelineRepository
    ) {
        var configurer = new Configurer(pipelineRepository, user);

        return new LocalLinkPredictionFacade(configurer, pipelineConfigurationParser, pipelineApplications);
    }

    @Override
    public Stream<PipelineInfoResult> addFeature(
        String pipelineNameAsString,
        String featureType,
        Map<String, Object> rawConfiguration
    ) {
        var pipelineName = PipelineName.parse(pipelineNameAsString);
        var configuration = pipelineConfigurationParser.parseLinkFeatureStepConfiguration(rawConfiguration);

        var pipeline = pipelineApplications.addFeature(pipelineName, featureType, configuration);

        var result = PipelineInfoResultTransformer.create(pipelineName, pipeline);

        return Stream.of(result);
    }

    @Override
    public Stream<PipelineInfoResult> addLogisticRegression(String pipelineName, Map<String, Object> configuration) {
        return configurer.configureLinkPredictionTrainingPipeline(
            pipelineName,
            () -> pipelineConfigurationParser.parseLogisticRegressionTrainerConfigForLinkPredictionOrNodeClassification(configuration),
            TrainingPipeline::addTrainerConfig
        );
    }

    @Override
    public Stream<PipelineInfoResult> addMLP(String pipelineName, Map<String, Object> configuration) {
        return configurer.configureLinkPredictionTrainingPipeline(
            pipelineName,
            () -> pipelineConfigurationParser.parseMLPClassifierTrainConfig(configuration),
            TrainingPipeline::addTrainerConfig
        );
    }

    @Override
    public Stream<PipelineInfoResult> addNodeProperty(
        String pipelineNameAsString,
        String taskName,
        Map<String, Object> procedureConfig
    ) {
        var pipelineName = PipelineName.parse(pipelineNameAsString);

        var pipeline = pipelineApplications.addNodePropertyToLinkPredictionPipeline(
            pipelineName,
            taskName,
            procedureConfig
        );

        var result = PipelineInfoResultTransformer.create(pipelineName, pipeline);

        return Stream.of(result);
    }

    @Override
    public Stream<PipelineInfoResult> addRandomForest(String pipelineName, Map<String, Object> configuration) {
        return configurer.configureLinkPredictionTrainingPipeline(
            pipelineName,
            () -> pipelineConfigurationParser.parseRandomForestClassifierTrainerConfigForLinkPredictionOrNodeClassification(configuration),
            TrainingPipeline::addTrainerConfig
        );
    }

    @Override
    public Stream<PipelineInfoResult> configureAutoTuning(String pipelineName, Map<String, Object> configuration) {
        return configurer.configureLinkPredictionTrainingPipeline(
            pipelineName,
            () -> pipelineConfigurationParser.parseAutoTuningConfig(configuration),
            TrainingPipeline::setAutoTuningConfig
        );
    }

    @Override
    public Stream<PipelineInfoResult> configureSplit(String pipelineName, Map<String, Object> configuration) {
        return configurer.configureLinkPredictionTrainingPipeline(
            pipelineName,
            () -> pipelineConfigurationParser.parseLinkPredictionSplitConfig(configuration),
            LinkPredictionTrainingPipeline::setSplitConfig
        );
    }

    @Override
    public Stream<PipelineInfoResult> createPipeline(String pipelineNameAsString) {
        var pipelineName = PipelineName.parse(pipelineNameAsString);

        var pipeline = pipelineApplications.createLinkPredictionTrainingPipeline(pipelineName);

        var result = PipelineInfoResultTransformer.create(pipelineName, pipeline);

        return Stream.of(result);
    }

    @Override
    public Stream<MutateResult> mutate(
        String graphNameAsString,
        Map<String, Object> configuration
    ) {
        PipelineCompanion.preparePipelineConfig(graphNameAsString, configuration);

        var graphName = GraphName.parse(graphNameAsString);

        var result = pipelineApplications.linkPredictionMutate(
            graphName,
            configuration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<MemoryEstimateResult> mutateEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> rawConfiguration
    ) {
        PipelineCompanion.preparePipelineConfig(graphNameOrConfiguration, rawConfiguration);

        var configuration = pipelineConfigurationParser.parseLinkPredictionPredictPipelineMutateConfig(rawConfiguration);

        var result = pipelineApplications.linkPredictionEstimate(
            graphNameOrConfiguration,
            configuration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<StreamResult> stream(
        String graphNameAsString,
        Map<String, Object> configuration
    ) {
        PipelineCompanion.preparePipelineConfig(graphNameAsString, configuration);

        var graphName = GraphName.parse(graphNameAsString);

        return pipelineApplications.linkPredictionStream(graphName, configuration);
    }

    @Override
    public Stream<MemoryEstimateResult> streamEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> rawConfiguration
    ) {
        PipelineCompanion.preparePipelineConfig(graphNameOrConfiguration, rawConfiguration);

        var configuration = pipelineConfigurationParser.parseLinkPredictionPredictPipelineStreamConfig(rawConfiguration);

        var result = pipelineApplications.linkPredictionEstimate(
            graphNameOrConfiguration,
            configuration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<LinkPredictionTrainResult> train(
        String graphNameAsString,
        Map<String, Object> configuration
    ) {
        PipelineCompanion.preparePipelineConfig(graphNameAsString, configuration);

        var graphName = GraphName.parse(graphNameAsString);

        return pipelineApplications.linkPredictionTrain(graphName, configuration);
    }

    @Override
    public Stream<MemoryEstimateResult> trainEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> rawConfiguration
    ) {
        PipelineCompanion.preparePipelineConfig(graphNameOrConfiguration, rawConfiguration);

        var configuration = pipelineConfigurationParser.parseLinkPredictionTrainConfig(rawConfiguration);

        var result = pipelineApplications.linkPredictionTrainEstimate(
            graphNameOrConfiguration,
            configuration
        );

        return Stream.of(result);
    }
}
