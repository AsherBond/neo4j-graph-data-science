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
package org.neo4j.gds.similarity.nodesim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.gds.Orientation;
import org.neo4j.gds.SimilarityAlgorithmTasks;
import org.neo4j.gds.TestProgressTrackerHelper;
import org.neo4j.gds.TestSupport;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.applications.algorithms.machinery.AlgorithmMachinery;
import org.neo4j.gds.applications.algorithms.machinery.ProgressTrackerCreator;
import org.neo4j.gds.applications.algorithms.machinery.RequestScopedDependencies;
import org.neo4j.gds.applications.algorithms.similarity.SimilarityAlgorithms;
import org.neo4j.gds.applications.algorithms.similarity.SimilarityAlgorithmsBusinessFacade;
import org.neo4j.gds.core.concurrency.Concurrency;
import org.neo4j.gds.core.concurrency.DefaultPool;
import org.neo4j.gds.core.utils.logging.LoggerForProgressTrackingAdapter;
import org.neo4j.gds.core.utils.progress.EmptyTaskRegistryFactory;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.core.utils.warnings.EmptyUserLogRegistryFactory;
import org.neo4j.gds.extension.GdlExtension;
import org.neo4j.gds.extension.GdlGraph;
import org.neo4j.gds.extension.Inject;
import org.neo4j.gds.extension.TestGraph;
import org.neo4j.gds.logging.GdsTestLog;
import org.neo4j.gds.similarity.SimilarityGraphResult;
import org.neo4j.gds.similarity.SimilarityResult;
import org.neo4j.gds.similarity.filtering.NodeFilter;
import org.neo4j.gds.termination.TerminationFlag;
import org.neo4j.gds.wcc.WccStub;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.neo4j.gds.Orientation.NATURAL;
import static org.neo4j.gds.Orientation.REVERSE;
import static org.neo4j.gds.Orientation.UNDIRECTED;
import static org.neo4j.gds.TestSupport.assertGraphEquals;
import static org.neo4j.gds.TestSupport.crossArguments;
import static org.neo4j.gds.TestSupport.fromGdl;
import static org.neo4j.gds.TestSupport.toArguments;
import static org.neo4j.gds.assertj.Extractors.removingThreadId;
import static org.neo4j.gds.compat.TestLog.INFO;
import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

@GdlExtension
final class NodeSimilarityTest {

    // fixing idOffset to 0 as the expectations hard-code ids
    @GdlGraph(graphNamePrefix = "natural", orientation = NATURAL, idOffset = 0)
    @GdlGraph(graphNamePrefix = "reverse", orientation = REVERSE, idOffset = 0)
    @GdlGraph(graphNamePrefix = "undirected", orientation = UNDIRECTED, idOffset = 0)
    private static final String DB_CYPHER =
        "CREATE" +
            "  (a:Person)" +
            ", (b:Person)" +
            ", (c:Person)" +
            ", (d:Person)" +
            ", (i1:Item)" +
            ", (i2:Item)" +
            ", (i3:Item)" +
            ", (i4:Item)" +
            ", (a)-[:LIKES {prop: 1.0}]->(i1)" +
            ", (a)-[:LIKES {prop: 1.0}]->(i2)" +
            ", (a)-[:LIKES {prop: 2.0}]->(i3)" +
            ", (b)-[:LIKES {prop: 1.0}]->(i1)" +
            ", (b)-[:LIKES {prop: 1.0}]->(i2)" +
            ", (c)-[:LIKES {prop: 1.0}]->(i3)" +
            ", (d)-[:LIKES {prop: 0.5}]->(i1)" +
            ", (d)-[:LIKES {prop: 1.0}]->(i2)" +
            ", (d)-[:LIKES {prop: 1.0}]->(i3)";

    @GdlGraph(graphNamePrefix = "naturalUnion", orientation = NATURAL, idOffset = 0)
    private static final String DB_CYPHER_UNION =
        "CREATE" +
            "  (a:Person)" +
            ", (b:Person)" +
            ", (c:Person)" +
            ", (d:Person)" +
            ", (i1:Item)" +
            ", (i2:Item)" +
            ", (i3:Item)" +
            ", (i4:Item)" +
            ", (a)-[:LIKES3 {prop: 1.0}]->(i1)" +
            ", (a)-[:LIKES2 {prop: 1.0}]->(i2)" +
            ", (a)-[:LIKES1 {prop: 2.0}]->(i3)" +
            ", (b)-[:LIKES2 {prop: 1.0}]->(i1)" +
            ", (b)-[:LIKES1 {prop: 1.0}]->(i2)" +
            ", (c)-[:LIKES3 {prop: 1.0}]->(i3)" +
            ", (d)-[:LIKES2 {prop: 0.5}]->(i1)" +
            ", (d)-[:LIKES3 {prop: 1.0}]->(i2)" +
            ", (d)-[:LIKES1 {prop: 1.0}]->(i3)";

    @Inject
    private TestGraph naturalGraph;

    @Inject
    private TestGraph reverseGraph;

    @Inject
    private TestGraph undirectedGraph;

    @Inject
    private TestGraph naturalUnionGraph;

    private static final Collection<String> EXPECTED_OUTGOING = new HashSet<>();
    private static final Collection<String> EXPECTED_INCOMING = new HashSet<>();

