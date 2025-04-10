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

import org.neo4j.gds.allshortestpaths.AllShortestPathsConfig;
import org.neo4j.gds.allshortestpaths.AllShortestPathsStreamResult;
import org.neo4j.gds.api.CloseableResourceRegistry;
import org.neo4j.gds.api.GraphName;
import org.neo4j.gds.api.NodeLookup;
import org.neo4j.gds.api.ProcedureReturnColumns;
import org.neo4j.gds.applications.ApplicationsFacade;
import org.neo4j.gds.applications.algorithms.machinery.MemoryEstimateResult;
import org.neo4j.gds.applications.algorithms.pathfinding.PathFindingAlgorithmsEstimationModeBusinessFacade;
import org.neo4j.gds.applications.algorithms.pathfinding.PathFindingAlgorithmsStatsModeBusinessFacade;
import org.neo4j.gds.applications.algorithms.pathfinding.PathFindingAlgorithmsStreamModeBusinessFacade;
import org.neo4j.gds.applications.algorithms.pathfinding.PathFindingAlgorithmsWriteModeBusinessFacade;
import org.neo4j.gds.dag.longestPath.DagLongestPathStreamConfig;
import org.neo4j.gds.dag.topologicalsort.TopologicalSortStreamConfig;
import org.neo4j.gds.kspanningtree.KSpanningTreeWriteConfig;
import org.neo4j.gds.paths.astar.config.ShortestPathAStarStreamConfig;
import org.neo4j.gds.paths.astar.config.ShortestPathAStarWriteConfig;
import org.neo4j.gds.paths.bellmanford.AllShortestPathsBellmanFordStatsConfig;
import org.neo4j.gds.paths.bellmanford.AllShortestPathsBellmanFordStreamConfig;
import org.neo4j.gds.paths.bellmanford.AllShortestPathsBellmanFordWriteConfig;
import org.neo4j.gds.paths.delta.config.AllShortestPathsDeltaStatsConfig;
import org.neo4j.gds.paths.delta.config.AllShortestPathsDeltaStreamConfig;
import org.neo4j.gds.paths.delta.config.AllShortestPathsDeltaWriteConfig;
import org.neo4j.gds.paths.dijkstra.config.AllShortestPathsDijkstraStreamConfig;
import org.neo4j.gds.paths.dijkstra.config.AllShortestPathsDijkstraWriteConfig;
import org.neo4j.gds.paths.dijkstra.config.ShortestPathDijkstraStreamConfig;
import org.neo4j.gds.paths.dijkstra.config.ShortestPathDijkstraWriteConfig;
import org.neo4j.gds.paths.traverse.BfsStatsConfig;
import org.neo4j.gds.paths.traverse.BfsStreamConfig;
import org.neo4j.gds.paths.traverse.DfsStreamConfig;
import org.neo4j.gds.paths.yens.config.ShortestPathYensStreamConfig;
import org.neo4j.gds.paths.yens.config.ShortestPathYensWriteConfig;
import org.neo4j.gds.pcst.PCSTStatsConfig;
import org.neo4j.gds.pcst.PCSTStreamConfig;
import org.neo4j.gds.pcst.PCSTWriteConfig;
import org.neo4j.gds.procedures.algorithms.configuration.UserSpecificConfigurationParser;
import org.neo4j.gds.procedures.algorithms.pathfinding.stubs.LocalBFSMutateStub;
import org.neo4j.gds.procedures.algorithms.pathfinding.stubs.LocalBellmanFordMutateStub;
import org.neo4j.gds.procedures.algorithms.pathfinding.stubs.LocalDFSMutateStub;
import org.neo4j.gds.procedures.algorithms.pathfinding.stubs.LocalDeltaSteppingMutateStub;
import org.neo4j.gds.procedures.algorithms.pathfinding.stubs.LocalPrizeCollectingSteinerTreeMutateStub;
import org.neo4j.gds.procedures.algorithms.pathfinding.stubs.LocalRandomWalkMutateStub;
import org.neo4j.gds.procedures.algorithms.pathfinding.stubs.LocalSinglePairShortestPathAStarMutateStub;
import org.neo4j.gds.procedures.algorithms.pathfinding.stubs.LocalSinglePairShortestPathDijkstraMutateStub;
import org.neo4j.gds.procedures.algorithms.pathfinding.stubs.LocalSinglePairShortestPathYensMutateStub;
import org.neo4j.gds.procedures.algorithms.pathfinding.stubs.LocalSingleSourceShortestPathDijkstraMutateStub;
import org.neo4j.gds.procedures.algorithms.pathfinding.stubs.LocalSpanningTreeMutateStub;
import org.neo4j.gds.procedures.algorithms.pathfinding.stubs.LocalSteinerTreeMutateStub;
import org.neo4j.gds.procedures.algorithms.pathfinding.stubs.PathFindingStubs;
import org.neo4j.gds.procedures.algorithms.results.StandardModeResult;
import org.neo4j.gds.procedures.algorithms.results.StandardStatsResult;
import org.neo4j.gds.procedures.algorithms.results.StandardWriteRelationshipsResult;
import org.neo4j.gds.procedures.algorithms.stubs.GenericStub;
import org.neo4j.gds.spanningtree.SpanningTreeStatsConfig;
import org.neo4j.gds.spanningtree.SpanningTreeStreamConfig;
import org.neo4j.gds.spanningtree.SpanningTreeWriteConfig;
import org.neo4j.gds.steiner.SteinerTreeStatsConfig;
import org.neo4j.gds.steiner.SteinerTreeStreamConfig;
import org.neo4j.gds.steiner.SteinerTreeWriteConfig;
import org.neo4j.gds.traversal.RandomWalkStatsConfig;
import org.neo4j.gds.traversal.RandomWalkStreamConfig;

