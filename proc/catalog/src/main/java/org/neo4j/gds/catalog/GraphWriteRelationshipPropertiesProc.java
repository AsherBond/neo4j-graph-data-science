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

import org.neo4j.gds.applications.graphstorecatalog.WriteRelationshipPropertiesResult;
import org.neo4j.gds.procedures.GraphDataScienceProcedures;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.neo4j.gds.catalog.GraphCatalogProcedureConstants.WRITE_RELATIONSHIP_PROPERTIES_DESCRIPTION;
import static org.neo4j.procedure.Mode.WRITE;

public class GraphWriteRelationshipPropertiesProc {
    @Context
    public GraphDataScienceProcedures facade;

    @SuppressWarnings("unused")
    @Procedure(name = "gds.graph.relationshipProperties.write", mode = WRITE)
    @Description(WRITE_RELATIONSHIP_PROPERTIES_DESCRIPTION)
    public Stream<WriteRelationshipPropertiesResult> writeRelationshipProperties(
        @Name(value = "graphName") String graphName,
        @Name(value = "relationshipType") String relationshipType,
        @Name(value = "relationshipProperties", defaultValue = "[]") List<String> relationshipProperties,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return facade.graphCatalog().writeRelationshipProperties(
            graphName,
            relationshipType,
            relationshipProperties,
            configuration
        );
    }
}
