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
package org.neo4j.gds.applications;

import org.neo4j.gds.algorithms.similarity.MutateRelationshipService;
import org.neo4j.gds.applications.algorithms.centrality.CentralityApplications;
import org.neo4j.gds.applications.algorithms.community.CommunityApplications;
import org.neo4j.gds.applications.algorithms.embeddings.NodeEmbeddingApplications;
import org.neo4j.gds.applications.algorithms.machinelearning.MachineLearningApplications;
import org.neo4j.gds.applications.algorithms.machinery.AlgorithmEstimationTemplate;
import org.neo4j.gds.applications.algorithms.machinery.AlgorithmProcessingTemplate;
import org.neo4j.gds.applications.algorithms.machinery.AlgorithmProcessingTemplateConvenience;
import org.neo4j.gds.applications.algorithms.machinery.MutateNodeProperty;
import org.neo4j.gds.applications.algorithms.machinery.ProgressTrackerCreator;
import org.neo4j.gds.applications.algorithms.machinery.RequestScopedDependencies;
import org.neo4j.gds.applications.algorithms.machinery.WriteContext;
import org.neo4j.gds.applications.algorithms.miscellaneous.MiscellaneousApplications;
import org.neo4j.gds.applications.algorithms.pathfinding.PathFindingApplications;
import org.neo4j.gds.applications.algorithms.similarity.SimilarityApplications;
import org.neo4j.gds.applications.graphstorecatalog.DefaultGraphCatalogApplications;
import org.neo4j.gds.applications.graphstorecatalog.ExportLocation;
import org.neo4j.gds.applications.graphstorecatalog.GraphCatalogApplications;
import org.neo4j.gds.applications.modelcatalog.DefaultModelCatalogApplications;
import org.neo4j.gds.applications.modelcatalog.ModelCatalogApplications;
import org.neo4j.gds.applications.modelcatalog.ModelRepository;
import org.neo4j.gds.applications.operations.FeatureTogglesRepository;
import org.neo4j.gds.applications.operations.OperationsApplications;
import org.neo4j.gds.core.utils.logging.GdsLoggers;
import org.neo4j.gds.core.loading.GraphStoreCatalogService;
import org.neo4j.gds.core.model.ModelCatalog;
import org.neo4j.gds.metrics.projections.ProjectionMetricsService;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;

import java.util.Optional;
import java.util.function.Function;

/**
 * This is the top level facade for GDS applications. If you are integrating GDS,
 * this is the one thing you want to work with. See for example Neo4j Procedures.
 * <p>
 * We use the facade pattern for well known reasons,
 * and we apply a breakdown into sub-facades to keep things smaller and more manageable.
 */
public final class ApplicationsFacade {
    private final CentralityApplications centralityApplications;
    private final CommunityApplications communityApplications;
    private final GraphCatalogApplications graphCatalogApplications;
    private final MachineLearningApplications machineLearningApplications;
    private final MiscellaneousApplications miscellaneousApplications;
    private final ModelCatalogApplications modelCatalogApplications;
    private final NodeEmbeddingApplications nodeEmbeddingApplications;
    private final OperationsApplications operationsApplications;
    private final PathFindingApplications pathFindingApplications;
    private final SimilarityApplications similarityApplications;

    ApplicationsFacade(
        CentralityApplications centralityApplications,
        CommunityApplications communityApplications,
        GraphCatalogApplications graphCatalogApplications,
        MachineLearningApplications machineLearningApplications,
        MiscellaneousApplications miscellaneousApplications,
        ModelCatalogApplications modelCatalogApplications,
        NodeEmbeddingApplications nodeEmbeddingApplications,
        OperationsApplications operationsApplications,
        PathFindingApplications pathFindingApplications,
        SimilarityApplications similarityApplications
    ) {
        this.centralityApplications = centralityApplications;
        this.communityApplications = communityApplications;
        this.graphCatalogApplications = graphCatalogApplications;
        this.machineLearningApplications = machineLearningApplications;
        this.miscellaneousApplications = miscellaneousApplications;
        this.modelCatalogApplications = modelCatalogApplications;
        this.nodeEmbeddingApplications = nodeEmbeddingApplications;
        this.operationsApplications = operationsApplications;
        this.pathFindingApplications = pathFindingApplications;
        this.similarityApplications = similarityApplications;
    }