    private static final Collection<String> EXPECTED_WEIGHTED_OUTGOING = new HashSet<>();
    private static final Collection<String> EXPECTED_WEIGHTED_INCOMING = new HashSet<>();

    private static final Collection<String> EXPECTED_OUTGOING_TOP_N_1 = new HashSet<>();
    private static final Collection<String> EXPECTED_INCOMING_TOP_N_1 = new HashSet<>();

    private static final Collection<String> EXPECTED_OUTGOING_TOP_K_1 = new HashSet<>();
    private static final Collection<String> EXPECTED_INCOMING_TOP_K_1 = new HashSet<>();

    private static final Collection<String> EXPECTED_OUTGOING_SIMILARITY_CUTOFF = new HashSet<>();
    private static final Collection<String> EXPECTED_INCOMING_SIMILARITY_CUTOFF = new HashSet<>();

    private static final Collection<String> EXPECTED_OUTGOING_DEGREE_CUTOFF = new HashSet<>();
    private static final Collection<String> EXPECTED_INCOMING_DEGREE_CUTOFF = new HashSet<>();

    private static final int COMPARED_ITEMS = 3;
    private static final int COMPARED_PERSONS = 4;

    private static String resultString(long node1, long node2, double similarity) {
        return formatWithLocale("%d,%d %f", node1, node2, similarity);
    }

    private static String resultString(SimilarityResult result) {
        return resultString(result.node1, result.node2, result.similarity);
    }

    private static Stream<Integer> concurrencies() {
        return Stream.of(1, 4);
    }

