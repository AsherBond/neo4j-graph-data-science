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
package org.neo4j.gds.kmeans;

import org.neo4j.gds.core.concurrency.DefaultPool;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;

import java.util.concurrent.ExecutorService;

public record KmeansContext(ExecutorService executor, ProgressTracker progressTracker) {

    public static KmeansContext defaults() {
        return new KmeansContext(
            DefaultPool.INSTANCE,
            ProgressTracker.NULL_TRACKER
        );
    }

    public static KmeansContext progressTrackerWithDefaultExecutor(ProgressTracker progressTracker) {
        return new KmeansContext(
            DefaultPool.INSTANCE,
            progressTracker
        );
    }
}