    /**
     * We can stuff all the boring structure stuff in here so nobody needs to worry about it.
     */
    public static ApplicationsFacade create(
        GdsLoggers loggers,
        ExportLocation exportLocation,
        Optional<Function<GraphCatalogApplications, GraphCatalogApplications>> graphCatalogApplicationsDecorator,
        Optional<Function<ModelCatalogApplications, ModelCatalogApplications>> modelCatalogApplicationsDecorator,
        FeatureTogglesRepository featureTogglesRepository,
        GraphStoreCatalogService graphStoreCatalogService,
        ProjectionMetricsService projectionMetricsService,
        RequestScopedDependencies requestScopedDependencies,
        WriteContext writeContext,
        ModelCatalog modelCatalog,
        ModelRepository modelRepository,
        GraphDatabaseService graphDatabaseService,
        Transaction procedureTransaction,
        ProgressTrackerCreator progressTrackerCreator,
        AlgorithmEstimationTemplate algorithmEstimationTemplate,
        AlgorithmProcessingTemplate algorithmProcessingTemplate
    ) {
        var algorithmProcessingTemplateConvenience = new AlgorithmProcessingTemplateConvenience(algorithmProcessingTemplate);

        var mutateNodeProperty = new MutateNodeProperty(loggers.log());
        var mutateRelationshipService =new MutateRelationshipService(loggers.log());

        var centralityApplications = CentralityApplications.create(
            loggers.log(),
            requestScopedDependencies,
            writeContext,
            algorithmEstimationTemplate,
            algorithmProcessingTemplateConvenience,
            progressTrackerCreator,
            mutateNodeProperty
        );

        var communityApplications = CommunityApplications.create(
            loggers.log(),
            requestScopedDependencies,
            writeContext,
            algorithmEstimationTemplate,
            algorithmProcessingTemplateConvenience,
            progressTrackerCreator,
            mutateNodeProperty
        );

        var graphCatalogApplications = createGraphCatalogApplications(
            loggers,
            exportLocation,
            graphStoreCatalogService,
            projectionMetricsService,
            requestScopedDependencies,
            graphDatabaseService,
            procedureTransaction,
            graphCatalogApplicationsDecorator
        );

        var machineLearningApplications = MachineLearningApplications.create(
            loggers.log(),
            requestScopedDependencies,
            writeContext,
            progressTrackerCreator,
            algorithmEstimationTemplate,
            algorithmProcessingTemplateConvenience,
            mutateRelationshipService
        );

        var miscellaneousApplications = MiscellaneousApplications.create(
            loggers.log(),
            requestScopedDependencies,
            writeContext,
            algorithmEstimationTemplate,
            algorithmProcessingTemplateConvenience,
            progressTrackerCreator,
            mutateNodeProperty,
            mutateRelationshipService
        );

        var modelCatalogApplications = createModelCatalogApplications(
            requestScopedDependencies,
            modelCatalog,
            modelCatalogApplicationsDecorator
        );

        var nodeEmbeddingApplications = NodeEmbeddingApplications.create(
            loggers.log(),
            requestScopedDependencies,
            writeContext,
            algorithmEstimationTemplate,
            algorithmProcessingTemplateConvenience,
            progressTrackerCreator,
            mutateNodeProperty,
            modelCatalog,
            modelRepository
        );

        var operationsApplications = OperationsApplications.create(featureTogglesRepository, requestScopedDependencies);

        var pathFindingApplications = PathFindingApplications.create(
            loggers.log(),
            requestScopedDependencies,
            writeContext,
            algorithmEstimationTemplate,
            algorithmProcessingTemplateConvenience,
            progressTrackerCreator,
            mutateNodeProperty,
            mutateRelationshipService
        );

        var similarityApplications = SimilarityApplications.create(
            loggers.log(),
            requestScopedDependencies,
            algorithmEstimationTemplate,
            algorithmProcessingTemplateConvenience,
            progressTrackerCreator,
            mutateRelationshipService,
            writeContext
        );

        return new ApplicationsFacadeBuilder()
            .with(centralityApplications)
            .with(communityApplications)
            .with(graphCatalogApplications)
            .with(machineLearningApplications)
            .with(miscellaneousApplications)
            .with(modelCatalogApplications)
            .with(nodeEmbeddingApplications)
            .with(operationsApplications)
            .with(pathFindingApplications)
            .with(similarityApplications)
            .build();
    }

    private static GraphCatalogApplications createGraphCatalogApplications(
        GdsLoggers loggers,
        ExportLocation exportLocation,
        GraphStoreCatalogService graphStoreCatalogService,
        ProjectionMetricsService projectionMetricsService,
        RequestScopedDependencies requestScopedDependencies,
        GraphDatabaseService graphDatabaseService,
        Transaction procedureTransaction,
        Optional<Function<GraphCatalogApplications, GraphCatalogApplications>> graphCatalogApplicationsDecorator
    ) {
        var graphCatalogApplications = DefaultGraphCatalogApplications.create(
            loggers,
            exportLocation,
            graphStoreCatalogService,
            projectionMetricsService,
            requestScopedDependencies,
            graphDatabaseService,
            procedureTransaction
        );

        if (graphCatalogApplicationsDecorator.isEmpty()) return graphCatalogApplications;

        return graphCatalogApplicationsDecorator.get().apply(graphCatalogApplications);
    }

    private static ModelCatalogApplications createModelCatalogApplications(
        RequestScopedDependencies requestScopedDependencies,
        ModelCatalog modelCatalog,
        Optional<Function<ModelCatalogApplications, ModelCatalogApplications>> modelCatalogApplicationsDecorator
    ) {
        var modelCatalogApplications = DefaultModelCatalogApplications.create(
            modelCatalog,
            requestScopedDependencies.user()
        );

        if (modelCatalogApplicationsDecorator.isEmpty()) return modelCatalogApplications;

        return modelCatalogApplicationsDecorator.get().apply(modelCatalogApplications);
    }

    public CentralityApplications centrality() {
        return centralityApplications;
    }

    public CommunityApplications community() {
        return communityApplications;
    }

    public GraphCatalogApplications graphCatalog() {
        return graphCatalogApplications;
    }

    public MachineLearningApplications machineLearning() {
        return machineLearningApplications;
    }

    public MiscellaneousApplications miscellaneous() {
        return miscellaneousApplications;
    }

    public ModelCatalogApplications modelCatalog() {
        return modelCatalogApplications;
    }

    public NodeEmbeddingApplications nodeEmbeddings() {
        return nodeEmbeddingApplications;
    }

    public OperationsApplications operations() {
        return operationsApplications;
    }

    public PathFindingApplications pathFinding() {
        return pathFindingApplications;
    }

    public SimilarityApplications similarity() {
        return similarityApplications;
    }
}
