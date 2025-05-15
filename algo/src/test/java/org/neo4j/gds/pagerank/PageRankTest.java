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
package org.neo4j.gds.pagerank;

import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.gds.TestSupport;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.applications.algorithms.centrality.CentralityAlgorithms;
import org.neo4j.gds.applications.algorithms.centrality.CentralityBusinessAlgorithms;
import org.neo4j.gds.applications.algorithms.machinery.ProgressTrackerCreator;
import org.neo4j.gds.applications.algorithms.machinery.RequestScopedDependencies;
import org.neo4j.gds.beta.generator.RandomGraphGenerator;
import org.neo4j.gds.beta.generator.RelationshipDistribution;
import org.neo4j.gds.compat.TestLog;
import org.neo4j.gds.core.utils.logging.LoggerForProgressTrackingAdapter;
import org.neo4j.gds.core.utils.progress.EmptyTaskRegistryFactory;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.core.utils.warnings.EmptyUserLogRegistryFactory;
import org.neo4j.gds.extension.GdlExtension;
import org.neo4j.gds.extension.GdlGraph;
import org.neo4j.gds.extension.IdFunction;
import org.neo4j.gds.extension.Inject;
import org.neo4j.gds.extension.TestGraph;
import org.neo4j.gds.logging.GdsTestLog;
import org.neo4j.gds.scaling.ScalerFactory;
import org.neo4j.gds.termination.TerminationFlag;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.neo4j.gds.assertj.Extractors.removingThreadId;

@ExtendWith(SoftAssertionsExtension.class)
class PageRankTest {

    private static final double SCORE_PRECISION = 1E-5;

    @Nested
    @GdlExtension
    class Unweighted {

        // https://en.wikipedia.org/wiki/PageRank#/media/File:PageRanks-Example.jpg
        @GdlGraph
        private static final String DB_CYPHER =
            "CREATE" +
            "  (a:Node { expectedRank: 0.3040965, expectedPersonalizedRank1: 0.17053529152163158 , expectedPersonalizedRank2: 0.017454997930076894 , expectedBiasedPersonalizedRank: 0.0052365       })" +
            ", (b:Node { expectedRank: 3.5604297, expectedPersonalizedRank1: 0.3216114449911402  , expectedPersonalizedRank2: 0.813246950528992    , expectedBiasedPersonalizedRank: 1.30106015 })" +
            ", (c:Node { expectedRank: 3.1757906, expectedPersonalizedRank1: 0.27329311398643763 , expectedPersonalizedRank2: 0.690991752640184    , expectedBiasedPersonalizedRank: 1.105901 })" +
            ", (d:Node { expectedRank: 0.3625935, expectedPersonalizedRank1: 0.048318333106500536, expectedPersonalizedRank2: 0.041070583050331164 , expectedBiasedPersonalizedRank: 0.01232117 })" +
            ", (e:Node { expectedRank: 0.7503465, expectedPersonalizedRank1: 0.17053529152163158 , expectedPersonalizedRank2: 0.1449550029964717   , expectedBiasedPersonalizedRank: 0.0434865})" +
            ", (f:Node { expectedRank: 0.3625935, expectedPersonalizedRank1: 0.048318333106500536, expectedPersonalizedRank2: 0.041070583050331164 , expectedBiasedPersonalizedRank: 0.10232117})" +
            ", (g:Node { expectedRank: 0.15     , expectedPersonalizedRank1: 0.0                 , expectedPersonalizedRank2: 0.0                  , expectedBiasedPersonalizedRank: 0.0})" +
            ", (h:Node { expectedRank: 0.15     , expectedPersonalizedRank1: 0.0                 , expectedPersonalizedRank2: 0.0                  , expectedBiasedPersonalizedRank: 0.0})" +
            ", (i:Node { expectedRank: 0.15     , expectedPersonalizedRank1: 0.0                 , expectedPersonalizedRank2: 0.0                  , expectedBiasedPersonalizedRank: 0.0})" +
            ", (j:Node { expectedRank: 0.15     , expectedPersonalizedRank1: 0.0                 , expectedPersonalizedRank2: 0.0                  , expectedBiasedPersonalizedRank: 0.0})" +
            ", (k:Node { expectedRank: 0.15     , expectedPersonalizedRank1: 0.0                 , expectedPersonalizedRank2: 0.15000000000000002  , expectedBiasedPersonalizedRank: 0.0})" +
            ", (b)-[:TYPE]->(c)" +
            ", (c)-[:TYPE]->(b)" +
            ", (d)-[:TYPE]->(a)" +
            ", (d)-[:TYPE]->(b)" +
            ", (e)-[:TYPE]->(b)" +
            ", (e)-[:TYPE]->(d)" +
            ", (e)-[:TYPE]->(f)" +
            ", (f)-[:TYPE]->(b)" +
            ", (f)-[:TYPE]->(e)" +
            ", (g)-[:TYPE]->(b)" +
            ", (g)-[:TYPE]->(e)" +
            ", (h)-[:TYPE]->(b)" +
            ", (h)-[:TYPE]->(e)" +
            ", (i)-[:TYPE]->(b)" +
            ", (i)-[:TYPE]->(e)" +
            ", (j)-[:TYPE]->(e)" +
            ", (k)-[:TYPE]->(e)";

