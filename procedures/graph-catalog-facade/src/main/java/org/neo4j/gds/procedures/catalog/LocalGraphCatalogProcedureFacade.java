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
package org.neo4j.gds.procedures.catalog;

import org.neo4j.gds.api.ProcedureReturnColumns;
import org.neo4j.gds.applications.algorithms.machinery.MemoryEstimateResult;
import org.neo4j.gds.applications.algorithms.machinery.RequestScopedDependencies;
import org.neo4j.gds.applications.algorithms.machinery.WriteContext;
import org.neo4j.gds.applications.graphstorecatalog.DatabaseExportResult;
import org.neo4j.gds.applications.graphstorecatalog.FileExportResult;
import org.neo4j.gds.applications.graphstorecatalog.GraphCatalogApplications;
import org.neo4j.gds.applications.graphstorecatalog.GraphGenerationStats;
import org.neo4j.gds.applications.graphstorecatalog.GraphMemoryUsage;
import org.neo4j.gds.applications.graphstorecatalog.GraphProjectMemoryUsageService;
import org.neo4j.gds.applications.graphstorecatalog.GraphStreamNodePropertiesResult;
import org.neo4j.gds.applications.graphstorecatalog.GraphStreamNodePropertyOrPropertiesResultProducer;
import org.neo4j.gds.applications.graphstorecatalog.GraphStreamNodePropertyResult;
import org.neo4j.gds.applications.graphstorecatalog.GraphStreamRelationshipPropertiesResult;
import org.neo4j.gds.applications.graphstorecatalog.GraphStreamRelationshipPropertyOrPropertiesResultProducer;
import org.neo4j.gds.applications.graphstorecatalog.GraphStreamRelationshipPropertyResult;
import org.neo4j.gds.applications.graphstorecatalog.MutateLabelResult;
import org.neo4j.gds.applications.graphstorecatalog.NodePropertiesWriteResult;
import org.neo4j.gds.applications.graphstorecatalog.RandomWalkSamplingResult;
import org.neo4j.gds.applications.graphstorecatalog.TopologyResult;
import org.neo4j.gds.applications.graphstorecatalog.WriteLabelResult;
import org.neo4j.gds.applications.graphstorecatalog.WriteRelationshipPropertiesResult;
import org.neo4j.gds.applications.graphstorecatalog.WriteRelationshipResult;
import org.neo4j.gds.beta.filter.GraphFilterResult;
import org.neo4j.gds.core.loading.GraphDropNodePropertiesResult;
import org.neo4j.gds.core.loading.GraphDropRelationshipResult;
import org.neo4j.gds.legacycypherprojection.GraphProjectCypherResult;
import org.neo4j.gds.projection.GraphProjectNativeResult;
import org.neo4j.gds.transaction.TransactionContext;
import org.neo4j.graphdb.GraphDatabaseService;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Catalog facade groups all catalog procedures in one class, for ease of dependency injection and management.
 * Behaviour captured here is Neo4j procedure specific stuff only,
 * everything else gets pushed down into domain and business logic. Conversely,
 * any actual Neo4j procedure specific behaviour should live here and not in procedure stubs.
 * This allows us to keep the procedure stub classes dumb and thin, and one day generateable.
 * <p>
 * This class gets constructed per request.
 */
public class LocalGraphCatalogProcedureFacade implements GraphCatalogProcedureFacade {

    private final RequestScopedDependencies requestScopedDependencies;
    private final ProcedureReturnColumns procedureReturnColumns;
    private final WriteContext writeContext;
    private final Consumer<AutoCloseable> streamCloser;
    private final GraphDatabaseService graphDatabaseService;
    private final GraphProjectMemoryUsageService graphProjectMemoryUsageService;
    private final TransactionContext transactionContext;
    private final DatabaseModeRestriction databaseModeRestriction;

    // business facade
    private final GraphCatalogApplications catalog;

    /**
     * @param streamCloser A special thing needed for property streaming
     */
    public LocalGraphCatalogProcedureFacade(
        RequestScopedDependencies requestScopedDependencies,
        Consumer<AutoCloseable> streamCloser,
        GraphDatabaseService graphDatabaseService,
        GraphProjectMemoryUsageService graphProjectMemoryUsageService,
        TransactionContext transactionContext,
        GraphCatalogApplications catalog,
        WriteContext writeContext,
        ProcedureReturnColumns procedureReturnColumns,
        DatabaseModeRestriction databaseModeRestriction
    ) {
        this.requestScopedDependencies = requestScopedDependencies;
        this.streamCloser = streamCloser;
        this.graphDatabaseService = graphDatabaseService;
        this.graphProjectMemoryUsageService = graphProjectMemoryUsageService;
        this.transactionContext = transactionContext;

        this.catalog = catalog;
        this.writeContext = writeContext;
        this.procedureReturnColumns = procedureReturnColumns;
        this.databaseModeRestriction = databaseModeRestriction;
    }

