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
package org.neo4j.gds.embeddings.graphsage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.neo4j.gds.NodeEmbeddingsAlgorithmTasks;
import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.Orientation;
import org.neo4j.gds.TestProgressTrackerHelper;
import org.neo4j.gds.api.DatabaseId;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.schema.Direction;
import org.neo4j.gds.applications.algorithms.embeddings.GraphSageModelCatalog;
import org.neo4j.gds.applications.algorithms.embeddings.NodeEmbeddingAlgorithms;
import org.neo4j.gds.beta.generator.PropertyProducer;
import org.neo4j.gds.beta.generator.RandomGraphGenerator;
import org.neo4j.gds.beta.generator.RelationshipDistribution;
import org.neo4j.gds.collections.ha.HugeObjectArray;
import org.neo4j.gds.config.RandomGraphGeneratorConfig;
import org.neo4j.gds.core.Aggregation;
import org.neo4j.gds.core.concurrency.Concurrency;
import org.neo4j.gds.core.concurrency.DefaultPool;
import org.neo4j.gds.core.huge.HugeGraph;
import org.neo4j.gds.core.loading.CSRGraphStoreUtil;
import org.neo4j.gds.core.loading.construction.NodeLabelTokens;
import org.neo4j.gds.core.model.InjectModelCatalog;
import org.neo4j.gds.core.model.ModelCatalog;
import org.neo4j.gds.core.model.ModelCatalogExtension;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.embeddings.graphsage.algo.ActivationFunctionType;
import org.neo4j.gds.embeddings.graphsage.algo.AggregatorType;
import org.neo4j.gds.embeddings.graphsage.algo.GraphSage;
import org.neo4j.gds.embeddings.graphsage.algo.GraphSageStreamConfigImpl;
import org.neo4j.gds.embeddings.graphsage.algo.GraphSageTrainConfigImpl;
import org.neo4j.gds.embeddings.graphsage.algo.SingleLabelGraphSageTrain;
import org.neo4j.gds.extension.GdlExtension;
import org.neo4j.gds.extension.GdlGraph;
import org.neo4j.gds.extension.Inject;
import org.neo4j.gds.termination.TerminatedException;
import org.neo4j.gds.termination.TerminationFlag;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.neo4j.gds.TestGdsVersion.testGdsVersion;
import static org.neo4j.gds.assertj.Extractors.removingThreadId;
import static org.neo4j.gds.compat.TestLog.INFO;

@GdlExtension
@ModelCatalogExtension
class GraphSageTest {

    @GdlGraph(orientation = Orientation.UNDIRECTED, graphNamePrefix = "orphan")
    private static final String ORPHAN_GRAPH = "CREATE " +
                                               "(a:P {f1: 0.0, f2: 0.0, f3: 0.0})" +
                                               ", (b:P {f1: 1.0, f2: 0.0, f3: 0.0})" +
                                               ", (c:P {f1: 1.0, f2: 0.0, f3: 0.0})" +
                                               ", (c)-[:T]->(c)";

    @Inject
    private Graph orphanGraph;

    @Inject
    private GraphStore orphanGraphStore;

    @InjectModelCatalog
    private ModelCatalog modelCatalog;

    private static final int NODE_COUNT = 20;
    private static final int FEATURES_COUNT = 1;
    private static final int EMBEDDING_DIMENSION = 64;
    private static final String MODEL_NAME = "graphSageModel";

    private Graph graph;
    private GraphStore graphStore;
    private HugeObjectArray<double[]> features;
    private GraphSageTrainConfigImpl.Builder configBuilder;

    @BeforeEach
    void setUp() {
        HugeGraph randomGraph = RandomGraphGenerator.builder()
            .nodeCount(NODE_COUNT)
            .averageDegree(3)
            .nodeLabelProducer(nodeId -> NodeLabelTokens.of("P"))
            .addNodePropertyProducer(NodeLabel.of("P"), PropertyProducer.randomDouble("f1", 0, 1))
            .relationshipDistribution(RelationshipDistribution.POWER_LAW)
            .relationshipPropertyProducer(PropertyProducer.fixedDouble("weight", 1.0))
            .seed(123L)
            .aggregation(Aggregation.SINGLE)
            .direction(Direction.UNDIRECTED)
            .allowSelfLoops(RandomGraphGeneratorConfig.AllowSelfLoops.NO)
            .build().generate();

        graph = randomGraph;

        long nodeCount = graph.nodeCount();

        graphStore = CSRGraphStoreUtil.createFromGraph(
            DatabaseId.random(),
            randomGraph,
            Optional.of("weight"),
            new Concurrency(4)
        );

        features = HugeObjectArray.newArray(double[].class, nodeCount);

        Random random = new Random();
        LongStream.range(0, nodeCount).forEach(n -> features.set(n, random.doubles(FEATURES_COUNT).toArray()));

        configBuilder = GraphSageTrainConfigImpl.builder().modelUser("").embeddingDimension(EMBEDDING_DIMENSION);
    }