    static {
        EXPECTED_OUTGOING.add(resultString(0, 1, 2 / 3.0));
        EXPECTED_OUTGOING.add(resultString(0, 2, 1 / 3.0));
        EXPECTED_OUTGOING.add(resultString(0, 3, 1.0));
        EXPECTED_OUTGOING.add(resultString(1, 2, 0.0));
        EXPECTED_OUTGOING.add(resultString(1, 3, 2 / 3.0));
        EXPECTED_OUTGOING.add(resultString(2, 3, 1 / 3.0));
        // Add results in reverse direction because topK
        EXPECTED_OUTGOING.add(resultString(1, 0, 2 / 3.0));
        EXPECTED_OUTGOING.add(resultString(2, 0, 1 / 3.0));
        EXPECTED_OUTGOING.add(resultString(3, 0, 1.0));
        EXPECTED_OUTGOING.add(resultString(2, 1, 0.0));
        EXPECTED_OUTGOING.add(resultString(3, 1, 2 / 3.0));
        EXPECTED_OUTGOING.add(resultString(3, 2, 1 / 3.0));

        EXPECTED_WEIGHTED_OUTGOING.add(resultString(0, 1, 2 / 4.0));
        EXPECTED_WEIGHTED_OUTGOING.add(resultString(0, 2, 1 / 4.0));
        EXPECTED_WEIGHTED_OUTGOING.add(resultString(0, 3, 2.5 / 4.0));
        EXPECTED_WEIGHTED_OUTGOING.add(resultString(1, 2, 0.0));
        EXPECTED_WEIGHTED_OUTGOING.add(resultString(1, 3, 2 / 4.0));
        EXPECTED_WEIGHTED_OUTGOING.add(resultString(2, 3, 1 / 2.5));
        // Add results in reverse direction because topK
        EXPECTED_WEIGHTED_OUTGOING.add(resultString(1, 0, 2 / 4.0));
        EXPECTED_WEIGHTED_OUTGOING.add(resultString(2, 0, 1 / 4.0));
        EXPECTED_WEIGHTED_OUTGOING.add(resultString(3, 0, 2.5 / 4.0));
        EXPECTED_WEIGHTED_OUTGOING.add(resultString(2, 1, 0.0));
        EXPECTED_WEIGHTED_OUTGOING.add(resultString(3, 1, 2 / 4.0));
        EXPECTED_WEIGHTED_OUTGOING.add(resultString(3, 2, 1 / 2.5));

        EXPECTED_OUTGOING_TOP_N_1.add(resultString(0, 3, 1.0));

        EXPECTED_OUTGOING_TOP_K_1.add(resultString(0, 3, 1.0));
        EXPECTED_OUTGOING_TOP_K_1.add(resultString(1, 0, 2 / 3.0));
        EXPECTED_OUTGOING_TOP_K_1.add(resultString(2, 0, 1 / 3.0));
        EXPECTED_OUTGOING_TOP_K_1.add(resultString(3, 0, 1.0));

        EXPECTED_OUTGOING_SIMILARITY_CUTOFF.add(resultString(0, 1, 2 / 3.0));
        EXPECTED_OUTGOING_SIMILARITY_CUTOFF.add(resultString(0, 2, 1 / 3.0));
        EXPECTED_OUTGOING_SIMILARITY_CUTOFF.add(resultString(0, 3, 1.0));
        EXPECTED_OUTGOING_SIMILARITY_CUTOFF.add(resultString(1, 3, 2 / 3.0));
        EXPECTED_OUTGOING_SIMILARITY_CUTOFF.add(resultString(2, 3, 1 / 3.0));
        // Add results in reverse direction because topK
        EXPECTED_OUTGOING_SIMILARITY_CUTOFF.add(resultString(1, 0, 2 / 3.0));
        EXPECTED_OUTGOING_SIMILARITY_CUTOFF.add(resultString(2, 0, 1 / 3.0));
        EXPECTED_OUTGOING_SIMILARITY_CUTOFF.add(resultString(3, 0, 1.0));
        EXPECTED_OUTGOING_SIMILARITY_CUTOFF.add(resultString(3, 1, 2 / 3.0));
        EXPECTED_OUTGOING_SIMILARITY_CUTOFF.add(resultString(3, 2, 1 / 3.0));

        EXPECTED_OUTGOING_DEGREE_CUTOFF.add(resultString(0, 1, 2 / 3.0));
        EXPECTED_OUTGOING_DEGREE_CUTOFF.add(resultString(0, 3, 1.0));
        EXPECTED_OUTGOING_DEGREE_CUTOFF.add(resultString(1, 3, 2 / 3.0));
        // Add results in reverse direction because topK
        EXPECTED_OUTGOING_DEGREE_CUTOFF.add(resultString(1, 0, 2 / 3.0));
        EXPECTED_OUTGOING_DEGREE_CUTOFF.add(resultString(3, 0, 1.0));
        EXPECTED_OUTGOING_DEGREE_CUTOFF.add(resultString(3, 1, 2 / 3.0));

        EXPECTED_INCOMING.add(resultString(4, 5, 1.0));
        EXPECTED_INCOMING.add(resultString(4, 6, 1 / 2.0));
        EXPECTED_INCOMING.add(resultString(5, 6, 1 / 2.0));
        // Add results in reverse direction because topK
        EXPECTED_INCOMING.add(resultString(5, 4, 1.0));
        EXPECTED_INCOMING.add(resultString(6, 4, 1 / 2.0));
        EXPECTED_INCOMING.add(resultString(6, 5, 1 / 2.0));

        EXPECTED_WEIGHTED_INCOMING.add(resultString(4, 5, 2.5 / 3.0));
        EXPECTED_WEIGHTED_INCOMING.add(resultString(4, 6, 1.5 / 5.0));
        EXPECTED_WEIGHTED_INCOMING.add(resultString(5, 6, 2.0 / 5.0));
        // Add results in reverse direction because topK
        EXPECTED_WEIGHTED_INCOMING.add(resultString(5, 4, 2.5 / 3.0));
        EXPECTED_WEIGHTED_INCOMING.add(resultString(6, 4, 1.5 / 5.0));
        EXPECTED_WEIGHTED_INCOMING.add(resultString(6, 5, 2.0 / 5.0));

        EXPECTED_INCOMING_TOP_N_1.add(resultString(4, 5, 3.0 / 3.0));

        EXPECTED_INCOMING_TOP_K_1.add(resultString(4, 5, 1.0));
        EXPECTED_INCOMING_TOP_K_1.add(resultString(5, 4, 1.0));
        EXPECTED_INCOMING_TOP_K_1.add(resultString(6, 4, 1 / 2.0));

        EXPECTED_INCOMING_SIMILARITY_CUTOFF.add(resultString(4, 5, 1.0));
        EXPECTED_INCOMING_SIMILARITY_CUTOFF.add(resultString(4, 6, 1 / 2.0));
        EXPECTED_INCOMING_SIMILARITY_CUTOFF.add(resultString(5, 6, 1 / 2.0));
        // Add results in reverse direction because topK
        EXPECTED_INCOMING_SIMILARITY_CUTOFF.add(resultString(5, 4, 1.0));
        EXPECTED_INCOMING_SIMILARITY_CUTOFF.add(resultString(6, 4, 1 / 2.0));
        EXPECTED_INCOMING_SIMILARITY_CUTOFF.add(resultString(6, 5, 1 / 2.0));

        EXPECTED_INCOMING_DEGREE_CUTOFF.add(resultString(4, 5, 3.0 / 3.0));
        EXPECTED_INCOMING_DEGREE_CUTOFF.add(resultString(4, 6, 1 / 2.0));
        EXPECTED_INCOMING_DEGREE_CUTOFF.add(resultString(5, 6, 1 / 2.0));
        // Add results in reverse direction because topK
        EXPECTED_INCOMING_DEGREE_CUTOFF.add(resultString(5, 4, 3.0 / 3.0));
        EXPECTED_INCOMING_DEGREE_CUTOFF.add(resultString(6, 4, 1 / 2.0));
        EXPECTED_INCOMING_DEGREE_CUTOFF.add(resultString(6, 5, 1 / 2.0));
    }

    static Stream<Arguments> supportedLoadAndComputeDirections() {
        Stream<Arguments> directions = Stream.of(
            arguments(NATURAL),
            arguments(REVERSE)
        );
        return crossArguments(() -> directions, toArguments(NodeSimilarityTest::concurrencies));
    }

    static Stream<Arguments> topKAndConcurrencies() {
        Stream<Integer> topKStream = Stream.of(10, 100);
        return TestSupport.crossArguments(
            toArguments(() -> topKStream),
            toArguments(NodeSimilarityTest::concurrencies)
        );
    }