        @Inject
        private TestGraph graph;

        @Test
        void withoutTolerance() {
            var config = PageRankStreamConfigImpl.builder()
                .maxIterations(41)
                .concurrency(1)
                .tolerance(0)
                .build();

            var centralityAlgorithms = new CentralityAlgorithms(null, TerminationFlag.RUNNING_TRUE);
            var result = centralityAlgorithms.pageRank(graph, config, ProgressTracker.NULL_TRACKER);
            var rankProvider = result.centralityScoreProvider();

            var expected = graph.nodeProperties("expectedRank");

            for (int nodeId = 0; nodeId < graph.nodeCount(); nodeId++) {
                assertThat(rankProvider.applyAsDouble(nodeId)).isEqualTo(
                    expected.doubleValue(nodeId),
                    within(SCORE_PRECISION)
                );
            }
        }

        @ParameterizedTest
        @CsvSource(value = {"0.5, 2", "0.1, 13"})
        void withTolerance(double tolerance, int expectedIterations) {
            var config = PageRankStreamConfigImpl.builder()
                .maxIterations(40)
                .concurrency(1)
                .tolerance(tolerance)
                .build();

            var centralityAlgorithms = new CentralityAlgorithms(null, TerminationFlag.RUNNING_TRUE);

            var pregelResult = centralityAlgorithms.pageRank(graph, config, ProgressTracker.NULL_TRACKER);

            // initial iteration is counted extra in Pregel
            assertThat(pregelResult.iterations()).isEqualTo(expectedIterations);
        }

        @ParameterizedTest
        @CsvSource(value = {
            "a;e,expectedPersonalizedRank1",
            "k;b,expectedPersonalizedRank2"
        })
        void withSourceNodes(String sourceNodesString, String expectedPropertyKey) {
            // ids are converted to mapped ids within the algorithms
            var sourceNodeIds = Arrays.stream(sourceNodesString.split(";"))
                .map(graph::toOriginalNodeId)
                .collect(Collectors.toList());

            var config = PageRankConfigImpl.builder()
                .maxIterations(41)
                .tolerance(0)
                .concurrency(1)
                .sourceNodes(sourceNodeIds)
                .build();

            var centralityAlgorithms = new CentralityAlgorithms(null, TerminationFlag.RUNNING_TRUE);
            var result = centralityAlgorithms.pageRank(graph, config, ProgressTracker.NULL_TRACKER);
            var rankProvider = result.centralityScoreProvider();

            var expected = graph.nodeProperties(expectedPropertyKey);

            for (int nodeId = 0; nodeId < graph.nodeCount(); nodeId++) {
                assertThat(rankProvider.applyAsDouble(nodeId)).isEqualTo(
                    expected.doubleValue(nodeId),
                    within(SCORE_PRECISION)
                );
            }
        }

        //Alternatively compare with biased sum of singleSourceNode results
        @Test
        void withBiasedSourceNodes() {

            BiFunction<String, Double, List<?>> entry = (a,b) -> List.of(graph.toOriginalNodeId(a),b);
            var sourceNodesMap = List.of(
                    entry.apply("b",2d),
                    entry.apply("f",0.6d)
                );

            var config = PageRankConfigImpl.builder()
                .maxIterations(100)
                .tolerance(0)
                .concurrency(1)
                .sourceNodes(sourceNodesMap)
                .build();

            var centralityAlgorithms = new CentralityAlgorithms(null, TerminationFlag.RUNNING_TRUE);
            var result = centralityAlgorithms.pageRank(graph, config, ProgressTracker.NULL_TRACKER);
            var rankProvider = result.centralityScoreProvider();

            var expected = graph.nodeProperties("expectedBiasedPersonalizedRank");

            for (int nodeId = 0; nodeId < graph.nodeCount(); nodeId++) {
                assertThat(rankProvider.applyAsDouble(nodeId)).isEqualTo(
                    expected.doubleValue(nodeId),
                    within(SCORE_PRECISION)
                );
            }
        }