    @ParameterizedTest
    @EnumSource
    void shouldNotMakeNanEmbeddings(AggregatorType aggregator) {
        var trainConfig = configBuilder
            .modelName(MODEL_NAME)
            .aggregator(aggregator)
            .activationFunction(ActivationFunctionType.RELU)
            .sampleSizes(List.of(75,25))
            .featureProperties(List.of("f1", "f2", "f3"))
            .concurrency(4)
            .build();

        var trainAlgo = new SingleLabelGraphSageTrain(
            orphanGraph,
            TrainConfigTransformer.toParameters(trainConfig),
            DefaultPool.INSTANCE,
            ProgressTracker.NULL_TRACKER,
            TerminationFlag.RUNNING_TRUE,
            testGdsVersion,
            trainConfig
        );
        var model = trainAlgo.compute();
        modelCatalog.set(model);

        var streamConfig = GraphSageStreamConfigImpl
            .builder()
            .modelUser("")
            .modelName(MODEL_NAME)
            .concurrency(4)
            .build();

        var graphSageModelCatalog = new GraphSageModelCatalog(modelCatalog);
        var nodeEmbeddingAlgorithms = new NodeEmbeddingAlgorithms(
            graphSageModelCatalog,
            TerminationFlag.RUNNING_TRUE
        );

        var result = nodeEmbeddingAlgorithms.graphSage(
            orphanGraph,
            streamConfig.toParameters(),
            ProgressTracker.NULL_TRACKER
        );

        for (int i = 0; i < orphanGraph.nodeCount() - 1; i++) {
            Arrays.stream(result.embeddings().get(i))
                .forEach(embeddingValue -> assertThat(embeddingValue).isNotNaN());
        }
    }

    @Test
    void differentTrainAndPredictionGraph() {
        var trainConfig = configBuilder
            .modelName(MODEL_NAME)
            .featureProperties(List.of("f1"))
            .relationshipWeightProperty("weight")
            .concurrency(1)
            .build();

        var graphSageTrain = new SingleLabelGraphSageTrain(
            graph,
            TrainConfigTransformer.toParameters(trainConfig),
            DefaultPool.INSTANCE,
            ProgressTracker.NULL_TRACKER,
            TerminationFlag.RUNNING_TRUE,
            testGdsVersion,
            trainConfig
        );
        var model = graphSageTrain.compute();


        int predictNodeCount = 2000;
        var trainGraph = RandomGraphGenerator.builder()
            .nodeCount(predictNodeCount)
            .averageDegree(3)
            .nodePropertyProducer(PropertyProducer.randomDouble("f1", 0D, 1D))
            .relationshipDistribution(RelationshipDistribution.POWER_LAW)
            .relationshipPropertyProducer(PropertyProducer.fixedDouble("weight", 1.0))
            .aggregation(Aggregation.SINGLE)
            .direction(Direction.UNDIRECTED)
            .allowSelfLoops(RandomGraphGeneratorConfig.AllowSelfLoops.NO)
            .build()
            .generate();

        var graphSage = new GraphSage(
            trainGraph,
            model,
            new Concurrency(4),
            2,
            DefaultPool.INSTANCE,
            ProgressTracker.NULL_TRACKER,
            TerminationFlag.RUNNING_TRUE
        );

        assertThat(graphSage.compute().embeddings().size()).isEqualTo(predictNodeCount);
    }

    @Test
    void testLogging() {

        var trainConfig = configBuilder
            .modelName(MODEL_NAME)
            .featureProperties(List.of("f1"))
            .relationshipWeightProperty("weight")
            .build();

        var graphSageTrain = new SingleLabelGraphSageTrain(
            graph,
            TrainConfigTransformer.toParameters(trainConfig),
            DefaultPool.INSTANCE,
            ProgressTracker.NULL_TRACKER,
            TerminationFlag.RUNNING_TRUE,
            testGdsVersion,
            trainConfig
        );

        var resultModel = graphSageTrain.compute();

        modelCatalog.set(resultModel);

        var progressTrackerWithLog = TestProgressTrackerHelper.create(
            new NodeEmbeddingsAlgorithmTasks().graphSage(graph),
            new Concurrency(4)
        );

        var progressTracker = progressTrackerWithLog.progressTracker();
        var log = progressTrackerWithLog.log();


        var graphSage = new GraphSage(
            graph,
            resultModel,
            new Concurrency(4),
            1,
            DefaultPool.INSTANCE,
            progressTracker,
            TerminationFlag.RUNNING_TRUE
        );
        graphSage.compute();

        var messagesInOrder = log.getMessages(INFO);

        assertThat(messagesInOrder)
            // avoid asserting on the thread id
            .extracting(removingThreadId())
            .containsExactly(
                "GraphSage :: Start",
                "GraphSage 5%",
                "GraphSage 10%",
                "GraphSage 15%",
                "GraphSage 20%",
                "GraphSage 25%",
                "GraphSage 30%",
                "GraphSage 35%",
                "GraphSage 40%",
                "GraphSage 45%",
                "GraphSage 50%",
                "GraphSage 55%",
                "GraphSage 60%",
                "GraphSage 65%",
                "GraphSage 70%",
                "GraphSage 75%",
                "GraphSage 80%",
                "GraphSage 85%",
                "GraphSage 90%",
                "GraphSage 95%",
                "GraphSage 100%",
                "GraphSage :: Finished"
            );
    }

    @Test
    void testTermination() {
        var terminationFlag = mock(TerminationFlag.class);

        var trainConfig = configBuilder
            .modelName(MODEL_NAME)
            .featureProperties(List.of("f1"))
            .relationshipWeightProperty("weight")
            .build();

        doNothing().when(terminationFlag).assertRunning();

        var resultModel = new SingleLabelGraphSageTrain(
            graph,
            TrainConfigTransformer.toParameters(trainConfig),
            DefaultPool.INSTANCE,
            ProgressTracker.NULL_TRACKER,
            terminationFlag,
            testGdsVersion,
            trainConfig
        ).compute();


        doThrow(new TerminatedException()).when(terminationFlag).assertRunning();

        var graphSage = new GraphSage(
            graph,
            resultModel,
            new Concurrency(4),
            1,
            DefaultPool.INSTANCE,
            ProgressTracker.NULL_TRACKER,
            terminationFlag
        );

        assertThatThrownBy(graphSage::compute)
            .isInstanceOf(TerminatedException.class)
            .hasMessageContaining("The execution has been terminated.");
    }
}
