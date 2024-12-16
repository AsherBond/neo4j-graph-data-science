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
package org.neo4j.gds.applications.algorithms.embeddings;

import org.neo4j.gds.api.Graph;
import org.neo4j.gds.applications.algorithms.machinery.AlgorithmLabel;
import org.neo4j.gds.applications.algorithms.machinery.AlgorithmMachinery;
import org.neo4j.gds.applications.algorithms.machinery.ProgressTrackerCreator;
import org.neo4j.gds.core.concurrency.DefaultPool;
import org.neo4j.gds.core.model.Model;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.core.utils.progress.tasks.Task;
import org.neo4j.gds.core.utils.progress.tasks.Tasks;
import org.neo4j.gds.degree.DegreeCentralityTask;
import org.neo4j.gds.embeddings.fastrp.FastRP;
import org.neo4j.gds.embeddings.fastrp.FastRPBaseConfig;
import org.neo4j.gds.embeddings.fastrp.FastRPConfigTransformer;
import org.neo4j.gds.embeddings.fastrp.FastRPResult;
import org.neo4j.gds.embeddings.graphsage.GraphSageModelTrainer;
import org.neo4j.gds.embeddings.graphsage.ModelData;
import org.neo4j.gds.embeddings.graphsage.algo.GraphSage;
import org.neo4j.gds.embeddings.graphsage.algo.GraphSageBaseConfig;
import org.neo4j.gds.embeddings.graphsage.algo.GraphSageResult;
import org.neo4j.gds.embeddings.graphsage.algo.GraphSageTrainConfig;
import org.neo4j.gds.embeddings.graphsage.algo.GraphSageTrainTask;
import org.neo4j.gds.embeddings.hashgnn.HashGNN;
import org.neo4j.gds.embeddings.hashgnn.HashGNNConfig;
import org.neo4j.gds.embeddings.hashgnn.HashGNNConfigTransformer;
import org.neo4j.gds.embeddings.hashgnn.HashGNNResult;
import org.neo4j.gds.embeddings.hashgnn.HashGNNTask;
import org.neo4j.gds.embeddings.node2vec.Node2Vec;
import org.neo4j.gds.embeddings.node2vec.Node2VecBaseConfig;
import org.neo4j.gds.embeddings.node2vec.Node2VecConfigTransformer;
import org.neo4j.gds.embeddings.node2vec.Node2VecResult;
import org.neo4j.gds.ml.core.features.FeatureExtraction;
import org.neo4j.gds.termination.TerminationFlag;

import java.util.ArrayList;
import java.util.List;

public class NodeEmbeddingAlgorithms {
    private static final GraphSageTrainAlgorithmFactory graphSageTrainAlgorithmFactory = new GraphSageTrainAlgorithmFactory();

    private final AlgorithmMachinery algorithmMachinery = new AlgorithmMachinery();

    private final GraphSageModelCatalog graphSageModelCatalog;

    private final ProgressTrackerCreator progressTrackerCreator;
    private final TerminationFlag terminationFlag;

    public NodeEmbeddingAlgorithms(
        GraphSageModelCatalog graphSageModelCatalog,
        ProgressTrackerCreator progressTrackerCreator,
        TerminationFlag terminationFlag
    ) {
        this.graphSageModelCatalog = graphSageModelCatalog;
        this.progressTrackerCreator = progressTrackerCreator;
        this.terminationFlag = terminationFlag;
    }

    public FastRPResult fastRP(Graph graph, FastRPBaseConfig configuration) {
        var task = createFastRPTask(graph, configuration.nodeSelfInfluence(), configuration.iterationWeights().size());
        var progressTracker = progressTrackerCreator.createProgressTracker(configuration, task);

        return fastRP(graph, configuration, progressTracker);
    }

    public FastRPResult fastRP(Graph graph, FastRPBaseConfig configuration, ProgressTracker progressTracker) {
        var parameters = FastRPConfigTransformer.toParameters(configuration);

        var featureExtractors = FeatureExtraction.propertyExtractors(graph, parameters.featureProperties());

        var algorithm = new FastRP(
            graph,
            parameters,
            configuration.concurrency(),
            10_000,
            featureExtractors,
            progressTracker,
            configuration.randomSeed(),
            terminationFlag
        );

        return algorithmMachinery.runAlgorithmsAndManageProgressTracker(
            algorithm,
            progressTracker,
            true,
            configuration.concurrency()
        );
    }