        @Test
        void shouldLogProgress() {
            var log = new GdsTestLog();
            var requestScopedDependencies = RequestScopedDependencies.builder()
                .taskRegistryFactory(EmptyTaskRegistryFactory.INSTANCE)
                .userLogRegistryFactory(EmptyUserLogRegistryFactory.INSTANCE)
                .build();
            var progressTrackerCreator = new ProgressTrackerCreator(new LoggerForProgressTrackingAdapter(log), requestScopedDependencies);
            var centralityAlgorithms = new CentralityAlgorithms(progressTrackerCreator, TerminationFlag.RUNNING_TRUE);
            var businessAlgorithms = new CentralityBusinessAlgorithms(
                centralityAlgorithms,
                progressTrackerCreator
            );

            var maxIterations = 10;
            var config = PageRankConfigImpl.builder().maxIterations(maxIterations).build();
            businessAlgorithms.pageRank(graph, config);

            assertThat(log.getMessages(TestLog.INFO))
                .extracting(removingThreadId())
                .contains(
                    "PageRank :: Start",
                    "PageRank :: Compute iteration 1 of 10 :: Start",
                    "PageRank :: Compute iteration 1 of 10 100%",
                    "PageRank :: Compute iteration 1 of 10 :: Finished",
                    "PageRank :: Master compute iteration 1 of 10 :: Start",
                    "PageRank :: Master compute iteration 1 of 10 100%",
                    "PageRank :: Master compute iteration 1 of 10 :: Finished",
                    "PageRank :: Compute iteration 2 of 10 :: Start",
                    "PageRank :: Compute iteration 2 of 10 100%",
                    "PageRank :: Compute iteration 2 of 10 :: Finished",
                    "PageRank :: Master compute iteration 2 of 10 :: Start",
                    "PageRank :: Master compute iteration 2 of 10 100%",
                    "PageRank :: Master compute iteration 2 of 10 :: Finished",
                    "PageRank :: Compute iteration 3 of 10 :: Start",
                    "PageRank :: Compute iteration 3 of 10 100%",
                    "PageRank :: Compute iteration 3 of 10 :: Finished",
                    "PageRank :: Master compute iteration 3 of 10 :: Start",
                    "PageRank :: Master compute iteration 3 of 10 100%",
                    "PageRank :: Master compute iteration 3 of 10 :: Finished",
                    "PageRank :: Compute iteration 4 of 10 :: Start",
                    "PageRank :: Compute iteration 4 of 10 100%",
                    "PageRank :: Compute iteration 4 of 10 :: Finished",
                    "PageRank :: Master compute iteration 4 of 10 :: Start",
                    "PageRank :: Master compute iteration 4 of 10 100%",
                    "PageRank :: Master compute iteration 4 of 10 :: Finished",
                    "PageRank :: Compute iteration 5 of 10 :: Start",
                    "PageRank :: Compute iteration 5 of 10 100%",
                    "PageRank :: Compute iteration 5 of 10 :: Finished",
                    "PageRank :: Master compute iteration 5 of 10 :: Start",
                    "PageRank :: Master compute iteration 5 of 10 100%",
                    "PageRank :: Master compute iteration 5 of 10 :: Finished",
                    "PageRank :: Compute iteration 6 of 10 :: Start",
                    "PageRank :: Compute iteration 6 of 10 100%",
                    "PageRank :: Compute iteration 6 of 10 :: Finished",
                    "PageRank :: Master compute iteration 6 of 10 :: Start",
                    "PageRank :: Master compute iteration 6 of 10 100%",
                    "PageRank :: Master compute iteration 6 of 10 :: Finished",
                    "PageRank :: Compute iteration 7 of 10 :: Start",
                    "PageRank :: Compute iteration 7 of 10 100%",
                    "PageRank :: Compute iteration 7 of 10 :: Finished",
                    "PageRank :: Master compute iteration 7 of 10 :: Start",
                    "PageRank :: Master compute iteration 7 of 10 100%",
                    "PageRank :: Master compute iteration 7 of 10 :: Finished",
                    "PageRank :: Compute iteration 8 of 10 :: Start",
                    "PageRank :: Compute iteration 8 of 10 100%",
                    "PageRank :: Compute iteration 8 of 10 :: Finished",
                    "PageRank :: Master compute iteration 8 of 10 :: Start",
                    "PageRank :: Master compute iteration 8 of 10 100%",
                    "PageRank :: Master compute iteration 8 of 10 :: Finished",
                    "PageRank :: Compute iteration 9 of 10 :: Start",
                    "PageRank :: Compute iteration 9 of 10 100%",
                    "PageRank :: Compute iteration 9 of 10 :: Finished",
                    "PageRank :: Master compute iteration 9 of 10 :: Start",
                    "PageRank :: Master compute iteration 9 of 10 100%",
                    "PageRank :: Master compute iteration 9 of 10 :: Finished",
                    "PageRank :: Compute iteration 10 of 10 :: Start",
                    "PageRank :: Compute iteration 10 of 10 100%",
                    "PageRank :: Compute iteration 10 of 10 :: Finished",
                    "PageRank :: Master compute iteration 10 of 10 :: Start",
                    "PageRank :: Master compute iteration 10 of 10 100%",
                    "PageRank :: Master compute iteration 10 of 10 :: Finished",
                    "PageRank :: Finished"
                );
        }