    @ParameterizedTest(name = "orientation: {0}, concurrency: {1}")
    @MethodSource("supportedLoadAndComputeDirections")
    void shouldComputeWeightedForSupportedDirections(Orientation orientation, int concurrency) {
        Graph graph = orientation == NATURAL ? naturalGraph : reverseGraph;

        var parameters = new NodeSimilarityParameters(
            new Concurrency(concurrency),
            new JaccardSimilarityComputer(0.0),
            1,
            Integer.MAX_VALUE,
            10,
            0,
            true,
            true,
            false,
            null
        );

        var nodeSimilarity = constructNodeSimilarity(
            graph,
            parameters
        );

        Set<String> result = nodeSimilarity
            .compute()
            .streamResult()
            .map(NodeSimilarityTest::resultString)
            .collect(Collectors.toSet());

        assertEquals(orientation == REVERSE ? EXPECTED_WEIGHTED_INCOMING : EXPECTED_WEIGHTED_OUTGOING, result);
    }

    @ParameterizedTest(name = "orientation: {0}, concurrency: {1}")
    @MethodSource("supportedLoadAndComputeDirections")
    void shouldComputeForSupportedDirections(Orientation orientation, int concurrency) {
        Graph graph = orientation == NATURAL ? naturalGraph : reverseGraph;

        var parameters = new NodeSimilarityParameters(
            new Concurrency(concurrency),
            new JaccardSimilarityComputer(0.0),
            1,
            Integer.MAX_VALUE,
            10,
            0,
            true,
            false,
            false,
            null
        );
        NodeSimilarity nodeSimilarity = constructNodeSimilarity(
            graph,
            parameters
        );

        Set<String> result = nodeSimilarity
            .compute()
            .streamResult()
            .map(NodeSimilarityTest::resultString)
            .collect(Collectors.toSet());

        assertEquals(orientation == REVERSE ? EXPECTED_INCOMING : EXPECTED_OUTGOING, result);
    }

    @ParameterizedTest(name = "orientation: {0}, concurrency: {1}")
    @MethodSource("supportedLoadAndComputeDirections")
    void shouldComputeTopNForSupportedDirections(Orientation orientation, int concurrency) {
        Graph graph = orientation == NATURAL ? naturalGraph : reverseGraph;

        var parameters = new NodeSimilarityParameters(
            new Concurrency(concurrency),
            new JaccardSimilarityComputer(0.0),
            1,
            Integer.MAX_VALUE,
            10,
            1,
            true,
            false,
            false,
            null
        );

        NodeSimilarity nodeSimilarity = constructNodeSimilarity(
            graph,
            parameters
        );

        Set<String> result = nodeSimilarity
            .compute()
            .streamResult()
            .map(NodeSimilarityTest::resultString)
            .collect(Collectors.toSet());

        assertEquals(orientation == REVERSE ? EXPECTED_INCOMING_TOP_N_1 : EXPECTED_OUTGOING_TOP_N_1, result);
    }

    @ParameterizedTest(name = "orientation: {0}, concurrency: {1}")
    @MethodSource("supportedLoadAndComputeDirections")
    void shouldComputeNegativeTopNForSupportedDirections(Orientation orientation, int concurrency) {
        Graph graph = orientation == NATURAL ? naturalGraph : reverseGraph;

        var parameters = new NodeSimilarityParameters(
            new Concurrency(concurrency),
            new JaccardSimilarityComputer(0.0),
            1,
            Integer.MAX_VALUE,
            10,
            -1,
            false,
            false,
            false,
            null
        );

        NodeSimilarity nodeSimilarity = constructNodeSimilarity(
            graph,
            parameters
        );

        Graph similarityGraph = nodeSimilarity.compute().graphResult().similarityGraph();

        assertGraphEquals(
            orientation == REVERSE
                ? fromGdl(
                "(i1:Item)-[:REL {w: 0.50000D}]->(i3:Item), (i2:Item), (i4:Item), (a:Person), (b:Person), (c:Person), (d:Person)")
                : fromGdl(
                    "(a:Person), (b:Person)-[:REL {w: 0.00000D}]->(c:Person), (d:Person), (i1:Item), (i2:Item), (i3:Item), (i4:Item)"),
            similarityGraph
        );
    }

    @ParameterizedTest(name = "orientation: {0}, concurrency: {1}")
    @MethodSource("supportedLoadAndComputeDirections")
    void shouldComputeTopKForSupportedDirections(Orientation orientation, int concurrency) {
        Graph graph = orientation == NATURAL ? naturalGraph : reverseGraph;

        var parameters = new NodeSimilarityParameters(
            new Concurrency(concurrency),
            new JaccardSimilarityComputer(0.0),
            1,
            Integer.MAX_VALUE,
            1,
            0,
            true,
            false,
            false,
            null
        );

        NodeSimilarity nodeSimilarity = constructNodeSimilarity(
            graph,
            parameters
        );

        Set<String> result = nodeSimilarity
            .compute()
            .streamResult()
            .map(NodeSimilarityTest::resultString)
            .collect(Collectors.toSet());

        assertEquals(orientation == REVERSE ? EXPECTED_INCOMING_TOP_K_1 : EXPECTED_OUTGOING_TOP_K_1, result);
    }

