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
package org.neo4j.gds.core.utils.progress.tasks;

import org.junit.jupiter.api.Test;
import org.neo4j.gds.TestLog;
import org.neo4j.gds.TestProgressLogger;
import org.neo4j.gds.core.utils.ProgressLogger;
import org.neo4j.gds.core.utils.RenamesCurrentThread;
import org.neo4j.gds.core.utils.progress.EmptyTaskRegistryFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class TaskProgressTrackerTest {

    @Test
    void shouldStepThroughSubtasks() {
        var leafTask = Tasks.leaf("leaf1");
        var iterativeTask = Tasks.iterativeFixed("iterative", () -> List.of(Tasks.leaf("leaf2")), 2);
        var rootTask = Tasks.task(
            "root",
            leafTask,
            iterativeTask
        );

        var progressTracker = new TaskProgressTracker(
            rootTask,
            ProgressLogger.NULL_LOGGER,
            EmptyTaskRegistryFactory.INSTANCE
        );

        progressTracker.beginSubTask();
        assertThat(progressTracker.currentSubTask()).isEqualTo(rootTask);
        assertThat(rootTask.status()).isEqualTo(Status.RUNNING);

        progressTracker.beginSubTask();
        assertThat(progressTracker.currentSubTask()).isEqualTo(leafTask);
        assertThat(leafTask.status()).isEqualTo(Status.RUNNING);
        progressTracker.endSubTask();
        assertThat(leafTask.status()).isEqualTo(Status.FINISHED);

        progressTracker.beginSubTask();
        assertThat(progressTracker.currentSubTask()).isEqualTo(iterativeTask);
        assertThat(iterativeTask.status()).isEqualTo(Status.RUNNING);
        progressTracker.endSubTask();
        assertThat(iterativeTask.status()).isEqualTo(Status.FINISHED);

        assertThat(progressTracker.currentSubTask()).isEqualTo(rootTask);

        progressTracker.endSubTask();
        assertThat(rootTask.status()).isEqualTo(Status.FINISHED);
    }

    @Test
    void shouldThrowIfEndMoreTasksThanStarted() {
        var task = Tasks.leaf("leaf");
        var progressTracker = new TaskProgressTracker(
            task,
            ProgressLogger.NULL_LOGGER,
            EmptyTaskRegistryFactory.INSTANCE
        );
        progressTracker.beginSubTask();
        progressTracker.endSubTask();
        assertThatThrownBy(progressTracker::endSubTask)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No more running tasks");
    }

    @Test
    void shouldLogProgress() {
        var task = Tasks.leaf("leaf");
        var progressTracker = new TaskProgressTracker(
            task,
            ProgressLogger.NULL_LOGGER,
            EmptyTaskRegistryFactory.INSTANCE
        );
        progressTracker.beginSubTask();
        progressTracker.logProgress(42);
        assertThat(task.getProgress().progress()).isEqualTo(42);
    }

    @Test
    void shouldCancelSubTasksOnDynamicIterative() {
        var task = Tasks.iterativeDynamic("iterative", () -> List.of(Tasks.leaf("leaf")), 2);
        var progressTracker = new TaskProgressTracker(
            task,
            ProgressLogger.NULL_LOGGER,
            EmptyTaskRegistryFactory.INSTANCE
        );
        progressTracker.beginSubTask();
        assertThat(progressTracker.currentSubTask()).isEqualTo(task);

        var iterativeSubTasks = task.subTasks();

        // visit first iteration leaf
        progressTracker.beginSubTask();
        progressTracker.endSubTask();

        assertThat(iterativeSubTasks).extracting(Task::status).contains(Status.FINISHED);
        assertThat(iterativeSubTasks).extracting(Task::status).contains(Status.PENDING);

        // end task without visiting second iteration leaf
        progressTracker.endSubTask();
        assertThat(iterativeSubTasks).extracting(Task::status).contains(Status.FINISHED);
        assertThat(iterativeSubTasks).extracting(Task::status).contains(Status.CANCELED);
    }

    @Test
    void shouldAssertFailureOnExpectedSubTaskSubString() {
        var task = Tasks.task("Foo", Tasks.leaf("Leaf1"));
        var progressTracker = new TaskProgressTracker(
            task,
            ProgressLogger.NULL_LOGGER,
            EmptyTaskRegistryFactory.INSTANCE
        );
        assertThatThrownBy(() -> progressTracker.beginSubTask("Bar"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("Expected task name to contain `Bar`, but was `Foo`");

        assertThatThrownBy(() -> progressTracker.beginSubTask("Leaf2"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("Expected task name to contain `Leaf2`, but was `Leaf1`");

        assertThatThrownBy(() -> progressTracker.endSubTask("Leaf2"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("Expected task name to contain `Leaf2`, but was `Leaf1`");

        progressTracker.endSubTask(); // call once manually as the call before didn't execute due to the assertion error
        assertThatThrownBy(() -> progressTracker.endSubTask("Bar"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("Expected task name to contain `Bar`, but was `Foo`");
    }

    @Test
    void shouldAssertSuccessOnExpectedSuBTaskSubString() {
        var task = Tasks.task("Foo", Tasks.leaf("Leaf"));
        var progressTracker = new TaskProgressTracker(
            task,
            ProgressLogger.NULL_LOGGER,
            EmptyTaskRegistryFactory.INSTANCE
        );

        assertDoesNotThrow(() -> progressTracker.beginSubTask("Foo"));
        assertDoesNotThrow(() -> progressTracker.beginSubTask("Leaf"));
        assertDoesNotThrow(() -> progressTracker.endSubTask("Leaf"));
        assertDoesNotThrow(() -> progressTracker.endSubTask("Foo"));
    }

    @Test
    void shouldLog100WhenTaskFinishedEarly() {
        try (var ignored = RenamesCurrentThread.renameThread("test")) {
            var task = Tasks.leaf("leaf", 4);
            var logger = new TestProgressLogger(task, 1);
            var progressTracker = new TaskProgressTracker(task, logger, EmptyTaskRegistryFactory.INSTANCE);
            progressTracker.beginSubTask();
            progressTracker.logProgress(1);

            assertThat(logger.getMessages(TestLog.INFO)).contains(
                "[test] leaf :: Start",
                "[test] leaf 25%"
            );

            progressTracker.endSubTask();

            assertThat(logger.getMessages(TestLog.INFO)).contains(
                "[test] leaf 100%",
                "[test] leaf :: Finished"
            );
        }
    }

    @Test
    void shouldLog100OnlyOnLeafTasks() {
        try (var ignored = RenamesCurrentThread.renameThread("test")) {
            var task = Tasks.task("root", Tasks.leaf("leaf", 4));
            var logger = new TestProgressLogger(task, 1);
            var progressTracker = new TaskProgressTracker(task, logger, EmptyTaskRegistryFactory.INSTANCE);

            progressTracker.beginSubTask("root");
            progressTracker.beginSubTask("leaf");
            progressTracker.logProgress(1);
            progressTracker.endSubTask("leaf");
            progressTracker.endSubTask("root");

            assertThat(logger.getMessages(TestLog.INFO)).contains(
                "[test] root :: Start",
                "[test] root :: leaf :: Start",
                "[test] root :: leaf 25%",
                "[test] root :: leaf 100%",
                "[test] root :: leaf :: Finished",
                "[test] root :: Finished"
            );
        }
    }
}
