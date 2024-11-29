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
package org.neo4j.gds.memory;

import org.neo4j.gds.mem.UserEntityMemory;
import org.neo4j.gds.mem.UserMemorySummary;
import org.neo4j.gds.procedures.memory.MemoryFacade;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Procedure;

import java.util.stream.Stream;

public class MemoryProc {
    private static final String DESCRIPTION = "List memory for currently running tasks or stored graphs.";

    @Context
    public MemoryFacade facade;

    @Procedure("gds.memory.list")
    @Description(DESCRIPTION)
    public Stream<UserEntityMemory> listMemory() {
        return facade.list();
    }

    @Procedure("gds.memory.summary")
    @Description(DESCRIPTION)
    public Stream<UserMemorySummary> summary() {
        return facade.memorySummary();
    }
}