    @ParameterizedTest(name = "orientation: {0}, concurrency: {1}")
    @MethodSource("supportedLoadAndComputeDirections")
    void shouldComputeNegativeTopKForSupportedDirections(Orientation orientation, int concurrency) {
        Graph graph = orientation == NATURAL ? naturalGraph : reverseGraph;

        var parameters = new NodeSimilarityParameters(
            new Concurrency(concurrency),
            new JaccardSimilarityComputer(0.0),
            1,
            Integer.MAX_VALUE,
            -1,
            0,
            false,
            false,
            false,
            null
        );

        NodeSimilarity nodeSimilarity = constructNodeSimilarity(
            graph,
            parameters
        );

        Graph similarityGraph = nodeSimilarity.compute().graphResult().similarityGraph();

        assertGraphEquals(
            orientation == REVERSE
                ? fromGdl("  (i1:Item)-[:SIMILAR {w: 0.50000D}]->(i3:Item)" +
                ", (i2:Item)-[:SIMILAR {w: 0.50000D}]->(i3)" +
                ", (i3)-[:SIMILAR {w: 0.500000D}]->(i1)" +
                ", (i4:Item)" +
                ", (:Person), (:Person), (:Person), (:Person)")
                : fromGdl("  (a:Person)-[:SIMILAR {w: 0.333333D}]->(c:Person)" +
                    ", (b:Person)-[:SIMILAR {w: 0.00000D}]->(c)" +
                    ", (c)-[:SIMILAR {w: 0.000000D}]->(b)" +
                    ", (d:Person)-[:SIMILAR {w: 0.333333D}]->(c)" +
                    ", (:Item), (:Item), (:Item), (:Item)"),
            similarityGraph
        );
    }

    @ParameterizedTest(name = "orientation: {0}, concurrency: {1}")
    @MethodSource("supportedLoadAndComputeDirections")
    void shouldComputeWithSimilarityCutoffForSupportedDirections(Orientation orientation, int concurrency) {
        Graph graph = orientation == NATURAL ? naturalGraph : reverseGraph;

        var parameters = new NodeSimilarityParameters(
            new Concurrency(concurrency),
            new JaccardSimilarityComputer(0.1),
            1,
            Integer.MAX_VALUE,
            10,
            0,
            true,
            false,
            false,
            null
        );

        NodeSimilarity nodeSimilarity = constructNodeSimilarity(
            graph,
            parameters
        );

        Set<String> result = nodeSimilarity
            .compute()
            .streamResult()
            .map(NodeSimilarityTest::resultString)
            .collect(Collectors.toSet());

        assertEquals(
            orientation == REVERSE ? EXPECTED_INCOMING_SIMILARITY_CUTOFF : EXPECTED_OUTGOING_SIMILARITY_CUTOFF,
            result
        );
    }

    @ParameterizedTest(name = "orientation: {0}, concurrency: {1}")
    @MethodSource("supportedLoadAndComputeDirections")
    void shouldComputeWithDegreeCutoffForSupportedDirections(Orientation orientation, int concurrency) {
        Graph graph = orientation == NATURAL ? naturalGraph : reverseGraph;

        var parameters = new NodeSimilarityParameters(
            new Concurrency(concurrency),
            new JaccardSimilarityComputer(0.0),
            2,
            Integer.MAX_VALUE,
            10,
            0,
            true,
            false,
            false,
            null
        );

        NodeSimilarity nodeSimilarity = constructNodeSimilarity(
            graph,
            parameters
        );

        Set<String> result = nodeSimilarity
            .compute()
            .streamResult()
            .map(NodeSimilarityTest::resultString)
            .collect(Collectors.toSet());

        assertEquals(
            orientation == REVERSE ? EXPECTED_INCOMING_DEGREE_CUTOFF : EXPECTED_OUTGOING_DEGREE_CUTOFF,
            result
        );
    }

    @ParameterizedTest(name = "concurrency = {0}")
    @MethodSource("concurrencies")
    void shouldComputeForUndirectedGraphs(int concurrency) {
        var parameters = new NodeSimilarityParameters(
            new Concurrency(concurrency),
            new JaccardSimilarityComputer(0.0),
            1,
            Integer.MAX_VALUE,
            10,
            0,
            true,
            false,
            false,
            null
        );

        NodeSimilarity nodeSimilarity = constructNodeSimilarity(
            undirectedGraph,
            parameters
        );
        Set<SimilarityResult> result = nodeSimilarity.compute()
            .streamResult().collect(Collectors.toSet());
        assertNotEquals(Collections.emptySet(), result);
    }

    @Test
    void shouldComputeForUnionGraphs() {
        var parameters = new NodeSimilarityParameters(
            new Concurrency(1),
            new JaccardSimilarityComputer(0.0),
            1,
            Integer.MAX_VALUE,
            10,
            0,
            true,
            false,
            false,
            null
        );
        NodeSimilarity nodeSimilarity = constructNodeSimilarity(
            naturalGraph,
            parameters
        );
        var result1 = nodeSimilarity.compute()
            .streamResult().collect(Collectors.toSet());

        nodeSimilarity = constructNodeSimilarity(
            naturalUnionGraph,
            parameters
        );
        var result2 = nodeSimilarity.compute()
            .streamResult().collect(Collectors.toSet());

        assertEquals(result1, result2);
    }

