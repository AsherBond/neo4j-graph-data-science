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
package org.neo4j.gds.closeness;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.neo4j.gds.TestProgressTracker;
import org.neo4j.gds.core.concurrency.Concurrency;
import org.neo4j.gds.core.concurrency.DefaultPool;
import org.neo4j.gds.core.utils.logging.LoggerForProgressTrackingAdapter;
import org.neo4j.gds.core.utils.progress.EmptyTaskRegistryFactory;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.extension.GdlExtension;
import org.neo4j.gds.extension.GdlGraph;
import org.neo4j.gds.extension.IdFunction;
import org.neo4j.gds.extension.Inject;
import org.neo4j.gds.extension.TestGraph;
import org.neo4j.gds.logging.GdsTestLog;
import org.neo4j.gds.termination.TerminationFlag;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.gds.assertj.Extractors.removingThreadId;
import static org.neo4j.gds.compat.TestLog.INFO;

/**
 * Graph:
 *
 * (A)<-->(B)<-->(C)<-->(D)<-->(E)
 *
 * Calculation:
 *
 * N = 5        // number of nodes
 * k = N-1 = 4  // used for normalization
 *
 * A     B     C     D     E
 * --|-----------------------------
 * A | 0     1     2     3     4       // farness between each pair of nodes
 * B | 1     0     1     2     3
 * C | 2     1     0     1     2
 * D | 3     2     1     0     1
 * E | 4     3     2     1     0
 * --|-----------------------------
 * S | 10    7     6     7     10      // sum each column
 * ==|=============================
 * k/S| 0.4  0.57  0.67  0.57   0.4     // normalized centrality
 */
@GdlExtension
class ClosenessCentralityTest {

    @GdlGraph
    private static final String DB_CYPHER =
        "CREATE " +
        "  (a:Node)" +
        ", (b:Node)" +
        ", (c:Node)" +
        ", (d:Node)" +
        ", (e:Node)" +

        ", (a)-[:TYPE]->(b)" +
        ", (b)-[:TYPE]->(a)" +
        ", (b)-[:TYPE]->(c)" +
        ", (c)-[:TYPE]->(b)" +
        ", (c)-[:TYPE]->(d)" +
        ", (d)-[:TYPE]->(c)" +
        ", (d)-[:TYPE]->(e)" +
        ", (e)-[:TYPE]->(d)";

    @Inject
    private TestGraph graph;

    @Test
    void testGetCentrality() {
        IdFunction idFunction = graph::toMappedNodeId;

        var algo = new ClosenessCentrality(
            graph,
            new Concurrency(4),
            new DefaultCentralityComputer(),
            DefaultPool.INSTANCE,
            ProgressTracker.NULL_TRACKER,
            TerminationFlag.RUNNING_TRUE
        );

        var result = algo.compute().centralityScoreProvider();

        assertThat(result.applyAsDouble(idFunction.of("a"))).isCloseTo(0.4, Offset.offset(0.01));
        assertThat(result.applyAsDouble(idFunction.of("b"))).isCloseTo(0.57, Offset.offset(0.01));
        assertThat(result.applyAsDouble(idFunction.of("c"))).isCloseTo(0.66, Offset.offset(0.01));
        assertThat(result.applyAsDouble(idFunction.of("d"))).isCloseTo(0.57, Offset.offset(0.01));
        assertThat(result.applyAsDouble(idFunction.of("e"))).isCloseTo(0.4, Offset.offset(0.01));
    }

    @Test
    void shouldLogProgress() {
        var progressTask = ClosenessCentralityTask.create(graph.nodeCount());
        var testLog = new GdsTestLog();
        var progressTracker = new TestProgressTracker(progressTask, new LoggerForProgressTrackingAdapter(testLog), new Concurrency(1), EmptyTaskRegistryFactory.INSTANCE);

        var algo = new ClosenessCentrality(
            graph,
            new Concurrency(4),
            new DefaultCentralityComputer(),
            DefaultPool.INSTANCE,
            progressTracker,
            TerminationFlag.RUNNING_TRUE
        );

        algo.compute();

        List<AtomicLong> progresses = progressTracker.getProgresses();
        assertEquals(3, progresses.size());

        var messagesInOrder = testLog.getMessages(INFO);

        assertThat(messagesInOrder)
            // avoid asserting on the thread id
            .extracting(removingThreadId())
            .containsExactly(
                "Closeness Centrality :: Start",
                "Closeness Centrality :: Farness computation :: Start",
                "Closeness Centrality :: Farness computation 4%",
                "Closeness Centrality :: Farness computation 12%",
                "Closeness Centrality :: Farness computation 20%",
                "Closeness Centrality :: Farness computation 28%",
                "Closeness Centrality :: Farness computation 32%",
                "Closeness Centrality :: Farness computation 36%",
                "Closeness Centrality :: Farness computation 40%",
                "Closeness Centrality :: Farness computation 48%",
                "Closeness Centrality :: Farness computation 52%",
                "Closeness Centrality :: Farness computation 56%",
                "Closeness Centrality :: Farness computation 60%",
                "Closeness Centrality :: Farness computation 64%",
                "Closeness Centrality :: Farness computation 68%",
                "Closeness Centrality :: Farness computation 72%",
                "Closeness Centrality :: Farness computation 76%",
                "Closeness Centrality :: Farness computation 80%",
                "Closeness Centrality :: Farness computation 100%",
                "Closeness Centrality :: Farness computation :: Finished",
                "Closeness Centrality :: Closeness computation :: Start",
                "Closeness Centrality :: Closeness computation 100%",
                "Closeness Centrality :: Closeness computation :: Finished",
                "Closeness Centrality :: Finished"
            );
    }
}
