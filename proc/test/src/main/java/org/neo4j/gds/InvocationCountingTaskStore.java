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

import org.jetbrains.annotations.TestOnly;
import org.neo4j.gds.core.utils.progress.JobId;
import org.neo4j.gds.core.utils.progress.PerDatabaseTaskStore;
import org.neo4j.gds.core.utils.progress.UserTask;
import org.neo4j.gds.core.utils.progress.tasks.Task;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@TestOnly
public class InvocationCountingTaskStore extends PerDatabaseTaskStore {
    public int registerTaskInvocations;
    public int removeTaskInvocations;
    public Set<JobId> seenJobIds = new HashSet<>();

    public InvocationCountingTaskStore(Duration retentionPeriod) {
        super(retentionPeriod);
    }

    @Override
    public UserTask storeUserTask(String username, JobId jobId, Task task) {
        registerTaskInvocations++;
        seenJobIds.add(jobId);
        return super.storeUserTask(username, jobId, task);
    }

    @Override
    public Optional<UserTask> removeUserTask(String username, JobId jobId) {
        removeTaskInvocations++;
        return super.removeUserTask(username, jobId);
    }
}