        @Test
        void checkTerminationFlag() {
            var centralityAlgorithms = new CentralityAlgorithms(null, TerminationFlag.STOP_RUNNING);

            var config = PageRankStreamConfigImpl.builder()
                .maxIterations(40)
                .concurrency(1)
                .build();

            TestSupport.assertTransactionTermination(() -> centralityAlgorithms.pageRank(graph, config, ProgressTracker.NULL_TRACKER));
        }
    }

    @Nested
    @GdlExtension
    class Weighted {
        // https://en.wikipedia.org/wiki/PageRank#/media/File:PageRanks-Example.jpg
        @GdlGraph
        private static final String DB_CYPHER =
            "CREATE" +
            "  (a:Node { expectedRank: 0.24919, expectedBiasedPersonalizedRank: 0.02277154 })" +
            ", (b:Node { expectedRank: 3.69822, expectedBiasedPersonalizedRank: 1.15153359 })" +
            ", (c:Node { expectedRank: 3.29307, expectedBiasedPersonalizedRank: 0.97880374 })" +
            ", (d:Node { expectedRank: 0.58349, expectedBiasedPersonalizedRank: 0.13395024 })" +
            ", (e:Node { expectedRank: 0.72855, expectedBiasedPersonalizedRank: 0.19991637 })" +
            ", (f:Node { expectedRank: 0.27385, expectedBiasedPersonalizedRank: 0.03398578 })" +
            ", (g:Node { expectedRank: 0.15,    expectedBiasedPersonalizedRank: 0.0 })" +
            ", (h:Node { expectedRank: 0.15,    expectedBiasedPersonalizedRank: 0.0 })" +
            ", (i:Node { expectedRank: 0.15,    expectedBiasedPersonalizedRank: 0.45 })" +
            ", (j:Node { expectedRank: 0.15,    expectedBiasedPersonalizedRank: 0.0 })" +
            ", (k:Node { expectedRank: 0.15,    expectedBiasedPersonalizedRank: 0.0 })" +
            ", (b)-[:TYPE { weight: 1.0,   unnormalizedWeight: 5.0 }]->(c)" +
            ", (c)-[:TYPE { weight: 1.0,   unnormalizedWeight: 10.0 }]->(b)" +
            ", (d)-[:TYPE { weight: 0.2,   unnormalizedWeight: 2.0 }]->(a)" +
            ", (d)-[:TYPE { weight: 0.8,   unnormalizedWeight: 8.0 }]->(b)" +
            ", (e)-[:TYPE { weight: 0.10,  unnormalizedWeight: 1.0 }]->(b)" +
            ", (e)-[:TYPE { weight: 0.70,  unnormalizedWeight: 7.0 }]->(d)" +
            ", (e)-[:TYPE { weight: 0.20,  unnormalizedWeight: 2.0 }]->(f)" +
            ", (f)-[:TYPE { weight: 0.7,   unnormalizedWeight: 7.0 }]->(b)" +
            ", (f)-[:TYPE { weight: 0.3,   unnormalizedWeight: 3.0 }]->(e)" +
            ", (g)-[:TYPE { weight: 0.01,  unnormalizedWeight: 0.1 }]->(b)" +
            ", (g)-[:TYPE { weight: 0.99,  unnormalizedWeight: 9.9 }]->(e)" +
            ", (h)-[:TYPE { weight: 0.5,   unnormalizedWeight: 5.0 }]->(b)" +
            ", (h)-[:TYPE { weight: 0.5,   unnormalizedWeight: 5.0 }]->(e)" +
            ", (i)-[:TYPE { weight: 0.5,   unnormalizedWeight: 5.0 }]->(b)" +
            ", (i)-[:TYPE { weight: 0.5,   unnormalizedWeight: 5.0 }]->(e)" +
            ", (j)-[:TYPE { weight: 1.0,   unnormalizedWeight: 10.0 }]->(e)" +
            ", (k)-[:TYPE { weight: 1.0,   unnormalizedWeight: 10.0 }]->(e)";

