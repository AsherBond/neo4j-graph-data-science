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
package org.neo4j.gds.procedures.centrality;

import org.neo4j.gds.algorithms.NodePropertyMutateResult;
import org.neo4j.gds.algorithms.NodePropertyWriteResult;
import org.neo4j.gds.algorithms.StatsResult;
import org.neo4j.gds.algorithms.StreamComputationResult;
import org.neo4j.gds.algorithms.centrality.CentralityAlgorithmResult;
import org.neo4j.gds.algorithms.centrality.specificfields.DefaultCentralitySpecificFields;
import org.neo4j.gds.api.IdMap;
import org.neo4j.gds.config.AlgoBaseConfig;
import org.neo4j.gds.procedures.algorithms.centrality.CentralityMutateResult;
import org.neo4j.gds.procedures.algorithms.centrality.CentralityStatsResult;
import org.neo4j.gds.procedures.algorithms.centrality.CentralityStreamResult;
import org.neo4j.gds.procedures.algorithms.centrality.CentralityWriteResult;

import java.util.stream.LongStream;
import java.util.stream.Stream;

final class DefaultCentralityComputationalResultTransformer {

    private DefaultCentralityComputationalResultTransformer() {}


    static <RESULT extends CentralityAlgorithmResult> Stream<CentralityStreamResult> toStreamResult(
        StreamComputationResult<RESULT> computationResult
    ) {
        return computationResult.result().map(result -> {
            var nodePropertyValues = result.nodePropertyValues();
            var graph = computationResult.graph();
            return LongStream
                .range(IdMap.START_NODE_ID, graph.nodeCount())
                .filter(nodePropertyValues::hasValue)
                .mapToObj(nodeId ->
                    new CentralityStreamResult(
                        graph.toOriginalNodeId(nodeId),
                        nodePropertyValues.doubleValue(nodeId)
                    ));

        }).orElseGet(Stream::empty);
    }

    static CentralityStatsResult toStatsResult(
        StatsResult<DefaultCentralitySpecificFields> computationResult,
        AlgoBaseConfig configuration
    ) {
        return new CentralityStatsResult(
            computationResult.algorithmSpecificFields().centralityDistribution(),
            computationResult.preProcessingMillis(),
            computationResult.computeMillis(),
            computationResult.postProcessingMillis(),
            configuration.toMap()
        );
    }

    static CentralityMutateResult toMutateResult(
        NodePropertyMutateResult<DefaultCentralitySpecificFields> computationResult
    ) {
        return new CentralityMutateResult(
            computationResult.nodePropertiesWritten(),
            computationResult.preProcessingMillis(),
            computationResult.computeMillis(),
            computationResult.postProcessingMillis(),
            computationResult.mutateMillis(),
            computationResult.algorithmSpecificFields().centralityDistribution(),
            computationResult.configuration().toMap()
        );
    }

    static CentralityWriteResult toWriteResult(
        NodePropertyWriteResult<DefaultCentralitySpecificFields> computationResult
    ) {
        return new CentralityWriteResult(
            computationResult.nodePropertiesWritten(),
            computationResult.preProcessingMillis(),
            computationResult.computeMillis(),
            computationResult.postProcessingMillis(),
            computationResult.writeMillis(),
            computationResult.algorithmSpecificFields().centralityDistribution(),
            computationResult.configuration().toMap()
        );
    }

}