    /**
     * Discussion: this is used by two stubs, with different output marshalling functions.
     * <p>
     * We know we should test {@link #graphExists(String)} in isolation because combinatorials.
     * <p>
     * Do we test the output marshallers?
     * <p>
     * Well if we need confidence, not for just box ticking.
     * Neo4j Procedure Framework requires POJOs of a certain shape,
     * so there is scope for writing ridiculous amounts of code if you fancy ticking boxes.
     */
    @SuppressWarnings("WeakerAccess")
    @Override
    public <RETURN_TYPE> RETURN_TYPE graphExists(String graphName, Function<Boolean, RETURN_TYPE> outputMarshaller) {
        var graphExists = graphExists(graphName);

        return outputMarshaller.apply(graphExists);
    }

    @Override
    public boolean graphExists(String graphName) {
        return catalog.graphExists(
            requestScopedDependencies.user(),
            requestScopedDependencies.databaseId(),
            graphName
        );
    }

    /**
     * @param failIfMissing enable validation that graphs exist before dropping them
     * @param databaseName  optional override
     * @param username      optional override
     * @throws IllegalArgumentException if a database name was null or blank or not a String
     */
    @Override
    public Stream<GraphInfo> dropGraph(
        Object graphNameOrListOfGraphNames,
        boolean failIfMissing,
        String databaseName,
        String username
    ) throws IllegalArgumentException {
        var results = catalog.dropGraph(
            graphNameOrListOfGraphNames,
            failIfMissing,
            databaseName,
            username,
            requestScopedDependencies.databaseId(),
            requestScopedDependencies.user()
        );

        // we convert here from domain type to Neo4j display type
        return results.stream().map(gswc -> GraphInfo.withoutMemoryUsage(
            gswc.config(),
            gswc.graphStore()
        ));
    }

    @Override
    public Stream<GraphInfoWithHistogram> listGraphs(String graphName, Map<String, Object> configuration) {
        graphName = validateValue(graphName);

        var displayDegreeDistribution = procedureReturnColumns.contains("degreeDistribution");

        var results = catalog.listGraphs(
            requestScopedDependencies.user(),
            graphName,
            displayDegreeDistribution,
            requestScopedDependencies.terminationFlag()
        );

        // we convert here from domain type to Neo4j display type
        var computeGraphSize = procedureReturnColumns.contains("memoryUsage")
            || procedureReturnColumns.contains("sizeInBytes");
        return results.stream().map(p -> GraphInfoWithHistogram.of(
            p.getLeft().config(),
            p.getLeft().graphStore(),
            p.getRight(),
            computeGraphSize
        ));
    }

    @Override
    public Stream<GraphProjectNativeResult> nativeProject(
        String graphName,
        Object nodeProjection,
        Object relationshipProjection,
        Map<String, Object> configuration
    ) {
        var result = catalog.nativeProject(
            requestScopedDependencies.user(),
            requestScopedDependencies.databaseId(),
            graphDatabaseService,
            graphProjectMemoryUsageService,
            requestScopedDependencies.taskRegistryFactory(),
            requestScopedDependencies.terminationFlag(),
            transactionContext,
            requestScopedDependencies.userLogRegistryFactory(),
            graphName,
            nodeProjection,
            relationshipProjection,
            configuration
        );

        // the fact that it is a stream is just a Neo4j Procedure Framework convention
        return Stream.of(result);
    }