        @GdlGraph(graphNamePrefix = "zeroWeights")
        private static final String DB_ZERO_WEIGHTS =
            "CREATE" +
            "  (a:Node { expectedRank: 0.15 })" +
            ", (b:Node { expectedRank: 0.15 })" +
            ", (c:Node { expectedRank: 0.15 })" +
            ", (d:Node { expectedRank: 0.15 })" +
            ", (e:Node { expectedRank: 0.15 })" +
            ", (f:Node { expectedRank: 0.15 })" +
            ", (g:Node { expectedRank: 0.15 })" +
            ", (h:Node { expectedRank: 0.15 })" +
            ", (i:Node { expectedRank: 0.15 })" +
            ", (j:Node { expectedRank: 0.15 })" +
            ", (b)-[:TYPE1 {weight: 0}]->(c)" +
            ", (c)-[:TYPE1 {weight: 0}]->(b)" +
            ", (d)-[:TYPE1 {weight: 0}]->(a)" +
            ", (d)-[:TYPE1 {weight: 0}]->(b)" +
            ", (e)-[:TYPE1 {weight: 0}]->(b)" +
            ", (e)-[:TYPE1 {weight: 0}]->(d)" +
            ", (e)-[:TYPE1 {weight: 0}]->(f)" +
            ", (f)-[:TYPE1 {weight: 0}]->(b)" +
            ", (f)-[:TYPE1 {weight: 0}]->(e)";

        @Inject
        private TestGraph graph;

        @Inject
        private Graph zeroWeightsGraph;

        @ParameterizedTest
        @ValueSource(strings = {"weight", "unnormalizedWeight"})
        void withWeights(String relationshipWeight) {
            var config = PageRankStreamConfigImpl.builder()
                .maxIterations(41)
                .tolerance(0)
                .relationshipWeightProperty(relationshipWeight)
                .concurrency(1)
                .build();

            var centralityAlgorithms = new CentralityAlgorithms(null, TerminationFlag.RUNNING_TRUE);
            var result = centralityAlgorithms.pageRank(graph, config, ProgressTracker.NULL_TRACKER);
            var rankProvider = result.centralityScoreProvider();

            var expected = graph.nodeProperties("expectedRank");

            for (int nodeId = 0; nodeId < graph.nodeCount(); nodeId++) {
                assertThat(rankProvider.applyAsDouble(nodeId)).isEqualTo(
                    expected.doubleValue(nodeId),
                    within(SCORE_PRECISION)
                );
            }
        }

        @Test
        void withZeroWeights() {
            var config = PageRankStreamConfigImpl.builder()
                .maxIterations(40)
                .tolerance(0)
                .relationshipWeightProperty("weight")
                .concurrency(1)
                .build();

            var centralityAlgorithms = new CentralityAlgorithms(null, TerminationFlag.RUNNING_TRUE);
            var result = centralityAlgorithms.pageRank(zeroWeightsGraph, config, ProgressTracker.NULL_TRACKER);
            var rankProvider = result.centralityScoreProvider();

            var expected = zeroWeightsGraph.nodeProperties("expectedRank");

            for (int nodeId = 0; nodeId < zeroWeightsGraph.nodeCount(); nodeId++) {
                assertThat(rankProvider.applyAsDouble(nodeId)).isEqualTo(
                    expected.doubleValue(nodeId),
                    within(SCORE_PRECISION)
                );
            }
        }

        @Test
        void withWeightsAndBiasedSourceNodes() {

            BiFunction<String, Double, List<?>> entry = (a,b) -> List.of(graph.toOriginalNodeId(a),b);
            var sourceNodesMap = List.of(
                entry.apply("d",0.1d),
                entry.apply("i",3d)
            );

            var config = PageRankConfigImpl.builder()
                .maxIterations(100)
                .tolerance(0)
                .relationshipWeightProperty("weight")
                .concurrency(1)
                .sourceNodes(sourceNodesMap)
                .build();

            var centralityAlgorithms = new CentralityAlgorithms(null, TerminationFlag.RUNNING_TRUE);
            var result = centralityAlgorithms.pageRank(graph, config, ProgressTracker.NULL_TRACKER);
            var rankProvider = result.centralityScoreProvider();

            var expected = graph.nodeProperties("expectedBiasedPersonalizedRank");

            for (int nodeId = 0; nodeId < graph.nodeCount(); nodeId++) {
                assertThat(rankProvider.applyAsDouble(nodeId)).isEqualTo(
                    expected.doubleValue(nodeId),
                    within(SCORE_PRECISION)
                );
            }
        }
    }

    @Nested
    @GdlExtension
    class ArticleRank {

