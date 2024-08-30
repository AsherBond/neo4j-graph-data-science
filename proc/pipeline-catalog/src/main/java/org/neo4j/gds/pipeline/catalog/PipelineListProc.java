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
package org.neo4j.gds.pipeline.catalog;

import org.neo4j.gds.procedures.GraphDataScienceProcedures;
import org.neo4j.gds.procedures.pipelines.PipelineCatalogResult;
import org.neo4j.gds.procedures.pipelines.PipelinesProcedureFacade;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Internal;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.stream.Stream;

import static org.neo4j.procedure.Mode.READ;

public class PipelineListProc {
    private static final String DESCRIPTION = "Lists all pipelines contained in the pipeline catalog.";

    @Context
    public GraphDataScienceProcedures facade;

    @Procedure(name = "gds.pipeline.list", mode = READ)
    @Description(DESCRIPTION)
    public Stream<PipelineCatalogResult> list(@Name(value = "pipelineName", defaultValue = PipelinesProcedureFacade.NO_VALUE) String pipelineName) {
        return facade.pipelines().list(pipelineName);
    }

    @Procedure(name = "gds.beta.pipeline.list", mode = READ, deprecatedBy = "gds.pipeline.list")
    @Description(DESCRIPTION)
    @Internal
    @Deprecated(forRemoval = true)
    public Stream<PipelineCatalogResult> betaList(@Name(value = "pipelineName", defaultValue = PipelinesProcedureFacade.NO_VALUE) String pipelineName) {
        facade.deprecatedProcedures().called("gds.beta.pipeline.list");
        facade
            .log()
            .warn("The procedure `gds.beta.pipeline.list` is deprecated and will be removed in a future release. Please use `gds.pipeline.list` instead.");

        return list(pipelineName);
    }
}
