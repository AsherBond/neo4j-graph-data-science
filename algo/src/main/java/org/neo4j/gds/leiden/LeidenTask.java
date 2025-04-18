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
package org.neo4j.gds.leiden;

import org.neo4j.gds.api.IdMap;
import org.neo4j.gds.applications.algorithms.machinery.AlgorithmLabel;
import org.neo4j.gds.core.utils.progress.tasks.Task;
import org.neo4j.gds.core.utils.progress.tasks.Tasks;

import java.util.List;

public final class LeidenTask {
    private LeidenTask() {}

    public static Task create(IdMap idMap, LeidenParameters parameters) {
        var iterations = parameters.maxLevels();
        var iterativeTasks = Tasks.iterativeDynamic(
            "Iteration",
            () ->
                List.of(
                    Tasks.leaf("Local Move", 1),
                    Tasks.leaf("Modularity Computation", idMap.nodeCount()),
                    Tasks.leaf("Refinement", idMap.nodeCount()),
                    Tasks.leaf("Aggregation", idMap.nodeCount())
                ),
            iterations
        );
        var initializationTask = Tasks.leaf("Initialization", idMap.nodeCount());
        return Tasks.task(AlgorithmLabel.Leiden.asString(), initializationTask, iterativeTasks);
    }
}