        @GdlGraph
        private static final String DB_CYPHER =
            "CREATE" +
            "  (a:Node { expectedRank: 0.20720 , expectedBiasedPersonalizedRank: 0.00614168 })" +
            ", (b:Node { expectedRank: 0.47091 , expectedBiasedPersonalizedRank: 0.41661876  })" +
            ", (c:Node { expectedRank: 0.36067 , expectedBiasedPersonalizedRank: 0.18638208 })" +
            ", (d:Node { expectedRank: 0.19515 , expectedBiasedPersonalizedRank: 0.02095396   })" +
            ", (e:Node { expectedRank: 0.20720 , expectedBiasedPersonalizedRank: 0.09614168 })" +
            ", (f:Node { expectedRank: 0.19515 , expectedBiasedPersonalizedRank: 0.02095396   })" +
            ", (g:Node { expectedRank: 0.15 ,    expectedBiasedPersonalizedRank: 0.0        })" +
            ", (h:Node { expectedRank: 0.15 ,    expectedBiasedPersonalizedRank: 0.0        })" +
            ", (i:Node { expectedRank: 0.15 ,    expectedBiasedPersonalizedRank: 0.0        })" +
            ", (j:Node { expectedRank: 0.15 ,    expectedBiasedPersonalizedRank: 0.0        })" +
            ", (b)-[:TYPE]->(c)" +
            ", (c)-[:TYPE]->(b)" +
            ", (d)-[:TYPE]->(a)" +
            ", (d)-[:TYPE]->(b)" +
            ", (e)-[:TYPE]->(b)" +
            ", (e)-[:TYPE]->(d)" +
            ", (e)-[:TYPE]->(f)" +
            ", (f)-[:TYPE]->(b)" +
            ", (f)-[:TYPE]->(e)";

        @GdlGraph(graphNamePrefix = "paper")
        public static final String DB_PAPERS =
            "CREATE" +
            "  (a:Node { expectedRank: 0.34627 })" +
            ", (b:Node { expectedRank: 0.31950 })" +
            ", (c:Node { expectedRank: 0.21092 })" +
            ", (d:Node { expectedRank: 0.18028 })" +
            ", (e:Node { expectedRank: 0.21375 })" +
            ", (f:Node { expectedRank: 0.15000 })" +
            ", (g:Node { expectedRank: 0.15000 })" +
            ", (b)-[:TYPE]->(a)" +
            ", (c)-[:TYPE]->(a)" +
            ", (c)-[:TYPE]->(b)" +
            ", (d)-[:TYPE]->(a)" +
            ", (d)-[:TYPE]->(b)" +
            ", (d)-[:TYPE]->(c)" +
            ", (e)-[:TYPE]->(a)" +
            ", (e)-[:TYPE]->(b)" +
            ", (e)-[:TYPE]->(c)" +
            ", (e)-[:TYPE]->(d)" +
            ", (f)-[:TYPE]->(b)" +
            ", (f)-[:TYPE]->(e)" +
            ", (g)-[:TYPE]->(b)" +
            ", (g)-[:TYPE]->(e)";

        @Inject
        private TestGraph graph;

        @Inject
        private Graph paperGraph;

        @Test
        void articleRank(SoftAssertions softly) {
            var config = ArticleRankStreamConfigImpl
                .builder()
                .maxIterations(40)
                .tolerance(0)
                .concurrency(1)
                .build();

            var centralityAlgorithms = new CentralityAlgorithms(null, TerminationFlag.RUNNING_TRUE);

            var result = centralityAlgorithms.articleRank(graph, config, ProgressTracker.NULL_TRACKER);

            var rankProvider = result.centralityScoreProvider();

            var expected = graph.nodeProperties("expectedRank");

            for (int nodeId = 0; nodeId < graph.nodeCount(); nodeId++) {
                softly.assertThat(rankProvider.applyAsDouble(nodeId))
                    .isEqualTo(expected.doubleValue(nodeId), within(SCORE_PRECISION));
            }
        }

        @Test
        void articleRankOnPaperGraphTest(SoftAssertions softly) {
            var config = ArticleRankStreamConfigImpl
                .builder()
                .maxIterations(20)
                .tolerance(0)
                .dampingFactor(0.85)
                .concurrency(1)
                .build();

            var centralityAlgorithms = new CentralityAlgorithms(null, TerminationFlag.RUNNING_TRUE);

            var result = centralityAlgorithms.articleRank(paperGraph, config, ProgressTracker.NULL_TRACKER);

            var rankProvider = result.centralityScoreProvider();

            var expected = paperGraph.nodeProperties("expectedRank");

            for (int nodeId = 0; nodeId < paperGraph.nodeCount(); nodeId++) {
                softly.assertThat(rankProvider.applyAsDouble(nodeId))
                    .isEqualTo(expected.doubleValue(nodeId), within(SCORE_PRECISION));
            }
        }

        @Test
        void withBiasedSourceNodes() {
            BiFunction<String, Double, List<?>> entry = (a,b) -> List.of(graph.toOriginalNodeId(a),b);
            var sourceNodesMap = List.of(
                entry.apply("b",2d),
                entry.apply("e",0.6d)
            );
            var config = ArticleRankStreamConfigImpl.builder()
                .maxIterations(100)
                .tolerance(0)
                .concurrency(1)
                .sourceNodes(sourceNodesMap)
                .build();

            var centralityAlgorithms = new CentralityAlgorithms(null, TerminationFlag.RUNNING_TRUE);

            var result = centralityAlgorithms.articleRank(graph, config, ProgressTracker.NULL_TRACKER);

            var rankProvider = result.centralityScoreProvider();

            var expected = graph.nodeProperties("expectedBiasedPersonalizedRank");

            for (int nodeId = 0; nodeId < graph.nodeCount(); nodeId++) {
                assertThat(rankProvider.applyAsDouble(nodeId)).isEqualTo(
                    expected.doubleValue(nodeId),
                    within(SCORE_PRECISION)
                );
            }
        }
    }