    @Override
    public Stream<MemoryEstimateResult> estimateNativeProject(
        Object nodeProjection,
        Object relationshipProjection,
        Map<String, Object> configuration
    ) {
        var result = catalog.estimateNativeProject(
            requestScopedDependencies.databaseId(),
            graphProjectMemoryUsageService,
            requestScopedDependencies.taskRegistryFactory(),
            requestScopedDependencies.terminationFlag(),
            transactionContext,
            requestScopedDependencies.userLogRegistryFactory(),
            nodeProjection,
            relationshipProjection,
            configuration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<GraphProjectCypherResult> cypherProject(
        String graphName,
        String nodeQuery,
        String relationshipQuery,
        Map<String, Object> configuration
    ) {
        var result = catalog.cypherProject(
            requestScopedDependencies.user(),
            requestScopedDependencies.databaseId(),
            graphDatabaseService,
            graphProjectMemoryUsageService,
            requestScopedDependencies.taskRegistryFactory(),
            requestScopedDependencies.terminationFlag(),
            transactionContext,
            requestScopedDependencies.userLogRegistryFactory(),
            graphName,
            nodeQuery,
            relationshipQuery,
            configuration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<MemoryEstimateResult> estimateCypherProject(
        String nodeQuery,
        String relationshipQuery,
        Map<String, Object> configuration
    ) {
        var result = catalog.estimateCypherProject(
            requestScopedDependencies.databaseId(),
            graphProjectMemoryUsageService,
            requestScopedDependencies.taskRegistryFactory(),
            requestScopedDependencies.terminationFlag(),
            transactionContext,
            requestScopedDependencies.userLogRegistryFactory(),
            nodeQuery,
            relationshipQuery,
            configuration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<GraphFilterResult> subGraphProject(
        String graphName,
        String originGraphName,
        String nodeFilter,
        String relationshipFilter,
        Map<String, Object> configuration
    ) {
        var result = catalog.subGraphProject(
            requestScopedDependencies.user(),
            requestScopedDependencies.databaseId(),
            requestScopedDependencies.taskRegistryFactory(),
            requestScopedDependencies.userLogRegistryFactory(),
            graphName,
            originGraphName,
            nodeFilter,
            relationshipFilter,
            configuration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<GraphMemoryUsage> sizeOf(String graphName) {
        var result = catalog.sizeOf(
            requestScopedDependencies.user(),
            requestScopedDependencies.databaseId(),
            graphName
        );

        return Stream.of(result);
    }

    @Override
    public Stream<GraphDropNodePropertiesResult> dropNodeProperties(
        String graphName,
        Object nodeProperties,
        Map<String, Object> configuration
    ) {
        var result = catalog.dropNodeProperties(
            requestScopedDependencies.user(),
            requestScopedDependencies.databaseId(),
            requestScopedDependencies.taskRegistryFactory(),
            requestScopedDependencies.userLogRegistryFactory(),
            graphName,
            nodeProperties,
            configuration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<GraphDropRelationshipResult> dropRelationships(
        String graphName,
        String relationshipType
    ) {
        var result = catalog.dropRelationships(
            requestScopedDependencies.user(),
            requestScopedDependencies.databaseId(),
            requestScopedDependencies.taskRegistryFactory(),
            requestScopedDependencies.userLogRegistryFactory(),
            graphName,
            relationshipType
        );

        return Stream.of(result);
    }

    @Override
    public Stream<GraphDropGraphPropertiesResult> dropGraphProperty(
        String graphName,
        String graphProperty,
        Map<String, Object> configuration
    ) {
        var numberOfPropertiesRemoved = catalog.dropGraphProperty(
            requestScopedDependencies.user(),
            requestScopedDependencies.databaseId(),
            graphName,
            graphProperty,
            configuration
        );

        var result = new GraphDropGraphPropertiesResult(
            graphName,
            graphProperty,
            numberOfPropertiesRemoved
        );

        return Stream.of(result);
    }

    @Override
    public Stream<MutateLabelResult> mutateNodeLabel(
        String graphName,
        String nodeLabel,
        Map<String, Object> configuration
    ) {
        var result = catalog.mutateNodeLabel(
            requestScopedDependencies.user(),
            requestScopedDependencies.databaseId(),
            graphName,
            nodeLabel,
            configuration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<StreamGraphPropertyResult> streamGraphProperty(
        String graphName,
        String graphProperty,
        Map<String, Object> configuration
    ) {
        var result = catalog.streamGraphProperty(
            requestScopedDependencies.user(),
            requestScopedDependencies.databaseId(),
            graphName,
            graphProperty,
            configuration
        );

        return result.map(StreamGraphPropertyResult::new);
    }

    @Override
    public Stream<GraphStreamNodePropertiesResult> streamNodeProperties(
        String graphName,
        Object nodeProperties,
        Object nodeLabels,
        Map<String, Object> configuration
    ) {
        return streamNodePropertyOrProperties(
            graphName,
            nodeProperties,
            nodeLabels,
            configuration,
            GraphStreamNodePropertiesResult::new
        );
    }

    @Override
    public Stream<GraphStreamNodePropertyResult> streamNodeProperty(
        String graphName,
        String nodeProperty,
        Object nodeLabels,
        Map<String, Object> configuration
    ) {
        return streamNodePropertyOrProperties(
            graphName,
            List.of(nodeProperty),
            nodeLabels,
            configuration,
            (nodeId, propertyName, propertyValue, nodeLabelList) -> new GraphStreamNodePropertyResult(
                nodeId,
                propertyValue,
                nodeLabelList
            )
        );
    }

    @Override
    public Stream<GraphStreamRelationshipPropertiesResult> streamRelationshipProperties(
        String graphName,
        List<String> relationshipProperties,
        Object relationshipTypes,
        Map<String, Object> configuration
    ) {
        return streamRelationshipPropertyOrProperties(
            graphName,
            relationshipProperties,
            relationshipTypes,
            configuration,
            GraphStreamRelationshipPropertiesResult::new
        );
    }

    @Override
    public Stream<GraphStreamRelationshipPropertyResult> streamRelationshipProperty(
        String graphName,
        String relationshipProperty,
        Object relationshipTypes,
        Map<String, Object> configuration
    ) {
        return streamRelationshipPropertyOrProperties(
            graphName,
            List.of(relationshipProperty),
            relationshipTypes,
            configuration,
            (sourceId, targetId, relationshipType, propertyName, propertyValue) -> new GraphStreamRelationshipPropertyResult(
                sourceId,
                targetId,
                relationshipType,
                propertyValue
            )
        );
    }

    @Override
    public Stream<TopologyResult> streamRelationships(
        String graphName,
        Object relationshipTypes,
        Map<String, Object> configuration
    ) {
        return catalog.streamRelationships(
            requestScopedDependencies.user(),
            requestScopedDependencies.databaseId(),
            graphName,
            relationshipTypes,
            configuration
        );
    }

    @Override
    public Stream<NodePropertiesWriteResult> writeNodeProperties(
        String graphName,
        Object nodeProperties,
        Object nodeLabels,
        Map<String, Object> configuration
    ) {
        var result = catalog.writeNodeProperties(
            requestScopedDependencies.user(),
            requestScopedDependencies.databaseId(),
            writeContext.nodePropertyExporterBuilder(),
            requestScopedDependencies.taskRegistryFactory(),
            requestScopedDependencies.terminationFlag(),
            requestScopedDependencies.userLogRegistryFactory(),
            graphName,
            nodeProperties,
            nodeLabels,
            configuration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<WriteRelationshipPropertiesResult> writeRelationshipProperties(
        String graphName,
        String relationshipType,
        List<String> relationshipProperties,
        Map<String, Object> configuration
    ) {
        var result = catalog.writeRelationshipProperties(
            requestScopedDependencies.user(),
            requestScopedDependencies.databaseId(),
            writeContext.relationshipPropertiesExporterBuilder(),
            requestScopedDependencies.terminationFlag(),
            graphName,
            relationshipType,
            relationshipProperties,
            configuration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<WriteLabelResult> writeNodeLabel(
        String graphName,
        String nodeLabel,
        Map<String, Object> configuration
    ) {
        var result = catalog.writeNodeLabel(
            requestScopedDependencies.user(),
            requestScopedDependencies.databaseId(),
            writeContext.nodeLabelExporterBuilder(),
            requestScopedDependencies.terminationFlag(),
            graphName,
            nodeLabel,
            configuration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<WriteRelationshipResult> writeRelationships(
        String graphName,
        String relationshipType,
        String relationshipProperty,
        Map<String, Object> configuration
    ) {
        var result = catalog.writeRelationships(
            requestScopedDependencies.user(),
            requestScopedDependencies.databaseId(),
            writeContext.relationshipExporterBuilder(),
            requestScopedDependencies.taskRegistryFactory(),
            requestScopedDependencies.terminationFlag(),
            requestScopedDependencies.userLogRegistryFactory(),
            graphName,
            relationshipType,
            relationshipProperty,
            configuration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<RandomWalkSamplingResult> sampleRandomWalkWithRestarts(
        String graphName,
        String originGraphName,
        Map<String, Object> configuration
    ) {
        var result = catalog.sampleRandomWalkWithRestarts(
            requestScopedDependencies.user(),
            requestScopedDependencies.databaseId(),
            requestScopedDependencies.taskRegistryFactory(),
            requestScopedDependencies.userLogRegistryFactory(),
            requestScopedDependencies.terminationFlag(),
            graphName,
            originGraphName,
            configuration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<RandomWalkSamplingResult> sampleCommonNeighbourAwareRandomWalk(
        String graphName,
        String originGraphName,
        Map<String, Object> configuration
    ) {
        var result = catalog.sampleCommonNeighbourAwareRandomWalk(
            requestScopedDependencies.user(),
            requestScopedDependencies.databaseId(),
            requestScopedDependencies.taskRegistryFactory(),
            requestScopedDependencies.userLogRegistryFactory(),
            requestScopedDependencies.terminationFlag(),
            graphName,
            originGraphName,
            configuration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<MemoryEstimateResult> estimateCommonNeighbourAwareRandomWalk(
        String graphName,
        Map<String, Object> configuration
    ) {
        var result = catalog.estimateCommonNeighbourAwareRandomWalk(
            requestScopedDependencies.user(),
            requestScopedDependencies.databaseId(),
            graphName,
            configuration
        );

        return Stream.of(result);
    }

    @Override
    public Stream<FileExportResult> exportToCsv(
        String graphName,
        Map<String, Object> configuration
    ) {
        var result = catalog.exportToCsv(graphName, configuration);

        return Stream.of(result);
    }

    @Override
    public Stream<MemoryEstimateResult> exportToCsvEstimate(String graphName, Map<String, Object> configuration) {
        var result = catalog.exportToCsvEstimate(graphName, configuration);

        return Stream.of(result);
    }

    @Override
    public Stream<DatabaseExportResult> exportToDatabase(
        String graphName,
        Map<String, Object> configuration
    ) {
        databaseModeRestriction.ensureNotOnCluster();

        var result = catalog.exportToDatabase(graphName, configuration);

        return Stream.of(result);
    }

    @Override
    public Stream<GraphGenerationStats> generateGraph(
        String graphName,
        long nodeCount,
        long averageDegree,
        Map<String, Object> configuration
    ) {
        var result = catalog.generateGraph(
            requestScopedDependencies.user(),
            requestScopedDependencies.databaseId(),
            graphName,
            nodeCount,
            averageDegree,
            configuration
        );

        return Stream.of(result);
    }

    private <T> Stream<T> streamRelationshipPropertyOrProperties(
        String graphName,
        List<String> relationshipProperties,
        Object relationshipTypes,
        Map<String, Object> configuration,
        GraphStreamRelationshipPropertyOrPropertiesResultProducer<T> outputMarshaller
    ) {
        var usesPropertyNameColumn = procedureReturnColumns.contains("relationshipProperty");

        var resultStream = catalog.streamRelationshipProperties(
            requestScopedDependencies.user(),
            requestScopedDependencies.databaseId(),
            requestScopedDependencies.taskRegistryFactory(),
            requestScopedDependencies.userLogRegistryFactory(),
            graphName,
            relationshipProperties,
            relationshipTypes,
            configuration,
            usesPropertyNameColumn,
            outputMarshaller
        );

        streamCloser.accept(resultStream);

        return resultStream;
    }

    /**
     * We have to potentially unstack the placeholder. This is purely a Neo4j Procedure framework concern.
     */
    private String validateValue(String graphName) {
        if (NO_VALUE_PLACEHOLDER.equals(graphName)) return null;

        return graphName;
    }

    // good generics!
    private <T> Stream<T> streamNodePropertyOrProperties(
        String graphName,
        Object nodeProperties,
        Object nodeLabels,
        Map<String, Object> configuration,
        GraphStreamNodePropertyOrPropertiesResultProducer<T> outputMarshaller
    ) {
        var usesPropertyNameColumn = procedureReturnColumns.contains("nodeProperty");

        var resultStream = catalog.streamNodeProperties(
            requestScopedDependencies.user(),
            requestScopedDependencies.databaseId(),
            requestScopedDependencies.taskRegistryFactory(),
            requestScopedDependencies.userLogRegistryFactory(),
            graphName,
            nodeProperties,
            nodeLabels,
            configuration,
            usesPropertyNameColumn,
            outputMarshaller
        );

        streamCloser.accept(resultStream);

        return resultStream;
    }

}
