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
package org.neo4j.gds.ml.linkmodels;

import org.neo4j.gds.AlgorithmFactory;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.core.utils.mem.AllocationTracker;
import org.neo4j.gds.core.utils.mem.MemoryEstimation;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.core.utils.progress.tasks.Task;
import org.neo4j.gds.core.utils.progress.tasks.Tasks;

public class LinkPredictionTrainFactory extends AlgorithmFactory<LinkPredictionTrain, LinkPredictionTrainConfig> {

    LinkPredictionTrainFactory() {
        super();
    }

    @Override
    protected String taskName() {
        return "LinkPredictionTrain";
    }

    @Override
    protected LinkPredictionTrain build(
        Graph graph, LinkPredictionTrainConfig configuration, AllocationTracker allocationTracker, ProgressTracker progressTracker
    ) {
        return new LinkPredictionTrain(graph, configuration, progressTracker);
    }

    @Override
    public MemoryEstimation memoryEstimation(LinkPredictionTrainConfig configuration) {
        return LinkPredictionTrainEstimation.estimate(configuration);
    }

    @Override
    public Task progressTask(
        Graph graph, LinkPredictionTrainConfig config
    ) {
        var modelSelectionTaskVolume = config.params().size() * config.validationFolds();
        return Tasks.task(
            taskName(),
            Tasks.leaf("ModelSelection", modelSelectionTaskVolume),
            Tasks.leaf("Training"),
            Tasks.task(
                "Evaluation",
                Tasks.leaf("Training"),
                Tasks.leaf("Testing")
            )
        );
    }
}