    @Nested
    @GdlExtension
    class Eigenvector {

        @GdlGraph
        private static final String DB_CYPHER =
            "CREATE" +
            "  (a:Node { expectedRank: 0.01262, expectedWeightedRank: 0.00210, expectedPersonalizedRank:  0.00997 })" +
            ", (b:Node { expectedRank: 0.71623, expectedWeightedRank: 0.70774, expectedPersonalizedRank:  0.70735 })" +
            ", (c:Node { expectedRank: 0.69740, expectedWeightedRank: 0.70645, expectedPersonalizedRank:  0.70678 })" +
            ", (d:Node { expectedRank: 0.01262, expectedWeightedRank: 0.00172, expectedPersonalizedRank:  0.00056 })" +
            ", (e:Node { expectedRank: 0.01262, expectedWeightedRank: 0.00210, expectedPersonalizedRank:  0.0     })" +
            ", (f:Node { expectedRank: 0.01262, expectedWeightedRank: 0.00172, expectedPersonalizedRank:  0.0     })" +
            ", (g:Node { expectedRank: 0.0    , expectedWeightedRank: 0.0    , expectedPersonalizedRank:  0.0     })" +
            ", (h:Node { expectedRank: 0.0    , expectedWeightedRank: 0.0    , expectedPersonalizedRank:  0.0     })" +
            ", (i:Node { expectedRank: 0.0    , expectedWeightedRank: 0.0    , expectedPersonalizedRank:  0.0     })" +
            ", (j:Node { expectedRank: 0.0    , expectedWeightedRank: 0.0    , expectedPersonalizedRank:  0.0     })" +
            ", (b)-[:TYPE { weight: 1.0 } ]->(c)" +
            ", (c)-[:TYPE { weight: 3.0 } ]->(b)" +
            ", (d)-[:TYPE { weight: 5.0 } ]->(a)" +
            ", (d)-[:TYPE { weight: 5.0 } ]->(b)" +
            ", (e)-[:TYPE { weight: 4.0 } ]->(b)" +
            ", (e)-[:TYPE { weight: 4.0 } ]->(d)" +
            ", (e)-[:TYPE { weight: 4.0 } ]->(f)" +
            ", (f)-[:TYPE { weight: 10.0 } ]->(b)" +
            ", (f)-[:TYPE { weight: 10.0 } ]->(e)";

        @Inject
        private Graph graph;

        @Inject
        private IdFunction idFunction;

        @Test
        void eigenvector() {
            var config = EigenvectorStreamConfigImpl
                .builder()
                .maxIterations(40)
                .tolerance(0)
                .concurrency(1)
                .build();

            var centralityAlgorithms = new CentralityAlgorithms(null, TerminationFlag.RUNNING_TRUE);

            var result = centralityAlgorithms.eigenVector(graph, config, ProgressTracker.NULL_TRACKER);

            var rankProvider = result.centralityScoreProvider();

            var expected = graph.nodeProperties("expectedRank");

            for (int nodeId = 0; nodeId < graph.nodeCount(); nodeId++) {
                assertThat(rankProvider.applyAsDouble(nodeId)).isEqualTo(
                    expected.doubleValue(nodeId),
                    within(SCORE_PRECISION)
                );
            }
        }

        @Test
        void weighted() {
            var config = EigenvectorStreamConfigImpl
                .builder()
                .relationshipWeightProperty("weight")
                .maxIterations(10)
                .tolerance(0)
                .concurrency(1)
                .build();

            var centralityAlgorithms = new CentralityAlgorithms(null, TerminationFlag.RUNNING_TRUE);

            var result = centralityAlgorithms.eigenVector(graph, config, ProgressTracker.NULL_TRACKER);

            var rankProvider = result.centralityScoreProvider();

            var expected = graph.nodeProperties("expectedWeightedRank");

            for (int nodeId = 0; nodeId < graph.nodeCount(); nodeId++) {
                assertThat(rankProvider.applyAsDouble(nodeId)).isEqualTo(
                    expected.doubleValue(nodeId),
                    within(SCORE_PRECISION)
                );
            }
        }