import java.util.Map;
import java.util.stream.Stream;

/**
 * This is the top facade on the Neo4j Procedures integration for path finding algorithms.
 * The role it plays is, to be newed up with request scoped dependencies,
 * and to capture the procedure-specific bits of path finding algorithms calls.
 * For example, translating a return column specification into a parameter, a business level concept.
 * This is also where we put result rendering.
 */
public final class LocalPathFindingProcedureFacade implements PathFindingProcedureFacade {
    // request scoped services
    private final CloseableResourceRegistry closeableResourceRegistry;
    private final NodeLookup nodeLookup;
    private final ProcedureReturnColumns procedureReturnColumns;

    // delegate
    private final PathFindingAlgorithmsEstimationModeBusinessFacade estimationModeBusinessFacade;
    private final PathFindingAlgorithmsStatsModeBusinessFacade statsModeBusinessFacade;
    private final PathFindingAlgorithmsStreamModeBusinessFacade streamModeBusinessFacade;
    private final PathFindingAlgorithmsWriteModeBusinessFacade writeModeBusinessFacade;

    // applications
    private final PathFindingStubs stubs;

    // infrastructure
    private final UserSpecificConfigurationParser configurationParser;

    private LocalPathFindingProcedureFacade(
        CloseableResourceRegistry closeableResourceRegistry,
        NodeLookup nodeLookup,
        ProcedureReturnColumns procedureReturnColumns,
        PathFindingAlgorithmsEstimationModeBusinessFacade estimationModeBusinessFacade,
        PathFindingAlgorithmsStatsModeBusinessFacade statsModeBusinessFacade,
        PathFindingAlgorithmsStreamModeBusinessFacade streamModeBusinessFacade,
        PathFindingAlgorithmsWriteModeBusinessFacade writeModeBusinessFacade,
        PathFindingStubs stubs,
        UserSpecificConfigurationParser configurationParser
    ) {
        this.closeableResourceRegistry = closeableResourceRegistry;
        this.nodeLookup = nodeLookup;
        this.procedureReturnColumns = procedureReturnColumns;

        this.estimationModeBusinessFacade = estimationModeBusinessFacade;
        this.statsModeBusinessFacade = statsModeBusinessFacade;
        this.streamModeBusinessFacade = streamModeBusinessFacade;
        this.writeModeBusinessFacade = writeModeBusinessFacade;

        this.stubs = stubs;

        this.configurationParser = configurationParser;
    }

    /**
     * Encapsulating some of the boring structure stuff
     */
    public static PathFindingProcedureFacade create(
        CloseableResourceRegistry closeableResourceRegistry,
        NodeLookup nodeLookup,
        ProcedureReturnColumns procedureReturnColumns,
        ApplicationsFacade applicationsFacade,
        GenericStub genericStub,
        UserSpecificConfigurationParser configurationParser
    ) {
        var mutateModeBusinessFacade = applicationsFacade.pathFinding().mutate();
        var estimationModeBusinessFacade = applicationsFacade.pathFinding().estimate();
        var aStarStub = new LocalSinglePairShortestPathAStarMutateStub(
            genericStub,
            mutateModeBusinessFacade,
            estimationModeBusinessFacade
        );

        var bellmanFordMutateStub = new LocalBellmanFordMutateStub(
            genericStub,
            mutateModeBusinessFacade,
            estimationModeBusinessFacade
        );

        var breadthFirstSearchMutateStub = new LocalBFSMutateStub(
            genericStub,
            mutateModeBusinessFacade,
            estimationModeBusinessFacade
        );

        var deltaSteppingMutateStub = new LocalDeltaSteppingMutateStub(
            genericStub,
            mutateModeBusinessFacade,
            estimationModeBusinessFacade
        );

        var depthFirstSearchMutateStub = new LocalDFSMutateStub(
            genericStub,
            mutateModeBusinessFacade,
            estimationModeBusinessFacade
        );

        var prizeCollectingSteinerTreeMutateStub = new LocalPrizeCollectingSteinerTreeMutateStub(
            genericStub,
            mutateModeBusinessFacade,
            estimationModeBusinessFacade
        );


        var randomWalkMutateStub = new LocalRandomWalkMutateStub(
            genericStub,
            mutateModeBusinessFacade,
            estimationModeBusinessFacade
        );

        var singlePairDijkstraStub = new LocalSinglePairShortestPathDijkstraMutateStub(
            genericStub,
            mutateModeBusinessFacade,
            estimationModeBusinessFacade
        );

        var yensStub = new LocalSinglePairShortestPathYensMutateStub(
            genericStub,
            mutateModeBusinessFacade,
            estimationModeBusinessFacade
        );

        var singleSourceDijkstraStub = new LocalSingleSourceShortestPathDijkstraMutateStub(
            genericStub,
            mutateModeBusinessFacade,
            estimationModeBusinessFacade
        );

        var spanningTreeMutateStub = new LocalSpanningTreeMutateStub(
            genericStub,
            mutateModeBusinessFacade,
            estimationModeBusinessFacade
        );

        var steinerTreeMutateStub = new LocalSteinerTreeMutateStub(
            genericStub,
            mutateModeBusinessFacade,
            estimationModeBusinessFacade
        );

        var stubs = new PathFindingStubs(
            bellmanFordMutateStub,
            breadthFirstSearchMutateStub,
            deltaSteppingMutateStub,
            depthFirstSearchMutateStub,
            prizeCollectingSteinerTreeMutateStub,
            randomWalkMutateStub,
            aStarStub,
            singlePairDijkstraStub,
            yensStub,
            singleSourceDijkstraStub,
            spanningTreeMutateStub,
            steinerTreeMutateStub
        );

        return new LocalPathFindingProcedureFacade(
            closeableResourceRegistry,
            nodeLookup,
            procedureReturnColumns,
            estimationModeBusinessFacade,
            applicationsFacade.pathFinding().stats(),
            applicationsFacade.pathFinding().stream(),
            applicationsFacade.pathFinding().write(),
            stubs,
            configurationParser
        );
    }

