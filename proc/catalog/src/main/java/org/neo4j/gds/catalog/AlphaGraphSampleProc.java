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
package org.neo4j.gds.catalog;

import org.neo4j.gds.applications.graphstorecatalog.RandomWalkSamplingResult;
import org.neo4j.gds.procedures.GraphDataScienceProcedures;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Internal;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.stream.Stream;

import static org.neo4j.gds.catalog.GraphCatalogProcedureConstants.RWR_DESCRIPTION;
import static org.neo4j.procedure.Mode.READ;

public class AlphaGraphSampleProc {
    @Context
    public GraphDataScienceProcedures facade;

    @Internal
    @Deprecated(forRemoval = true)
    @Procedure(name = "gds.alpha.graph.sample.rwr", mode = READ, deprecatedBy = "gds.graph.sample.rwr")
    @Description(RWR_DESCRIPTION)
    public Stream<RandomWalkSamplingResult> sampleRandomWalkWithRestartsAlpha(
        @Name(value = "graphName") String graphName,
        @Name(value = "fromGraphName") String fromGraphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        facade.deprecatedProcedures().called("gds.alpha.graph.sample.rwr");
        facade.log()
            .warn("Procedure `gds.alpha.graph.sample.rwr` has been deprecated, please use `gds.graph.sample.rwr`.");
        return facade.graphCatalog().sampleRandomWalkWithRestarts(graphName, fromGraphName, configuration);
    }
}