        @Test
        void withSourceNodes() {
            var config = EigenvectorStreamConfigImpl
                .builder()
                .maxIterations(10)
                .tolerance(0.1)
                .concurrency(1)
                .sourceNodes(List.of(idFunction.of("d")))
                .build();

            var centralityAlgorithms = new CentralityAlgorithms(null, TerminationFlag.RUNNING_TRUE);

            var result = centralityAlgorithms.eigenVector(graph, config, ProgressTracker.NULL_TRACKER);

            var rankProvider = result.centralityScoreProvider();

            var expected = graph.nodeProperties("expectedPersonalizedRank");

            for (int nodeId = 0; nodeId < graph.nodeCount(); nodeId++) {
                assertThat(rankProvider.applyAsDouble(nodeId)).isEqualTo(
                    expected.doubleValue(nodeId),
                    within(SCORE_PRECISION)
                );
            }
        }
    }

    @Nested
    @GdlExtension
    class Scaling {
        @GdlGraph
        private static final String DB_CYPHER =
            "CREATE" +
            "  (a:Node { expectedMinMax: 0.05268, expectedStdScore: -0.41956, expectedMean: -0.15783 })" +
            ", (b:Node { expectedMinMax: 1.0,     expectedStdScore:  2.09854, expectedMean:  0.78947 })" +
            ", (c:Node { expectedMinMax: 0.92189, expectedStdScore:  1.89092, expectedMean:  0.71136 })" +
            ", (d:Node { expectedMinMax: 0.03900, expectedStdScore: -0.45594, expectedMean: -0.17152 })" +
            ", (e:Node { expectedMinMax: 0.05268, expectedStdScore: -0.41956, expectedMean: -0.15783 })" +
            ", (f:Node { expectedMinMax: 0.03900, expectedStdScore: -0.45594, expectedMean: -0.17152 })" +
            ", (g:Node { expectedMinMax: 0.0,     expectedStdScore: -0.55961, expectedMean: -0.21052 })" +
            ", (h:Node { expectedMinMax: 0.0,     expectedStdScore: -0.55961, expectedMean: -0.21052 })" +
            ", (i:Node { expectedMinMax: 0.0,     expectedStdScore: -0.55961, expectedMean: -0.21052 })" +
            ", (j:Node { expectedMinMax: 0.0,     expectedStdScore: -0.55961, expectedMean: -0.21052 })" +
            ", (b)-[:TYPE]->(c)" +
            ", (c)-[:TYPE]->(b)" +
            ", (d)-[:TYPE]->(a)" +
            ", (d)-[:TYPE]->(b)" +
            ", (e)-[:TYPE]->(b)" +
            ", (e)-[:TYPE]->(d)" +
            ", (e)-[:TYPE]->(f)" +
            ", (f)-[:TYPE]->(b)" +
            ", (f)-[:TYPE]->(e)";

        @Inject
        private Graph graph;

        @ParameterizedTest
        @CsvSource({"MINMAX, expectedMinMax", "STDSCORE, expectedStdScore", "MEAN, expectedMean"})
        void test(String scalerName, String expectedPropertyKey) {
            var config = PageRankConfigImpl
                .builder()
                .maxIterations(40)
                .tolerance(0)
                .concurrency(1)
                .scaler(ScalerFactory.parse(scalerName))
                .build();

            var centralityAlgorithms = new CentralityAlgorithms(null, TerminationFlag.RUNNING_TRUE);

            var result = centralityAlgorithms.pageRank(graph, config, ProgressTracker.NULL_TRACKER);
            var rankProvider = result.centralityScoreProvider();

            var expected = graph.nodeProperties(expectedPropertyKey);

            for (int nodeId = 0; nodeId < graph.nodeCount(); nodeId++) {
                assertThat(rankProvider.applyAsDouble(nodeId)).isEqualTo(
                    expected.doubleValue(nodeId),
                    within(SCORE_PRECISION)
                );
            }
        }
    }

    @Test
    void parallelExecution() {
        var graph = RandomGraphGenerator.builder()
            .nodeCount(40_000)
            .averageDegree(5)
            .relationshipDistribution(RelationshipDistribution.RANDOM)
            .build()
            .generate();

        var configBuilder = PageRankConfigImpl.builder();

        PageRankConfig config1 = configBuilder.concurrency(1).build();
        var centralityAlgorithms = new CentralityAlgorithms(null, TerminationFlag.RUNNING_TRUE);
        var result = centralityAlgorithms.pageRank(graph, config1, ProgressTracker.NULL_TRACKER);
        var singleThreaded = result.centralityScoreProvider();

        PageRankConfig config = configBuilder.concurrency(4).build();
        var result2 = centralityAlgorithms.pageRank(graph, config, ProgressTracker.NULL_TRACKER);
        var multiThreaded = result2.centralityScoreProvider();

        for (long nodeId = 0; nodeId < graph.nodeCount(); nodeId++) {
            assertThat(singleThreaded.applyAsDouble(nodeId)).isEqualTo(multiThreaded.applyAsDouble(nodeId), Offset.offset(1e-5));
        }
    }
}
