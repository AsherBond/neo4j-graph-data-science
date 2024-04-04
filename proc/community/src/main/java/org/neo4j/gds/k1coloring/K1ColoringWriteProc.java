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
package org.neo4j.gds.k1coloring;

import org.neo4j.gds.BaseProc;
import org.neo4j.gds.procedures.GraphDataScienceProcedures;
import org.neo4j.gds.procedures.community.k1coloring.K1ColoringWriteResult;
import org.neo4j.gds.results.MemoryEstimateResult;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Internal;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.stream.Stream;

import static org.neo4j.gds.k1coloring.K1ColoringSpecificationHelper.K1_COLORING_DESCRIPTION;
import static org.neo4j.gds.procedures.ProcedureConstants.MEMORY_ESTIMATION_DESCRIPTION;
import static org.neo4j.procedure.Mode.READ;
import static org.neo4j.procedure.Mode.WRITE;

public class K1ColoringWriteProc extends BaseProc {

    @Context
    public GraphDataScienceProcedures facade;

    @Procedure(name = "gds.k1coloring.write", mode = WRITE)
    @Description(K1_COLORING_DESCRIPTION)
    public Stream<K1ColoringWriteResult> write(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return facade.community().k1ColoringWrite(graphName, configuration);
    }

    @Procedure(value = "gds.k1coloring.write.estimate", mode = READ)
    @Description(MEMORY_ESTIMATION_DESCRIPTION)
    public Stream<MemoryEstimateResult> estimate(
        @Name(value = "graphNameOrConfiguration") Object graphNameOrConfiguration,
        @Name(value = "algoConfiguration") Map<String, Object> algoConfiguration
    ) {
        return facade.community().k1ColoringEstimateWrite(graphNameOrConfiguration, algoConfiguration);
    }

    @Procedure(name = "gds.beta.k1coloring.write", mode = WRITE, deprecatedBy ="gds.k1coloring.write" )
    @Description(K1_COLORING_DESCRIPTION)
    @Internal
    @Deprecated(forRemoval = true)
    public Stream<K1ColoringWriteResult> betaWrite(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        executionContext()
            .metricsFacade()
            .deprecatedProcedures().called("gds.beta.k1coloring.write");
        executionContext()
            .log()
            .warn(
                "Procedure `gds.beta.k1coloring.write` has been deprecated, please use `gds.k1coloring.write`.");
        return  write(graphName,configuration);
    }

    @Procedure(value = "gds.beta.k1coloring.write.estimate", mode = READ, deprecatedBy = "gds.k1coloring.write.estimate")
    @Description(ESTIMATE_DESCRIPTION)
    @Internal
    @Deprecated(forRemoval = true)
    public Stream<MemoryEstimateResult> betaEstimate(
        @Name(value = "graphNameOrConfiguration") Object graphNameOrConfiguration,
        @Name(value = "algoConfiguration") Map<String, Object> algoConfiguration
    ) {
        executionContext()
            .metricsFacade()
            .deprecatedProcedures().called("gds.beta.k1coloring.write.estimate");
        executionContext()
            .log()
            .warn(
                "Procedure `gds.beta.k1coloring.write.estimate` has been deprecated, please use `gds.k1coloring.write.estimate`.");
        return estimate(graphNameOrConfiguration,algoConfiguration);
    }

}
