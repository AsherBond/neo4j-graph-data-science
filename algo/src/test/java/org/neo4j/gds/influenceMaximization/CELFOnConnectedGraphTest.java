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
package org.neo4j.gds.influenceMaximization;

import org.assertj.core.api.SoftAssertions;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.neo4j.gds.Orientation;
import org.neo4j.gds.applications.algorithms.centrality.CentralityAlgorithms;
import org.neo4j.gds.applications.algorithms.machinery.ProgressTrackerCreator;
import org.neo4j.gds.applications.algorithms.machinery.RequestScopedDependencies;
import org.neo4j.gds.compat.TestLog;
import org.neo4j.gds.core.concurrency.Concurrency;
import org.neo4j.gds.core.concurrency.DefaultPool;
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
import org.neo4j.gds.termination.TerminationFlag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.gds.assertj.Extractors.removingThreadId;
import static org.neo4j.gds.assertj.Extractors.replaceTimings;


@GdlExtension
class CELFOnConnectedGraphTest {
    @GdlGraph(orientation = Orientation.NATURAL)
    private static final String DB_CYPHER =
        "CREATE " +
        "  (a:Node)" +
        ", (b:Node)" +
        ", (c:Node)" +
        ", (d:Node)" +
        ", (e:Node)" +
        ", (a)-[:R]->(b) " +
        ", (a)-[:R]->(c) " +
        ", (a)-[:R]->(d) " +
        ", (d)-[:R]->(e) ";


    @Inject
    private TestGraph graph;

    @Test
    void testSpreadWithSeed1() {
        //monte-carlo 1
        // a->d (0.0264) : NOT a->b (0.8833) a->c (0.4315) d->e (0.8833)
        //monte-carlo 2
        // NOTHING ACTIVATED: a->b  (0.5665)  a->c  (0.7457) a->d (0.9710)  d->e (0.5665)

        //monte carlo 3
        // NOTHING ACTIVATED: a->b (0.5911) a->c (0.7491)  a->d(0.595) d-> (0.5911)

        //round 1         MC1                MC2                 MC3
        // gain[a] = (1 (a) + 1(d))      + ( 1(a) )     +     ( 1(a))
        //            = 2 + 1 + 1 = 4/3 = 1.33

        //round 2         MC1                               MC2                 MC3
        // gain[b|a] :       1(b)                           1(b)                1(b)  = 1
        // gain[c|a]:        1(c)                           1(c)                1(c)  = 1
        // gain[e|a]:        1(e)                           1(e)                1(e)  = 1
        //gain[d|a] :        0 {a already activates d}      1(d)                1(d) =  2/3 =0.667

        //choose {b,c,e} --> choose b

        //round 3
        // gain[c|a,b]:        1(c)                           1(c)                1(c)  = 1
        // gain[e|a,b]:        1(e)                           1(e)                1(e)  = 1
        // gain[d|a,b] :        0 {a already activates d}      1(d)                1(d)    =  2/3 =0.667

        //choose{c,e} -->choose c

        //round 4
        // gain[e|a,b,c]:        1(e)                           1(e)                1(e)  = 1
        // gain[d|a,b,d] :        0 {a already activates d}      1(d)                1(d)    =  2/3 =0.667

        //choose c

        //round 5
        // gain[d|a,b,d,e] :        0 {a already activates d}      1(d)                1(d)    =  2/3 =0.667
        IdFunction idFunction = variable -> graph.toMappedNodeId(variable);


        var parameters = new CELFParameters(
            5,
            0.2,
            3,
            new Concurrency(2),
            0L,
            10
        );

        var celf = new CELF(
            graph,
            parameters,
            DefaultPool.INSTANCE,
            ProgressTracker.NULL_TRACKER
        );

        var celfResult = celf.compute().seedSetNodes();
        var softAssertions = new SoftAssertions();

        softAssertions
            .assertThat(celfResult.get(idFunction.of("a")))
            .as("spread of a")
            .isEqualTo(4 / 3.0, Offset.offset(1e-5));
        softAssertions
            .assertThat(celfResult.get(idFunction.of("b")))
            .as("spread of b")
            .isEqualTo(1, Offset.offset(1e-5));
        softAssertions
            .assertThat(celfResult.get(idFunction.of("c")))
            .as("spread of c")
            .isEqualTo(1, Offset.offset(1e-5));
        softAssertions
            .assertThat(celfResult.get(idFunction.of("d")))
            .as("spread of d")
            .isEqualTo(2 / 3.0, Offset.offset(1e-5));
        softAssertions
            .assertThat(celfResult.get(idFunction.of("e")))
            .as("spread of e")
            .isEqualTo(1, Offset.offset(1e-5));

        softAssertions.assertAll();
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

        var config = InfluenceMaximizationStreamConfigImpl.builder().seedSetSize((int) graph.nodeCount()).build();
        centralityAlgorithms.celf(graph, config);

        assertThat(log.getMessages(TestLog.INFO))
            .extracting(removingThreadId())
            .extracting(replaceTimings())
            .containsExactly(
                "CELF :: Start",
                "CELF :: Greedy :: Start",
                "CELF :: Greedy 20%",      // 5 nodes  so 20% for each
                "CELF :: Greedy 40%",
                "CELF :: Greedy 60%",
                "CELF :: Greedy 80%",
                "CELF :: Greedy 100%",
                "CELF :: Greedy :: Finished",
                "CELF :: LazyForwarding :: Start",
                "CELF :: LazyForwarding 25%",    //4 iterations so 20% for each
                "CELF :: LazyForwarding 50%",
                "CELF :: LazyForwarding 75%",
                "CELF :: LazyForwarding 100%",
                "CELF :: LazyForwarding :: Finished",
                "CELF :: Finished"
            );
    }
}