    public GraphSageResult graphSage(Graph graph, GraphSageBaseConfig configuration) {
        var task = Tasks.leaf(AlgorithmLabel.GraphSage.asString(), graph.nodeCount());
        var progressTracker = progressTrackerCreator.createProgressTracker(configuration, task);

        return graphSage(graph, configuration, progressTracker);
    }

    public GraphSageResult graphSage(Graph graph, GraphSageBaseConfig configuration, ProgressTracker progressTracker) {
        var model = graphSageModelCatalog.get(configuration);
        var parameters = configuration.toParameters();

        var algorithm = new GraphSage(
            graph,
            model,
            parameters.concurrency(),
            parameters.batchSize(),
            DefaultPool.INSTANCE,
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

    public Model<ModelData, GraphSageTrainConfig, GraphSageModelTrainer.GraphSageTrainMetrics> graphSageTrain(
        Graph graph,
        GraphSageTrainConfig configuration
    ) {
        var task = GraphSageTrainTask.create(graph, configuration);
        var progressTracker = progressTrackerCreator.createProgressTracker(configuration, task);

        return graphSageTrain(graph, configuration, progressTracker);
    }

    public Model<ModelData, GraphSageTrainConfig, GraphSageModelTrainer.GraphSageTrainMetrics> graphSageTrain(
        Graph graph,
        GraphSageTrainConfig configuration,
        ProgressTracker progressTracker
    ) {
        var algorithm = graphSageTrainAlgorithmFactory.create(graph, configuration, progressTracker, terminationFlag);

        return algorithmMachinery.runAlgorithmsAndManageProgressTracker(
            algorithm,
            progressTracker,
            true,
            configuration.concurrency()
        );
    }

    HashGNNResult hashGnn(Graph graph, HashGNNConfig configuration) {
        var task = HashGNNTask.create(graph, configuration);
        var progressTracker = progressTrackerCreator.createProgressTracker(configuration, task);

        return hashGnn(graph, configuration, progressTracker);
    }

    public HashGNNResult hashGnn(Graph graph, HashGNNConfig configuration, ProgressTracker progressTracker) {
        var parameters = HashGNNConfigTransformer.toParameters(configuration);

        var algorithm = new HashGNN(graph, parameters, progressTracker, terminationFlag);

        return algorithmMachinery.runAlgorithmsAndManageProgressTracker(
            algorithm,
            progressTracker,
            true,
            configuration.concurrency()
        );
    }

    Node2VecResult node2Vec(Graph graph, Node2VecBaseConfig configuration) {
        var task = createNode2VecTask(graph, configuration);
        var progressTracker = progressTrackerCreator.createProgressTracker(configuration, task);

        var algorithm = new Node2Vec(
            graph,
            configuration.concurrency(),
            configuration.sourceNodes(),
            configuration.randomSeed(),
            configuration.walkBufferSize(),
            Node2VecConfigTransformer.node2VecParameters(configuration),
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

    private Task createFastRPTask(Graph graph, Number nodeSelfInfluence, int iterationWeightsSize) {
        var tasks = new ArrayList<Task>();
        tasks.add(Tasks.leaf("Initialize random vectors", graph.nodeCount()));
        if (Float.compare(nodeSelfInfluence.floatValue(), 0.0f) != 0) {
            tasks.add(Tasks.leaf("Apply node self-influence", graph.nodeCount()));
        }
        tasks.add(Tasks.iterativeFixed(
            "Propagate embeddings",
            () -> List.of(Tasks.leaf("Propagate embeddings task", graph.relationshipCount())),
            iterationWeightsSize
        ));
        return Tasks.task(AlgorithmLabel.FastRP.asString(), tasks);
    }

    private Task createNode2VecTask(Graph graph, Node2VecBaseConfig configuration) {
        var randomWalkTasks = new ArrayList<Task>();
        if (graph.hasRelationshipProperty()) {
            randomWalkTasks.add(DegreeCentralityTask.create(graph));
        }
        randomWalkTasks.add(Tasks.leaf("create walks", graph.nodeCount()));

        return Tasks.task(
            AlgorithmLabel.Node2Vec.asString(),
            Tasks.task("RandomWalk", randomWalkTasks),
            Tasks.iterativeFixed(
                "train",
                () -> List.of(Tasks.leaf("iteration")),
                configuration.iterations()
            )
        );
    }
}