    @ParameterizedTest(name = "orientation: {0}, concurrency: {1}")
    @MethodSource("supportedLoadAndComputeDirections")
    void shouldComputeSimilarityGraphInAllSupportedDirections(Orientation orientation, int concurrency) {
        Graph graph = orientation == NATURAL ? naturalGraph : reverseGraph;

        var parameters = new NodeSimilarityParameters(
            new Concurrency(concurrency),
            new JaccardSimilarityComputer(0.0),
            1,
            Integer.MAX_VALUE,
            10,
            0,
            false,
            false,
            false,
            null
        );

        NodeSimilarity nodeSimilarity = constructNodeSimilarity(
            graph,
            parameters
        );

        SimilarityGraphResult similarityGraphResult = nodeSimilarity.compute().graphResult();
        assertEquals(
            orientation == REVERSE ? COMPARED_ITEMS : COMPARED_PERSONS,
            similarityGraphResult.comparedNodes()
        );
        Graph resultGraph = similarityGraphResult.similarityGraph();
        assertGraphEquals(
            orientation == REVERSE
                ? fromGdl("  (:Person), (:Person), (:Person), (:Person)" +
                ", (:Item)" +
                ", (i1:Item)-[:SIMILAR {property: 1.000000D}]->(i2:Item)" +
                ", (i1)-[:SIMILAR {property: 0.500000D}]->(i3:Item)" +
                ", (i2)-[:SIMILAR {property: 0.500000D}]->(i3)" +
                // Add results in reverse direction because topK
                ", (i2)-[:SIMILAR {property: 1.000000D}]->(i1)" +
                ", (i3)-[:SIMILAR {property: 0.500000D}]->(i1)" +
                ", (i3)-[:SIMILAR {property: 0.500000D}]->(i2)")
                : fromGdl("  (p1:Person)-[:SIMILAR {property: 0.666667D}]->(p2:Person)" +
                    ", (p1)-[:SIMILAR {property: 0.333333D}]->(p3:Person)" +
                    ", (p1)-[:SIMILAR {property: 1.000000D}]->(p4:Person)" +
                    ", (p2)-[:SIMILAR {property: 0.000000D}]->(p3)" +
                    ", (p2)-[:SIMILAR {property: 0.666667D}]->(p4)" +
                    ", (p3)-[:SIMILAR {property: 0.333333D}]->(p4)" +
                    // Add results in reverse direction because topK
                    "  (p2)-[:SIMILAR {property: 0.666667D}]->(p1)" +
                    ", (p3)-[:SIMILAR {property: 0.333333D}]->(p1)" +
                    ", (p4)-[:SIMILAR {property: 1.000000D}]->(p1)" +
                    ", (p3)-[:SIMILAR {property: 0.000000D}]->(p2)" +
                    ", (p4)-[:SIMILAR {property: 0.666667D}]->(p2)" +
                    ", (p4)-[:SIMILAR {property: 0.333333D}]->(p3)" +
                    ", (:Item), (:Item), (:Item), (:Item)"),
            resultGraph
        );
    }

    @ParameterizedTest(name = "orientation: {0}, concurrency: {1}")
    @MethodSource("supportedLoadAndComputeDirections")
    void shouldComputeToGraphWithUnusedNodesInInputGraph(Orientation orientation, int concurrency) {
        Graph graph = fromGdl(DB_CYPHER + ", (:Unused)".repeat(1024), orientation);

        var parameters = new NodeSimilarityParameters(
            new Concurrency(concurrency),
            new JaccardSimilarityComputer(0.0),
            1,
            Integer.MAX_VALUE,
            100,
            1,
            false,
            false,
            false,
            null
        );

        NodeSimilarity nodeSimilarity = constructNodeSimilarity(
            graph,
            parameters
        );

        SimilarityGraphResult similarityGraphResult = nodeSimilarity.compute().graphResult();
        assertEquals(
            orientation == REVERSE ? COMPARED_ITEMS : COMPARED_PERSONS,
            similarityGraphResult.comparedNodes()
        );

        Graph resultGraph = similarityGraphResult.similarityGraph();
        String expected = orientation == REVERSE ? resultString(4, 5, 1.00000) : resultString(
            0,
            3,
            1.00000
        );

        resultGraph.forEachNode(n -> {
            resultGraph.forEachRelationship(n, -1.0, (s, t, w) -> {
                assertEquals(expected, resultString(s, t, w));
                return true;
            });
            return true;
        });

    }

    @ParameterizedTest(name = "orientation: {0}, concurrency: {1}")
    @MethodSource("supportedLoadAndComputeDirections")
    void shouldIgnoreLoops(Orientation orientation, int concurrency) {
        // Add loops
        var gdl = DB_CYPHER + ", (a)-[:LIKES {prop: 1.0}]->(a), (i1)-[:LIKES {prop: 1.0}]->(i1)";

        Graph graph = fromGdl(gdl, orientation);

        var parameters = new NodeSimilarityParameters(
            new Concurrency(concurrency),
            new JaccardSimilarityComputer(0.0),
            1,
            Integer.MAX_VALUE,
            10,
            1,
            true,
            false,
            false,
            null
        );

        NodeSimilarity nodeSimilarity = constructNodeSimilarity(
            graph,
            parameters
        );

        Set<String> result = nodeSimilarity
            .compute()
            .streamResult()
            .map(NodeSimilarityTest::resultString)
            .collect(Collectors.toSet());

        assertEquals(orientation == REVERSE ? EXPECTED_INCOMING_TOP_N_1 : EXPECTED_OUTGOING_TOP_N_1, result);
    }