    @Override
    public PathFindingStubs stubs() {
        return stubs;
    }

    @Override
    public Stream<AllShortestPathsStreamResult> allShortestPathStream(
        String graphName,
        Map<String, Object> configuration
    ) {
        return streamModeBusinessFacade.allShortestPaths(
            GraphName.parse(graphName),
            configurationParser.parseConfiguration(configuration, AllShortestPathsConfig::of),
            (g, gs, result) -> result.orElse(Stream.empty())
        );
    }

    @Override
    public Stream<BellmanFordStreamResult> bellmanFordStream(String graphName, Map<String, Object> configuration) {
        var routeRequested = procedureReturnColumns.contains("route");
        var resultBuilder = new BellmanFordResultBuilderForStreamMode(
            closeableResourceRegistry,
            nodeLookup,
            routeRequested
        );

        return streamModeBusinessFacade.bellmanFord(
            GraphName.parse(graphName),
            configurationParser.parseConfiguration(configuration, AllShortestPathsBellmanFordStreamConfig::of),
            resultBuilder
        );
    }

    @Override
    public Stream<MemoryEstimateResult> bellmanFordStreamEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return Stream.of(
            estimationModeBusinessFacade.bellmanFord(
                configurationParser.parseConfiguration(
                    algorithmConfiguration,
                    AllShortestPathsBellmanFordStreamConfig::of
                ),
                graphNameOrConfiguration
            )
        );
    }

    @Override
    public Stream<BellmanFordMutateResult> bellmanFordMutate(String graphName, Map<String, Object> configuration) {
        return stubs.bellmanFord().execute(graphName,configuration);
    }

    @Override
    public Stream<MemoryEstimateResult> bellmanFordMutateEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return stubs.bellmanFord().estimate(graphNameOrConfiguration,algorithmConfiguration);
    }

    @Override
    public Stream<BellmanFordStatsResult> bellmanFordStats(String graphName, Map<String, Object> rawConfiguration) {
        var configuration = configurationParser.parseConfiguration(
            rawConfiguration,
            AllShortestPathsBellmanFordStatsConfig::of
        );

        return statsModeBusinessFacade.bellmanFord(
            GraphName.parse(graphName),
            configuration,
            new BellmanFordResultBuilderForStatsMode(configuration)
        );
    }

    @Override
    public Stream<MemoryEstimateResult> bellmanFordStatsEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return Stream.of(
            estimationModeBusinessFacade.bellmanFord(
                configurationParser.parseConfiguration(
                    algorithmConfiguration,
                    AllShortestPathsBellmanFordStatsConfig::of
                ),
                graphNameOrConfiguration
            )
        );
    }

    @Override
    public Stream<BellmanFordWriteResult> bellmanFordWrite(
        String graphName,
        Map<String, Object> configuration
    ) {
        return Stream.of(
            writeModeBusinessFacade.bellmanFord(
                GraphName.parse(graphName),
                configurationParser.parseConfiguration(configuration, AllShortestPathsBellmanFordWriteConfig::of),
                new BellmanFordResultBuilderForWriteMode()
            )
        );
    }

    @Override
    public Stream<MemoryEstimateResult> bellmanFordWriteEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return Stream.of(
            estimationModeBusinessFacade.bellmanFord(
                configurationParser.parseConfiguration(
                    algorithmConfiguration,
                    AllShortestPathsBellmanFordWriteConfig::of
                ),
                graphNameOrConfiguration
            )
        );
    }

    @Override
    public Stream<PathFindingMutateResult> breadthFirstSearchMutate(
        String graphName,
        Map<String, Object> configuration
    ) {
        return stubs.bfs().execute(graphName,configuration);
    }

    @Override
    public Stream<MemoryEstimateResult> breadthFirstSearchMutateEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return stubs.bfs().estimate(graphNameOrConfiguration,algorithmConfiguration);
    }

    @Override
    public Stream<StandardStatsResult> breadthFirstSearchStats(String graphName, Map<String, Object> rawConfiguration) {
        var configuration = configurationParser.parseConfiguration(rawConfiguration, BfsStatsConfig::of);

        return statsModeBusinessFacade.breadthFirstSearch(
            GraphName.parse(graphName),
            configuration,
            new BfsStatsResultBuilder(configuration)
        );
    }

    @Override
    public Stream<MemoryEstimateResult> breadthFirstSearchStatsEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return Stream.of(
            estimationModeBusinessFacade.breadthFirstSearch(
                configurationParser.parseConfiguration(algorithmConfiguration, BfsStatsConfig::of),
                graphNameOrConfiguration
            )
        );
    }

    @Override
    public Stream<BfsStreamResult> breadthFirstSearchStream(String graphName, Map<String, Object> configuration) {
        var parsedConfig = configurationParser.parseConfiguration(configuration, BfsStreamConfig::of);
        return streamModeBusinessFacade.breadthFirstSearch(
            GraphName.parse(graphName),
            parsedConfig,
            new BfsStreamResultBuilder(nodeLookup, procedureReturnColumns.contains("path"), parsedConfig)
        );
    }

    @Override
    public Stream<MemoryEstimateResult> breadthFirstSearchStreamEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return Stream.of(
            estimationModeBusinessFacade.breadthFirstSearch(
                configurationParser.parseConfiguration(algorithmConfiguration, BfsStreamConfig::of),
                graphNameOrConfiguration
            )
        );
    }

    @Override
    public Stream<PathFindingMutateResult> deltaSteppingMutate(String graphName, Map<String, Object> configuration) {
        return stubs.deltaStepping().execute(graphName,configuration);
    }

    @Override
    public Stream<MemoryEstimateResult> deltaSteppingMutateEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return stubs.deltaStepping().estimate(graphNameOrConfiguration,algorithmConfiguration);
    }

    @Override
    public Stream<StandardStatsResult> deltaSteppingStats(String graphName, Map<String, Object> rawConfiguration) {
        var configuration = configurationParser.parseConfiguration(
            rawConfiguration,
            AllShortestPathsDeltaStatsConfig::of
        );

        return statsModeBusinessFacade.deltaStepping(
            GraphName.parse(graphName),
            configuration,
            new DeltaSteppingResultBuilderForStatsMode(configuration)
        );
    }

    @Override
    public Stream<MemoryEstimateResult> deltaSteppingStatsEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {

        return Stream.of(
            estimationModeBusinessFacade.deltaStepping(
                configurationParser.parseConfiguration(algorithmConfiguration, AllShortestPathsDeltaStatsConfig::of),
                graphNameOrConfiguration
            )
        );
    }

    @Override
    public Stream<PathFindingStreamResult> deltaSteppingStream(String graphName, Map<String, Object> configuration) {
        var resultBuilder = new PathFindingResultBuilderForStreamMode(
            closeableResourceRegistry,
            nodeLookup,
            procedureReturnColumns.contains("path")
        );

        return streamModeBusinessFacade.deltaStepping(
            GraphName.parse(graphName),
            configurationParser.parseConfiguration(configuration, AllShortestPathsDeltaStreamConfig::of),
            resultBuilder
        );
    }

    @Override
    public Stream<MemoryEstimateResult> deltaSteppingStreamEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return Stream.of(
            estimationModeBusinessFacade.deltaStepping(
                configurationParser.parseConfiguration(algorithmConfiguration, AllShortestPathsDeltaStreamConfig::of),
                graphNameOrConfiguration
            )
        );
    }

    @Override
    public Stream<StandardWriteRelationshipsResult> deltaSteppingWrite(
        String graphName,
        Map<String, Object> configuration
    ) {
        return Stream.of(
            writeModeBusinessFacade.deltaStepping(
                GraphName.parse(graphName),
                configurationParser.parseConfiguration(configuration, AllShortestPathsDeltaWriteConfig::of),
                new PathFindingResultBuilderForWriteMode<>()
            )
        );
    }

    @Override
    public Stream<MemoryEstimateResult> deltaSteppingWriteEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return Stream.of(
            estimationModeBusinessFacade.deltaStepping(
                configurationParser.parseConfiguration(algorithmConfiguration, AllShortestPathsDeltaWriteConfig::of),
                graphNameOrConfiguration
            )
        );
    }

    @Override
    public Stream<PathFindingMutateResult> depthFirstSearchMutate(String graphName, Map<String, Object> configuration) {
        return stubs.dfs().execute(graphName,configuration);
    }

    @Override
    public Stream<MemoryEstimateResult> depthFirstSearchMutateEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return stubs.dfs().estimate(graphNameOrConfiguration,algorithmConfiguration);
    }


    @Override
    public Stream<DfsStreamResult> depthFirstSearchStream(String graphName, Map<String, Object> configuration) {
        var parsedConfig = configurationParser.parseConfiguration(configuration, DfsStreamConfig::of);
        return streamModeBusinessFacade.depthFirstSearch(
            GraphName.parse(graphName),
            parsedConfig,
            new DfsStreamResultBuilder(nodeLookup, procedureReturnColumns.contains("path"), parsedConfig)
        );
    }

    @Override
    public Stream<MemoryEstimateResult> depthFirstSearchStreamEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return Stream.of(
            estimationModeBusinessFacade.depthFirstSearch(
                configurationParser.parseConfiguration(algorithmConfiguration, DfsStreamConfig::of),
                graphNameOrConfiguration
            )
        );
    }

    @Override
    public Stream<KSpanningTreeWriteResult> kSpanningTreeWrite(String graphName, Map<String, Object> configuration) {
        return Stream.of(
            writeModeBusinessFacade.kSpanningTree(
                GraphName.parse(graphName),
                configurationParser.parseConfiguration(configuration, KSpanningTreeWriteConfig::of),
                new KSpanningTreeResultBuilderForWriteMode()
            )
        );
    }

    @Override
    public Stream<PathFindingStreamResult> longestPathStream(String graphName, Map<String, Object> configuration) {
        var resultBuilder = new PathFindingResultBuilderForStreamMode(
            closeableResourceRegistry,
            nodeLookup,
            procedureReturnColumns.contains("path")
        );

        return streamModeBusinessFacade.longestPath(
            GraphName.parse(graphName),
            configurationParser.parseConfiguration(configuration, DagLongestPathStreamConfig::of),
            resultBuilder
        );
    }

    @Override
    public Stream<SpanningTreeStreamResult> prizeCollectingSteinerTreeStream(
        String graphName,
        Map<String, Object> configuration
    ) {

        var resultBuilder  = new PrizeCollectingSteinerTreeResultBuilderForStreamMode();
        return streamModeBusinessFacade.pcst(
            GraphName.parse(graphName),
            configurationParser.parseConfiguration(configuration, PCSTStreamConfig::of),
            resultBuilder
        );
    }

    @Override
    public Stream<PrizeCollectingSteinerTreeMutateResult> prizeCollectingSteinerTreeMutate(
        String graphName,
        Map<String, Object> configuration
    ) {
        return stubs.pcst().execute(graphName,configuration);
    }

    @Override
    public Stream<MemoryEstimateResult> prizeCollectingSteinerTreeMutateEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return stubs.pcst().estimate(graphNameOrConfiguration,algorithmConfiguration);
    }

    @Override
    public Stream<MemoryEstimateResult> prizeCollectingSteinerTreeStreamEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return
            Stream.of(
                estimationModeBusinessFacade.pcst(
                    configurationParser.parseConfiguration(algorithmConfiguration, PCSTStreamConfig::of),
                    graphNameOrConfiguration
                )
            );
    }

    @Override
    public Stream<PrizeCollectingSteinerTreeStatsResult> prizeCollectingSteinerTreeStats(
        String graphName,
        Map<String, Object> configuration
    ) {
        var config = configurationParser.parseConfiguration(configuration, PCSTStatsConfig::of);
        var resultBuilder  = new PrizeCollectingSteinerTreeResultBuilderForStatsMode(config);

        return statsModeBusinessFacade.pcst(
            GraphName.parse(graphName),
            config,
            resultBuilder
        );
    }

    @Override
    public Stream<MemoryEstimateResult> prizeCollectingSteinerTreeStatsEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return
            Stream.of(
                estimationModeBusinessFacade.pcst(
                    configurationParser.parseConfiguration(algorithmConfiguration, PCSTStatsConfig::of),
                    graphNameOrConfiguration
                )
            );
    }

    @Override
    public Stream<PrizeCollectingSteinerTreeWriteResult> prizeCollectingSteinerTreeWrite(
        String graphName,
        Map<String, Object> configuration
    ) {
        var config = configurationParser.parseConfiguration(configuration, PCSTWriteConfig::of);
        var resultBuilder  = new PrizeCollectingSteinerTreeResultBuilderForWriteMode();

        return  Stream.of(
                writeModeBusinessFacade.pcst(
                GraphName.parse(graphName),
                config,
                resultBuilder
            )
        );
    }

    @Override
    public Stream<MemoryEstimateResult> prizeCollectingSteinerTreeWriteEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return
            Stream.of(
                estimationModeBusinessFacade.pcst(
                    configurationParser.parseConfiguration(algorithmConfiguration, PCSTWriteConfig::of),
                    graphNameOrConfiguration
                )
            );
    }

    @Override
    public Stream<StandardModeResult> randomWalkStats(String graphName, Map<String, Object> rawConfiguration) {
        var configuration = configurationParser.parseConfiguration(
            rawConfiguration,
            RandomWalkStatsConfig::of
        );

        return statsModeBusinessFacade.randomWalk(
            GraphName.parse(graphName),
            configuration,
            new RandomWalkResultBuilderForStatsMode(configuration)
        );
    }

    @Override
    public Stream<MemoryEstimateResult> randomWalkStatsEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return Stream.of(
            estimationModeBusinessFacade.randomWalk(
                configurationParser.parseConfiguration(algorithmConfiguration, RandomWalkStatsConfig::of),
                graphNameOrConfiguration
            )
        );
    }

    @Override
    public Stream<RandomWalkStreamResult> randomWalkStream(String graphName, Map<String, Object> configuration) {
        var resultBuilder = new RandomWalkResultBuilderForStreamMode(
            closeableResourceRegistry,
            nodeLookup,
            procedureReturnColumns.contains("path")
        );

        return streamModeBusinessFacade.randomWalk(
            GraphName.parse(graphName),
            configurationParser.parseConfiguration(configuration, RandomWalkStreamConfig::of),
            resultBuilder
        );
    }

    @Override
    public Stream<MemoryEstimateResult> randomWalkStreamEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {

        return Stream.of(
            estimationModeBusinessFacade.randomWalk(
                configurationParser.parseConfiguration(algorithmConfiguration, RandomWalkStreamConfig::of),
                graphNameOrConfiguration
            )
        );
    }

    @Override
    public Stream<RandomWalkMutateResult> randomWalkMutate(String graphName, Map<String, Object> configuration) {
        return stubs.randomWalk().execute(graphName,configuration);
    }

    @Override
    public Stream<MemoryEstimateResult> randomWalkMutateEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return stubs.randomWalk().estimate(graphNameOrConfiguration,algorithmConfiguration);
    }

    @Override
    public Stream<PathFindingStreamResult> singlePairShortestPathAStarStream(
        String graphName,
        Map<String, Object> configuration
    ) {
        var resultBuilder = new PathFindingResultBuilderForStreamMode(
            closeableResourceRegistry,
            nodeLookup,
            procedureReturnColumns.contains("path")
        );

        return streamModeBusinessFacade.singlePairShortestPathAStar(
            GraphName.parse(graphName),
            configurationParser.parseConfiguration(configuration, ShortestPathAStarStreamConfig::of),
            resultBuilder
        );
    }

    @Override
    public Stream<MemoryEstimateResult> singlePairShortestPathAStarStreamEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {

        return Stream.of(
            estimationModeBusinessFacade.singlePairShortestPathAStar(
                configurationParser.parseConfiguration(algorithmConfiguration, ShortestPathAStarStreamConfig::of),
                graphNameOrConfiguration
            )
        );
    }

    @Override
    public Stream<PathFindingMutateResult> singlePairShortestPathAStarMutate(
        String graphName,
        Map<String, Object> configuration
    ) {
        return stubs.aStar().execute(graphName,configuration);
    }

    @Override
    public Stream<MemoryEstimateResult> singlePairShortestPathAStarMutateEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return stubs.aStar().estimate(graphNameOrConfiguration,algorithmConfiguration);
    }

    @Override
    public Stream<StandardWriteRelationshipsResult> singlePairShortestPathAStarWrite(
        String graphName,
        Map<String, Object> configuration
    ) {
        return Stream.of(
            writeModeBusinessFacade.singlePairShortestPathAStar(
                GraphName.parse(graphName),
                configurationParser.parseConfiguration(configuration, ShortestPathAStarWriteConfig::of),
                new PathFindingResultBuilderForWriteMode<>()
            )
        );
    }

    @Override
    public Stream<MemoryEstimateResult> singlePairShortestPathAStarWriteEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return Stream.of(
            estimationModeBusinessFacade.singlePairShortestPathAStar(
                configurationParser.parseConfiguration(algorithmConfiguration, ShortestPathAStarWriteConfig::of),
                graphNameOrConfiguration
            )
        );
    }

    @Override
    public Stream<PathFindingStreamResult> singlePairShortestPathDijkstraStream(
        String graphName,
        Map<String, Object> configuration
    ) {
        var resultBuilder = new PathFindingResultBuilderForStreamMode(
            closeableResourceRegistry,
            nodeLookup,
            procedureReturnColumns.contains("path")
        );

        return streamModeBusinessFacade.singlePairShortestPathDijkstra(
            GraphName.parse(graphName),
            configurationParser.parseConfiguration(configuration, ShortestPathDijkstraStreamConfig::of),
            resultBuilder
        );
    }

    @Override
    public Stream<MemoryEstimateResult> singlePairShortestPathDijkstraStreamEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return Stream.of(
            estimationModeBusinessFacade.singlePairShortestPathDijkstra(
                configurationParser.parseConfiguration(algorithmConfiguration, ShortestPathDijkstraStreamConfig::of),
                graphNameOrConfiguration
            )
        );
    }

    @Override
    public Stream<PathFindingMutateResult> singlePairShortestPathDijkstraMutate(
        String graphName,
        Map<String, Object> configuration
    ) {
        return stubs.singlePairDijkstra().execute(graphName,configuration);
    }

    @Override
    public Stream<MemoryEstimateResult> singlePairShortestPathDijkstraMutateEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return stubs.singlePairDijkstra().estimate(graphNameOrConfiguration,algorithmConfiguration);
    }

    @Override
    public Stream<StandardWriteRelationshipsResult> singlePairShortestPathDijkstraWrite(
        String graphName,
        Map<String, Object> configuration
    ) {
        return Stream.of(
            writeModeBusinessFacade.singlePairShortestPathDijkstra(
                GraphName.parse(graphName),
                configurationParser.parseConfiguration(configuration, ShortestPathDijkstraWriteConfig::of),
                new PathFindingResultBuilderForWriteMode<>()
            )
        );
    }

    @Override
    public Stream<MemoryEstimateResult> singlePairShortestPathDijkstraWriteEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {

        return Stream.of(
            estimationModeBusinessFacade.singlePairShortestPathDijkstra(
                configurationParser.parseConfiguration(algorithmConfiguration, ShortestPathDijkstraWriteConfig::of),
                graphNameOrConfiguration
            )
        );
    }

    @Override
    public Stream<PathFindingStreamResult> singlePairShortestPathYensStream(
        String graphName,
        Map<String, Object> configuration
    ) {
        var resultBuilder = new PathFindingResultBuilderForStreamMode(
            closeableResourceRegistry,
            nodeLookup,
            procedureReturnColumns.contains("path")
        );

        return streamModeBusinessFacade.singlePairShortestPathYens(
            GraphName.parse(graphName),
            configurationParser.parseConfiguration(configuration, ShortestPathYensStreamConfig::of),
            resultBuilder
        );
    }

    @Override
    public Stream<MemoryEstimateResult> singlePairShortestPathYensStreamEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {

        return Stream.of(
            estimationModeBusinessFacade.singlePairShortestPathYens(
                configurationParser.parseConfiguration(algorithmConfiguration, ShortestPathYensStreamConfig::of),
                graphNameOrConfiguration
            )
        );
    }

    @Override
    public Stream<PathFindingMutateResult> singlePairShortestPathYensMutate(
        String graphName,
        Map<String, Object> configuration
    ) {
        return stubs.yens().execute(graphName,configuration);
    }

    @Override
    public Stream<MemoryEstimateResult> singlePairShortestPathYensMutateEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return stubs.yens().estimate(graphNameOrConfiguration,algorithmConfiguration);
    }

    @Override
    public Stream<StandardWriteRelationshipsResult> singlePairShortestPathYensWrite(
        String graphName,
        Map<String, Object> configuration
    ) {
        return Stream.of(
            writeModeBusinessFacade.singlePairShortestPathYens(
                GraphName.parse(graphName),
                configurationParser.parseConfiguration(configuration, ShortestPathYensWriteConfig::of),
                new PathFindingResultBuilderForWriteMode<>()
            )
        );
    }

    @Override
    public Stream<MemoryEstimateResult> singlePairShortestPathYensWriteEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return Stream.of(
            estimationModeBusinessFacade.singlePairShortestPathYens(
                configurationParser.parseConfiguration(algorithmConfiguration, ShortestPathYensWriteConfig::of),
                graphNameOrConfiguration
            )
        );
    }

    @Override
    public Stream<PathFindingStreamResult> singleSourceShortestPathDijkstraStream(
        String graphName,
        Map<String, Object> configuration
    ) {
        var resultBuilder = new PathFindingResultBuilderForStreamMode(
            closeableResourceRegistry,
            nodeLookup,
            procedureReturnColumns.contains("path")
        );
        return streamModeBusinessFacade.singleSourceShortestPathDijkstra(
            GraphName.parse(graphName),
            configurationParser.parseConfiguration(configuration, AllShortestPathsDijkstraStreamConfig::of),
            resultBuilder
        );
    }

    @Override
    public Stream<MemoryEstimateResult> singleSourceShortestPathDijkstraStreamEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return Stream.of(
            estimationModeBusinessFacade.singleSourceShortestPathDijkstra(
                configurationParser.parseConfiguration(
                    algorithmConfiguration,
                    AllShortestPathsDijkstraStreamConfig::of
                ),
                graphNameOrConfiguration
            )
        );
    }

    @Override
    public Stream<PathFindingMutateResult> singleSourceShortestPathDijkstraMutate(
        String graphName,
        Map<String, Object> configuration
    ) {
        return stubs.singleSourceDijktra().execute(graphName,configuration);
    }

    @Override
    public Stream<MemoryEstimateResult> singleSourceShortestPathDijkstraMutateEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return stubs.singleSourceDijktra().estimate(graphNameOrConfiguration,algorithmConfiguration);
    }

    @Override
    public Stream<StandardWriteRelationshipsResult> singleSourceShortestPathDijkstraWrite(
        String graphName,
        Map<String, Object> configuration
    ) {
        return Stream.of(
            writeModeBusinessFacade.singleSourceShortestPathDijkstra(
                GraphName.parse(graphName),
                configurationParser.parseConfiguration(configuration, AllShortestPathsDijkstraWriteConfig::of),
                new PathFindingResultBuilderForWriteMode<>()
            )
        );
    }

    @Override
    public Stream<MemoryEstimateResult> singleSourceShortestPathDijkstraWriteEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return Stream.of(
            estimationModeBusinessFacade.singleSourceShortestPathDijkstra(
                configurationParser.parseConfiguration(algorithmConfiguration, AllShortestPathsDijkstraWriteConfig::of),
                graphNameOrConfiguration
            )
        );
    }


    @Override
    public Stream<SpanningTreeStatsResult> spanningTreeStats(
        String graphName,
        Map<String, Object> rawConfiguration
    ) {
        var configuration = configurationParser.parseConfiguration(
            rawConfiguration,
            SpanningTreeStatsConfig::of
        );

        return statsModeBusinessFacade.spanningTree(
            GraphName.parse(graphName),
            configuration,
            new SpanningTreeResultBuilderForStatsMode(configuration)
        );
    }

    @Override
    public Stream<MemoryEstimateResult> spanningTreeStatsEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return Stream.of(
            estimationModeBusinessFacade.spanningTree(
                configurationParser.parseConfiguration(algorithmConfiguration, SpanningTreeStatsConfig::of),
                graphNameOrConfiguration
            )
        );
    }

    @Override
    public Stream<SpanningTreeMutateResult> spanningTreeMutate(String graphName, Map<String, Object> configuration) {
        return  stubs.spanningTree().execute(graphName,configuration);
    }

    @Override
    public Stream<MemoryEstimateResult> spanningTreeMutateEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return stubs.spanningTree().estimate(graphNameOrConfiguration,algorithmConfiguration);
    }

    @Override
    public Stream<SpanningTreeStreamResult> spanningTreeStream(String graphName, Map<String, Object> configuration) {
        var parsedConfig = configurationParser.parseConfiguration(
            configuration,
            SpanningTreeStreamConfig::of
        );
        return streamModeBusinessFacade.spanningTree(
            GraphName.parse(graphName),
            parsedConfig,
            new SpanningTreeResultBuilderForStreamMode(parsedConfig)
        );
    }

    @Override
    public Stream<MemoryEstimateResult> spanningTreeStreamEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return Stream.of(
            estimationModeBusinessFacade.spanningTree(
                configurationParser.parseConfiguration(algorithmConfiguration, SpanningTreeStreamConfig::of),
                graphNameOrConfiguration
            )
        );
    }

    @Override
    public Stream<SpanningTreeWriteResult> spanningTreeWrite(String graphName, Map<String, Object> configuration) {
        return Stream.of(
            writeModeBusinessFacade.spanningTree(
                GraphName.parse(graphName),
                configurationParser.parseConfiguration(configuration, SpanningTreeWriteConfig::of),
                new SpanningTreeResultBuilderForWriteMode()
            )
        );
    }

    @Override
    public Stream<MemoryEstimateResult> spanningTreeWriteEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return Stream.of(
            estimationModeBusinessFacade.spanningTree(
                configurationParser.parseConfiguration(algorithmConfiguration, SpanningTreeWriteConfig::of),
                graphNameOrConfiguration
            )
        );
    }

    @Override
    public Stream<SteinerStatsResult> steinerTreeStats(String graphName, Map<String, Object> rawConfiguration) {
        var configuration = configurationParser.parseConfiguration(
            rawConfiguration,
            SteinerTreeStatsConfig::of
        );

        return statsModeBusinessFacade.steinerTree(
            GraphName.parse(graphName),
            configuration,
            new SteinerTreeResultBuilderForStatsMode(configuration)
        );
    }

    @Override
    public Stream<MemoryEstimateResult> steinerTreeStatsEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return Stream.of(
            estimationModeBusinessFacade.steinerTree(
                configurationParser.parseConfiguration(algorithmConfiguration, SteinerTreeStatsConfig::of),
                graphNameOrConfiguration
            )
        );
    }

    @Override
    public Stream<SteinerMutateResult> steinerTreeMutate(String graphName, Map<String, Object> configuration) {
        return stubs.steinerTree().execute(graphName,configuration);
    }

    @Override
    public Stream<MemoryEstimateResult> steinerTreeMutateEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return stubs.steinerTree().estimate(graphNameOrConfiguration,algorithmConfiguration);
    }

    @Override
    public Stream<SpanningTreeStreamResult> steinerTreeStream(String graphName, Map<String, Object> configuration) {
        var parsedConfig = configurationParser.parseConfiguration(
            configuration,
            SteinerTreeStreamConfig::of
        );
        return streamModeBusinessFacade.steinerTree(
            GraphName.parse(graphName),
            parsedConfig,
            new SteinerTreeResultBuilderForStreamMode(parsedConfig)
        );
    }

    @Override
    public Stream<MemoryEstimateResult> steinerTreeStreamEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return Stream.of(
            estimationModeBusinessFacade.steinerTree(
                configurationParser.parseConfiguration(algorithmConfiguration, SteinerTreeStreamConfig::of),
                graphNameOrConfiguration
            )
        );
    }

    @Override
    public Stream<SteinerWriteResult> steinerTreeWrite(String graphName, Map<String, Object> configuration) {
        return Stream.of(
            writeModeBusinessFacade.steinerTree(
                GraphName.parse(graphName),
                configurationParser.parseConfiguration(configuration, SteinerTreeWriteConfig::of),
                new SteinerTreeResultBuilderForWriteMode()
            )
        );
    }

    @Override
    public Stream<MemoryEstimateResult> steinerTreeWriteEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algorithmConfiguration
    ) {
        return Stream.of(
            estimationModeBusinessFacade.steinerTree(
                configurationParser.parseConfiguration(algorithmConfiguration, SteinerTreeWriteConfig::of),
                graphNameOrConfiguration
            )
        );
    }

    @Override
    public Stream<TopologicalSortStreamResult> topologicalSortStream(
        String graphName,
        Map<String, Object> configuration
    ) {
        return streamModeBusinessFacade.topologicalSort(
            GraphName.parse(graphName),
            configurationParser.parseConfiguration(configuration, TopologicalSortStreamConfig::of),
            new TopologicalSortResultBuilderForStreamMode()
        );
    }
}
