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
package org.neo4j.gds.procedures.pipelines;

import org.neo4j.gds.api.User;
import org.neo4j.gds.ml.pipeline.PipelineCatalog;
import org.neo4j.gds.ml.pipeline.TrainingPipeline;
import org.neo4j.gds.ml.pipeline.linkPipeline.LinkPredictionTrainingPipeline;
import org.neo4j.gds.ml.pipeline.nodePipeline.classification.NodeClassificationTrainingPipeline;
import org.neo4j.gds.ml.pipeline.nodePipeline.regression.NodeRegressionTrainingPipeline;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * One day we can replace the big singleton with local state, and manage things better.
 * For now this is the groundwork, rolling out this half way house.
 */
public class PipelineRepository {
    LinkPredictionTrainingPipeline createLinkPredictionTrainingPipeline(User user, PipelineName pipelineName) {
        var pipeline = new LinkPredictionTrainingPipeline();

        PipelineCatalog.set(user.getUsername(), pipelineName.value(), pipeline);

        return pipeline;
    }

    NodeClassificationTrainingPipeline createNodeClassificationTrainingPipeline(User user, PipelineName pipelineName) {
        var pipeline = new NodeClassificationTrainingPipeline();

        registerPipeline(user, pipelineName, pipeline);

        return pipeline;
    }

    NodeRegressionTrainingPipeline createNodeRegressionTrainingPipeline(User user, PipelineName pipelineName) {
        var pipeline = new NodeRegressionTrainingPipeline();

        registerPipeline(user, pipelineName, pipeline);

        return pipeline;
    }

    /**
     * Underlying catalog throws exception if pipeline does not exist
     */
    TrainingPipeline<?> drop(User user, PipelineName pipelineName) {
        return PipelineCatalog.drop(user.getUsername(), pipelineName.value());
    }

    boolean exists(User user, PipelineName pipelineName) {
        return PipelineCatalog.exists(user.getUsername(), pipelineName.value());
    }

    Stream<PipelineCatalog.PipelineCatalogEntry> getAll(User user) {
        return PipelineCatalog.getAllPipelines(user.getUsername());
    }

    LinkPredictionTrainingPipeline getLinkPredictionTrainingPipeline(User user, PipelineName pipelineName) {
        return PipelineCatalog.getTyped(user.getUsername(), pipelineName.value(), LinkPredictionTrainingPipeline.class);
    }

    NodeClassificationTrainingPipeline getNodeClassificationTrainingPipeline(User user, PipelineName pipelineName) {
        return PipelineCatalog.getTyped(
            user.getUsername(),
            pipelineName.value(),
            NodeClassificationTrainingPipeline.class
        );
    }

    NodeRegressionTrainingPipeline getNodeRegressionTrainingPipeline(User user, PipelineName pipelineName) {
        return PipelineCatalog.getTyped(
            user.getUsername(),
            pipelineName.value(),
            NodeRegressionTrainingPipeline.class
        );
    }

    Optional<TrainingPipeline<?>> getSingle(User user, PipelineName pipelineName) {
        if (!exists(user, pipelineName)) return Optional.empty();

        var pipeline = get(user, pipelineName);

        return Optional.of(pipeline);
    }

    String getType(User user, PipelineName pipelineName) {
        var pipeline = get(user, pipelineName);

        return pipeline.type();
    }

    private TrainingPipeline<?> get(User user, PipelineName pipelineName) {
        return PipelineCatalog.get(user.getUsername(), pipelineName.value());
    }

    private void registerPipeline(
        User user,
        PipelineName pipelineName,
        TrainingPipeline<?> pipeline
    ) {
        PipelineCatalog.set(user.getUsername(), pipelineName.value(), pipeline);
    }
}
