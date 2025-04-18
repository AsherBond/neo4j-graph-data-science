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
package org.neo4j.gds.test;

import org.junit.jupiter.api.Test;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.compat.TestLog;
import org.neo4j.gds.core.utils.progress.JobId;
import org.neo4j.gds.core.utils.progress.TaskRegistry;
import org.neo4j.gds.core.utils.progress.TaskRegistryFactory;
import org.neo4j.gds.core.utils.progress.tasks.Task;
import org.neo4j.gds.extension.GdlExtension;
import org.neo4j.gds.extension.GdlGraph;
import org.neo4j.gds.extension.Inject;
import org.neo4j.gds.logging.GdsTestLog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.neo4j.gds.assertj.Extractors.removingThreadId;
import static org.neo4j.gds.assertj.Extractors.replaceTimings;

@GdlExtension
class ProgressTrackingTest {

    @GdlGraph
    static String GDL =
        "CREATE " +
        " ()-[:REL]->()," +
        " ()-[:REL2]->(),";

    @Inject
    private Graph graph;

    @Test
    void shouldLogProgress() {
        var factory = new TestAlgorithmFactory<>();
        var testConfig = TestConfigImpl.builder().logProgress(true).build();
        var log = new GdsTestLog();

        factory.build(graph, testConfig, log, TaskRegistryFactory.empty()).compute();

        assertThat(log.getMessages(TestLog.INFO))
            .extracting(removingThreadId())
            .extracting(replaceTimings())
            .containsExactly(
                "TestAlgorithm :: Start",
                "TestAlgorithm 50%",
                "TestAlgorithm 100%",
                "TestAlgorithm :: Finished"
            );

    }

    @Test
    void shouldNotLogProgress() {
        var factory = new TestAlgorithmFactory<>();
        var testConfig = TestConfigImpl.builder().logProgress(false).build();
        var log = new GdsTestLog();

        TaskRegistryFactory taskRegistryFactoryMock = mock(TaskRegistryFactory.class);
        TaskRegistry taskRegistryMock = mock(TaskRegistry.class);
        doReturn(taskRegistryMock).when(taskRegistryFactoryMock).newInstance(any(JobId.class));

        factory.build(graph, testConfig, log, taskRegistryFactoryMock).compute();

        assertThat(log.getMessages(TestLog.INFO))
            .as("When `logProgress` is set to `false` there should only be `start` and `finished` log messages")
            .extracting(removingThreadId())
            .extracting(replaceTimings())
            .containsExactly(
                "TestAlgorithm :: Start",
                "TestAlgorithm :: Finished"
            );

        // Now make sure that the tasks have been registered
        verify(taskRegistryMock, times(1)).registerTask(any(Task.class));
        verify(taskRegistryMock, times(1)).markCompleted();
    }
}
