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
package org.neo4j.gds.core.utils.progress;

import org.jetbrains.annotations.TestOnly;
import org.neo4j.gds.core.utils.progress.tasks.Task;

public class TaskRegistry {

    private final String username;
    private final TaskStore taskStore;
    private final JobId jobId;

    @TestOnly
    public TaskRegistry(TaskRegistry taskRegistry) {
        this(taskRegistry.username, taskRegistry.taskStore);
    }

    public TaskRegistry(String username, TaskStore taskStore) {
        this.username = username;
        this.taskStore = taskStore;
        this.jobId = new JobId();
    }

    public TaskRegistry(String username, TaskStore taskStore, JobId jobId) {
        this.username = username;
        this.taskStore = taskStore;
        this.jobId = jobId;
    }

    public void registerTask(Task task) {
        taskStore.store(username, jobId, task);
    }

    public void markCompleted() {
        taskStore.markCompleted(username, jobId);
    }

    public boolean containsTask(Task task) {
        return taskStore.query(username, jobId)
            .map(t -> t.task() == task)
            .orElse(false);
    }
}
