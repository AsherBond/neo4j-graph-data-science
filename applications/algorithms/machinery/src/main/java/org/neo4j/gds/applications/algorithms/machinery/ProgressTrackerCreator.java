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
package org.neo4j.gds.applications.algorithms.machinery;

import org.neo4j.gds.core.concurrency.Concurrency;
import org.neo4j.gds.core.utils.progress.JobId;
import org.neo4j.gds.core.utils.progress.tasks.LoggerForProgressTracking;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.core.utils.progress.tasks.Task;
import org.neo4j.gds.core.utils.progress.tasks.TaskProgressTracker;
import org.neo4j.gds.core.utils.progress.tasks.TaskTreeProgressTracker;

/**
 * Just some convenience, address this one day when we attack ProgressTracker
 */
public class ProgressTrackerCreator {
    private final LoggerForProgressTracking log;
    private final RequestScopedDependencies requestScopedDependencies;

    public ProgressTrackerCreator(LoggerForProgressTracking log, RequestScopedDependencies requestScopedDependencies) {
        this.log = log;
        this.requestScopedDependencies = requestScopedDependencies;
    }

    public ProgressTracker createProgressTracker(
        Task task,
        JobId jobId,
        Concurrency concurrency,
        boolean logProgress
    ) {
        if (logProgress) {
            return new TaskProgressTracker(
                task,
                log,
                concurrency,
                jobId,
                requestScopedDependencies.taskRegistryFactory(),
                requestScopedDependencies.userLogRegistryFactory()
            );
        }

        return new TaskTreeProgressTracker(
            task,
            log,
            concurrency,
            jobId,
            requestScopedDependencies.taskRegistryFactory(),
            requestScopedDependencies.userLogRegistryFactory()
        );
    }
}
