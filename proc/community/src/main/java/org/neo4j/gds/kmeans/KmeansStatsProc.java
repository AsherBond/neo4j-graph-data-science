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
package org.neo4j.gds.kmeans;

import org.neo4j.gds.applications.algorithms.machinery.MemoryEstimateResult;
import org.neo4j.gds.procedures.GraphDataScienceProcedures;
import org.neo4j.gds.procedures.algorithms.community.KmeansStatsResult;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Internal;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.stream.Stream;

import static org.neo4j.gds.kmeans.Constants.KMEANS_DESCRIPTION;
import static org.neo4j.gds.procedures.ProcedureConstants.MEMORY_ESTIMATION_DESCRIPTION;
import static org.neo4j.procedure.Mode.READ;

public class KmeansStatsProc {
    @Context
    public GraphDataScienceProcedures facade;

    @Procedure(value = "gds.kmeans.stats", mode = READ)
    @Description(KMEANS_DESCRIPTION)
    public Stream<KmeansStatsResult> stats(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return facade.algorithms().community().kmeansStats(graphName, configuration);
    }

    @Deprecated(forRemoval = true)
    @Internal
    @Procedure(value = "gds.beta.kmeans.stats", mode = READ, deprecatedBy = "gds.kmeans.stats")
    @Description(KMEANS_DESCRIPTION)
    public Stream<KmeansStatsResult> betaStats(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        facade.deprecatedProcedures().called("gds.beta.kmeans.stats");
        facade
            .log()
            .warn("Procedure `gds.beta.kmeans.stats` has been deprecated, please use `gds.kmeans.stats`.");
        return stats(graphName, configuration);
    }

    @Procedure(value = "gds.kmeans.stats.estimate", mode = READ)
    @Description(MEMORY_ESTIMATION_DESCRIPTION)
    public Stream<MemoryEstimateResult> estimate(
        @Name(value = "graphNameOrConfiguration") Object graphName,
        @Name(value = "algoConfiguration") Map<String, Object> configuration
    ) {
        return facade.algorithms().community().kmeansStatsEstimate(graphName, configuration);
    }

    @Deprecated(forRemoval = true)
    @Internal
    @Procedure(value = "gds.beta.kmeans.stats.estimate", mode = READ, deprecatedBy = "gds.kmeans.stats.estimate")
    @Description(MEMORY_ESTIMATION_DESCRIPTION)
    public Stream<MemoryEstimateResult> betaEstimate(
        @Name(value = "graphNameOrConfiguration") Object graphName,
        @Name(value = "algoConfiguration") Map<String, Object> configuration
    ) {
        facade.deprecatedProcedures().called("gds.beta.kmeans.stats.estimate");
        facade
            .log()
            .warn("Procedure `gds.beta.kmeans.stats.estimate` has been deprecated, please use `gds.kmeans.stats.estimate`.");
        return estimate(graphName, configuration);
    }
}