    @ParameterizedTest(name = "orientation: {0}, concurrency: {1}")
    @MethodSource("supportedLoadAndComputeDirections")
    void shouldIgnoreParallelEdges(Orientation orientation, int concurrency) {
        // Add parallel edges
        var gdl = DB_CYPHER +
            ", (a)-[:LIKES {prop: 1.0}]->(i1)" +
            ", (c)-[:LIKES {prop: 1.0}]->(i3)" +
            ", (c)-[:LIKES {prop: 1.0}]->(i3)" +
            ", (c)-[:LIKES {prop: 1.0}]->(i3)";

        Graph graph = fromGdl(gdl, orientation);

        var parameters = new NodeSimilarityParameters(
            new Concurrency(concurrency),
            new JaccardSimilarityComputer(0.0),
            1,
            Integer.MAX_VALUE,
            10,
            0,
            true,
            false,
            false,
            null
        );

        NodeSimilarity nodeSimilarity = constructNodeSimilarity(
            graph,
            parameters
        );

        Set<String> result = nodeSimilarity
            .compute()
            .streamResult()
            .map(NodeSimilarityTest::resultString)
            .collect(Collectors.toSet());

        assertEquals(orientation == REVERSE ? EXPECTED_INCOMING : EXPECTED_OUTGOING, result);
    }

    @Test
    void shouldLogMessages() {

        var params =  NodeSimilarityBaseConfigImpl.builder().build().toParameters();
        var progressTrackerWithLog = TestProgressTrackerHelper.create(
            new SimilarityAlgorithmTasks().nodeSimilarity(naturalGraph, params),
            new Concurrency(2)
        );

        var progressTracker = progressTrackerWithLog.progressTracker();
        var log = progressTrackerWithLog.log();

        var nodeSimilarity = new NodeSimilarity(
            naturalGraph,
            params,
            DefaultPool.INSTANCE,
            progressTracker,
            NodeFilter.ALLOW_EVERYTHING,
            NodeFilter.ALLOW_EVERYTHING,
            TerminationFlag.RUNNING_TRUE,
            new WccStub(TerminationFlag.RUNNING_TRUE,new AlgorithmMachinery())
        );

        nodeSimilarity.compute();

        assertThat(log.getMessages(INFO))
            .extracting(removingThreadId())
            .contains(
                "Node Similarity :: prepare :: Start",
                "Node Similarity :: prepare :: Finished",
                "Node Similarity :: compare node pairs :: Start",
                "Node Similarity :: compare node pairs :: Finished"
            );
    }

    @Test
    void shouldNotLogMessagesWhenLoggingIsDisabled() {
        var log = new GdsTestLog();
        var requestScopedDependencies = RequestScopedDependencies.builder()
            .taskRegistryFactory(EmptyTaskRegistryFactory.INSTANCE)
            .terminationFlag(TerminationFlag.RUNNING_TRUE)
            .userLogRegistryFactory(EmptyUserLogRegistryFactory.INSTANCE)
            .build();
        var progressTrackerCreator = new ProgressTrackerCreator(new LoggerForProgressTrackingAdapter(log), requestScopedDependencies);
        var similarityAlgorithms = new SimilarityAlgorithms(requestScopedDependencies.terminationFlag());

        var similarityBusiness = new SimilarityAlgorithmsBusinessFacade(similarityAlgorithms,progressTrackerCreator);

        similarityBusiness.nodeSimilarity(naturalGraph, NodeSimilarityBaseConfigImpl.builder().logProgress(false).build());

        assertThat(log.getMessages(INFO))
            .as("When progress logging is disabled we only log `start` and `finished`.")
            .extracting(removingThreadId())
            .containsExactly(
                "Node Similarity :: Start",
                "Node Similarity :: prepare :: Start",
                "Node Similarity :: prepare :: Finished",
                "Node Similarity :: compare node pairs :: Start",
                "Node Similarity :: compare node pairs :: Finished",
                "Node Similarity :: Finished"
            );
    }

    @ParameterizedTest(name = "concurrency = {0}")
    @ValueSource(ints = {1, 2})
    void shouldLogProgress(int concurrencyValue) {
        var params =  NodeSimilarityBaseConfigImpl.builder().build().toParameters();
        var progressTrackerWithLog = TestProgressTrackerHelper.create(
            new SimilarityAlgorithmTasks().nodeSimilarity(naturalGraph, params),
            new Concurrency(concurrencyValue)
        );

        var progressTracker = progressTrackerWithLog.progressTracker();
        var log = progressTrackerWithLog.log();

        var nodeSimilarity = new NodeSimilarity(
            naturalGraph,
            params,
            DefaultPool.INSTANCE,
            progressTracker,
            NodeFilter.ALLOW_EVERYTHING,
            NodeFilter.ALLOW_EVERYTHING,
            TerminationFlag.RUNNING_TRUE,
            new WccStub(TerminationFlag.RUNNING_TRUE,new AlgorithmMachinery())
        );

        nodeSimilarity.compute();

        var progresses = progressTracker.getProgresses();

        // Should log progress for prepare and actual comparisons
        assertEquals(4, progresses.size());
        assertEquals(naturalGraph.relationshipCount(), progresses.get(1).get());

        assertThat(log.getMessages(INFO))
            .extracting(removingThreadId())
            .contains(
                "Node Similarity :: prepare :: Start",
                "Node Similarity :: prepare :: Finished",
                "Node Similarity :: compare node pairs :: Start",
                "Node Similarity :: compare node pairs :: Finished"
            );
    }

