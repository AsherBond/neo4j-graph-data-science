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
package org.neo4j.gds;

import org.neo4j.gds.compat.GraphDatabaseApiProxy;
import org.neo4j.gds.core.utils.progress.EmptyTaskRegistryFactory;
import org.neo4j.internal.kernel.api.procs.ProcedureCallContext;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.function.Consumer;

import static org.neo4j.gds.compat.GraphDatabaseApiProxy.newKernelTransaction;

public class ProcedureRunner {

    public static <P extends BaseProc> void applyOnProcedure(GraphDatabaseAPI graphDb, Class<P> procClass, Consumer<P> func) {
        try (GraphDatabaseApiProxy.Transactions transactions = newKernelTransaction(graphDb)) {
            P proc;
            try {
                proc = procClass.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Could not instantiate Procedure Class " + procClass.getSimpleName());
            }

            proc.procedureTransaction = transactions.tx();
            proc.transaction = transactions.ktx();
            proc.api = graphDb;
            proc.callContext = ProcedureCallContext.EMPTY;
            proc.log = new TestLog();
            proc.taskRegistryFactory = EmptyTaskRegistryFactory.INSTANCE;

            func.accept(proc);
        }
    }
}
