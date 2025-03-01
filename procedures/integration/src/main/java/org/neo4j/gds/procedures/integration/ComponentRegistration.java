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
package org.neo4j.gds.procedures.integration;

import org.neo4j.common.DependencySatisfier;
import org.neo4j.function.ThrowingFunction;
import org.neo4j.gds.logging.Log;
import org.neo4j.internal.kernel.api.exceptions.ProcedureException;
import org.neo4j.kernel.api.procedure.Context;
import org.neo4j.kernel.api.procedure.GlobalProcedures;

public class ComponentRegistration {
    private final Log log;
    private final DependencySatisfier dependencySatisfier;
    private final GlobalProcedures globalProcedures;

    public ComponentRegistration(Log log, DependencySatisfier dependencySatisfier, GlobalProcedures globalProcedures) {
        this.log = log;
        this.dependencySatisfier = dependencySatisfier;
        this.globalProcedures = globalProcedures;
    }

    public <T> void registerComponent(
        String name,
        Class<T> clazz,
        ThrowingFunction<Context, T, ProcedureException> provider
    ) {
        log.info("Register " + name + "...");
        globalProcedures.registerComponent(
            clazz,
            provider,
            true
        );
        log.info(name + " registered.");
    }

    void setUpDependency(Object component) {
        dependencySatisfier.satisfyDependency(component);
    }
}