    @Test
    void shouldLogProgressForWccOptimization() {

        var params =  NodeSimilarityBaseConfigImpl
            .builder()
            .useComponents(true)
            .build()
            .toParameters();

        var progressTrackerWithLog = TestProgressTrackerHelper.create(
            new SimilarityAlgorithmTasks().nodeSimilarity(naturalGraph, params),
            new Concurrency(4)
        );

        var progressTracker = progressTrackerWithLog.progressTracker();
        var log = progressTrackerWithLog.log();

        var nodeSimilarity = new NodeSimilarity(
            naturalGraph,
            params,
            DefaultPool.INSTANCE,
            progressTracker,
            NodeFilter.ALLOW_EVERYTHING,
            NodeFilter.ALLOW_EVERYTHING,
            TerminationFlag.RUNNING_TRUE,
            new WccStub(TerminationFlag.RUNNING_TRUE,new AlgorithmMachinery())
        );

        nodeSimilarity.compute();

        var progresses = progressTracker.getProgresses();

        // Should log progress for prepare and actual comparisons
        assertThat(progresses).hasSize(6);

        assertThat(log.getMessages(INFO))
            .extracting(removingThreadId())
            .contains(
                "Node Similarity :: prepare :: WCC :: Start",
                "Node Similarity :: prepare :: WCC :: Finished",
                "Node Similarity :: prepare :: Start",
                "Node Similarity :: prepare :: Finished",
                "Node Similarity :: compare node pairs :: Start",
                "Node Similarity :: compare node pairs :: Finished"
            );

       assertThat(log.getMessages(INFO)
                .stream()
                .filter( v -> v.contains("WCC"))
                .count())
           .isGreaterThan(3);


    }

    @Test
    void shouldGiveCorrectResultsWithOverlap() {
        var gdl =
            "CREATE" +
                "  (a:Person)" +
                ", (c:Person)" +
                ", (i1:Item)" +
                ", (i2:Item)" +
                ", (i3:Item)" +
                ", (i4:Item)" +
                ", (a)-[:LIKES {prop: 1.0}]->(i1)" +
                ", (a)-[:LIKES {prop: 2.0}]->(i4)" +
                ", (c)-[:LIKES {prop: 3.0}]->(i3)" +
                ", (c)-[:LIKES {prop: 4.0}]->(i1)";

        Graph graph = fromGdl(gdl);

        var parameters1 = new NodeSimilarityParameters(
            new Concurrency(1),
            new OverlapSimilarityComputer(0.0),
            1,
            Integer.MAX_VALUE,
            10,
            0,
            true,
            false,
            false,
            null
        );
        NodeSimilarity nodeSimilarity = constructNodeSimilarity(
            graph,
            parameters1
        );

        Set<String> result = nodeSimilarity
            .compute()
            .streamResult()
            .map(NodeSimilarityTest::resultString)
            .collect(Collectors.toSet());

        assertThat(result).contains("0,1 0.500000");

        var parameters2 = new NodeSimilarityParameters(
            new Concurrency(1),
            new OverlapSimilarityComputer(1E-42),
            1,
            Integer.MAX_VALUE,
            10,
            0,
            true,
            true,
            false,
            null
        );
        nodeSimilarity = constructNodeSimilarity(graph, parameters2);

        result = nodeSimilarity.compute()
            .streamResult().map(NodeSimilarityTest::resultString).collect(Collectors.toSet());

        assertThat(result).contains("0,1 0.333333");

    }

    static Stream<Arguments> degreeCutoffInput() {
        return Stream.of(
            arguments(2, Integer.MAX_VALUE,
                new String[]{
                    resultString(0, 1, 0.666667),
                    resultString(3, 0, 1.0), resultString(3, 1, 0.666667),
                    resultString(1, 3, 0.666667), resultString(1, 0, 0.666667),
                    resultString(0, 3, 1.0)
                }
            ),
            arguments(1, 2,
                new String[]{
                    resultString(1, 2, 0.0),
                    resultString(2, 1, 0.0)
                }  //their similarity is zero, but we still return them
            ),
            arguments(3, 3,
                new String[]{
                    resultString(0, 3, 1.0),
                    resultString(3, 0, 1.0)
                }
            )
        );
    }

    @ParameterizedTest
    @MethodSource("degreeCutoffInput")
    void shouldWorkForAllDegreeBoundsCombinations(int lowBound, int upperBound, String... expectedOutput) {
        var parameters = new NodeSimilarityParameters(
            new Concurrency(4),
            new JaccardSimilarityComputer(0.0),
            lowBound,
            upperBound,
            10,
            0,
            true,
            false,
            false,
            null
        );

        NodeSimilarity nodeSimilarity = constructNodeSimilarity(
            naturalGraph,
            parameters
        );

        Set<String> result = nodeSimilarity
            .compute()
            .streamResult()
            .map(NodeSimilarityTest::resultString)
            .collect(Collectors.toSet());

        assertThat(result).containsExactlyInAnyOrder(expectedOutput);
    }

    private static NodeSimilarity constructNodeSimilarity(
        Graph graph,
        NodeSimilarityParameters parameters
    ) {
        var wccStub = new WccStub(TerminationFlag.RUNNING_TRUE, new AlgorithmMachinery());

        return new NodeSimilarity(
            graph,
            parameters,
            DefaultPool.INSTANCE,
            ProgressTracker.NULL_TRACKER,
            NodeFilter.ALLOW_EVERYTHING,
            NodeFilter.ALLOW_EVERYTHING,
            TerminationFlag.RUNNING_TRUE,
            wccStub
        );
    }
}
