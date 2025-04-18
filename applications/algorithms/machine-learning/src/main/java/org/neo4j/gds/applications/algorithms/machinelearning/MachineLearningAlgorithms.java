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
package org.neo4j.gds.applications.algorithms.machinelearning;

import com.carrotsearch.hppc.BitSet;
import org.neo4j.gds.algorithms.machinelearning.KGEPredictBaseConfig;
import org.neo4j.gds.algorithms.machinelearning.KGEPredictConfigTransformer;
import org.neo4j.gds.algorithms.machinelearning.KGEPredictResult;
import org.neo4j.gds.algorithms.machinelearning.TopKMapComputer;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.applications.algorithms.machinery.AlgorithmLabel;
import org.neo4j.gds.applications.algorithms.machinery.AlgorithmMachinery;
import org.neo4j.gds.applications.algorithms.machinery.ProgressTrackerCreator;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.core.utils.progress.tasks.Tasks;
import org.neo4j.gds.ml.splitting.EdgeSplitter;
import org.neo4j.gds.ml.splitting.SplitRelationships;
import org.neo4j.gds.ml.splitting.SplitRelationshipsBaseConfig;
import org.neo4j.gds.termination.TerminationFlag;

public class MachineLearningAlgorithms {
    private final AlgorithmMachinery algorithmMachinery = new AlgorithmMachinery();

    private final ProgressTrackerCreator progressTrackerCreator;
    private final TerminationFlag terminationFlag;

    public MachineLearningAlgorithms(ProgressTrackerCreator progressTrackerCreator, TerminationFlag terminationFlag) {
        this.progressTrackerCreator = progressTrackerCreator;
        this.terminationFlag = terminationFlag;
    }

    KGEPredictResult kge(Graph graph, KGEPredictBaseConfig configuration) {
        var progressTracker = progressTrackerCreator.createProgressTracker(
            Tasks.leaf(AlgorithmLabel.KGE.asString()),
            configuration.jobId(),
            configuration.concurrency(),
            configuration.logProgress()
        );

        return kge(graph, configuration, progressTracker);
    }

    public KGEPredictResult kge(
        Graph graph,
        KGEPredictBaseConfig configuration,
        ProgressTracker progressTracker
    ) {
        var sourceNodes = new BitSet(graph.nodeCount());
        var targetNodes = new BitSet(graph.nodeCount());
        var parameters = KGEPredictConfigTransformer.toParameters(configuration);
        var sourceNodeFilter = parameters.sourceNodeFilter().toNodeFilter(graph);
        var targetNodeFilter = parameters.targetNodeFilter().toNodeFilter(graph);
        graph.forEachNode(node -> {
            if (sourceNodeFilter.test(node)) {
                sourceNodes.set(node);
            }
            if (targetNodeFilter.test(node)) {
                targetNodes.set(node);
            }
            return true;
        });
        var algorithm = new TopKMapComputer(
            graph,
            sourceNodes,
            targetNodes,
            parameters.nodeEmbeddingProperty(),
            parameters.relationshipTypeEmbedding(),
            parameters.scoringFunction(),
            parameters.topK(),
            parameters.concurrency(),
            progressTracker,
            terminationFlag
        );

        return algorithmMachinery.runAlgorithmsAndManageProgressTracker(
            algorithm,
            progressTracker,
            true,
            configuration.concurrency()
        );
    }

    EdgeSplitter.SplitResult splitRelationships(GraphStore graphStore, SplitRelationshipsBaseConfig configuration) {
        var algorithm = SplitRelationships.of(graphStore, configuration);

        return algorithm.compute();
    }
}
