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
package org.neo4j.gds.ml.linkmodels.logisticregression;

import org.neo4j.gds.api.Graph;
import org.neo4j.gds.core.utils.TerminationFlag;
import org.neo4j.gds.core.utils.paged.HugeLongArray;
import org.neo4j.gds.core.utils.paged.ReadOnlyHugeLongArray;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.ml.Training;
import org.neo4j.gds.ml.core.batch.BatchQueue;
import org.neo4j.gds.ml.core.batch.HugeBatchQueue;
import org.neo4j.gds.ml.core.features.FeatureExtractor;

import java.util.List;
import java.util.function.Supplier;

public class LinkLogisticRegressionTrain {

    private final Graph graph;
    private final ReadOnlyHugeLongArray trainSet;
    private final List<FeatureExtractor> extractors;
    private final LinkLogisticRegressionTrainConfig config;
    private final ProgressTracker progressTracker;
    private final TerminationFlag terminationFlag;
    private final int concurrency;

    public LinkLogisticRegressionTrain(
        Graph graph,
        HugeLongArray trainSet,
        List<FeatureExtractor> extractors,
        LinkLogisticRegressionTrainConfig config,
        ProgressTracker progressTracker,
        TerminationFlag terminationFlag,
        int concurrency
    ) {
        this.graph = graph;
        this.trainSet = ReadOnlyHugeLongArray.of(trainSet);
        this.extractors = extractors;
        this.config = config;
        this.progressTracker = progressTracker;
        this.terminationFlag = terminationFlag;
        this.concurrency = concurrency;
    }

    public LinkLogisticRegressionData compute() {
        var llrData = LinkLogisticRegressionData.from(
            graph,
            config.featureProperties(),
            LinkFeatureCombiners.valueOf(config.linkFeatureCombiner())
        );
        var objective = new LinkLogisticRegressionObjective(
            llrData,
            extractors,
            config.penalty(),
            graph
        );

        long trainSize = 0;
        for (long i = 0; i < trainSet.size(); i++) {
            trainSize += graph.degree(trainSet.get(i));
        }

        var training = new Training(config, progressTracker, trainSize, terminationFlag);
        Supplier<BatchQueue> queueSupplier = () -> new HugeBatchQueue(trainSet, config.batchSize());
        training.train(objective, queueSupplier, concurrency);
        return objective.modelData;
    }
}
