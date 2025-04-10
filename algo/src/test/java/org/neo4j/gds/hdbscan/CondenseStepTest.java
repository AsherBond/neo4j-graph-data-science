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
package org.neo4j.gds.hdbscan;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.neo4j.gds.collections.ha.HugeDoubleArray;
import org.neo4j.gds.collections.ha.HugeLongArray;
import org.neo4j.gds.compat.TestLog;
import org.neo4j.gds.core.concurrency.Concurrency;
import org.neo4j.gds.core.utils.logging.LoggerForProgressTrackingAdapter;
import org.neo4j.gds.core.utils.progress.EmptyTaskRegistryFactory;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.core.utils.progress.tasks.TaskProgressTracker;
import org.neo4j.gds.logging.GdsTestLog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.gds.assertj.Extractors.removingThreadId;
import static org.neo4j.gds.assertj.Extractors.replaceTimings;

class CondenseStepTest {

    @Test
    void minClusterSizeTwo() {
        var nodeCount = 7L;
        var root = 12L;
        var left = HugeLongArray.of(5, 4, 2, 9, 0, 11);
        var right = HugeLongArray.of(6, 7, 3, 8, 1, 10);
        var lambda = HugeDoubleArray.of(7d, 8d, 9d, 10d, 11d, 12d);
        var size = HugeLongArray.of(2, 3, 2, 5, 2, 7);

        var clusterHierarchy = new ClusterHierarchy(root, left, right, lambda, size, nodeCount);

        var condensedTree = new CondenseStep(nodeCount, ProgressTracker.NULL_TRACKER)
            .condense(clusterHierarchy, 2L);

        assertThat(condensedTree.root()).isEqualTo(7L);
        assertThat(condensedTree.maximumClusterId()).isEqualTo(11L);

        assertThat(condensedTree.size(7L)).isEqualTo(7L);

        assertThat(condensedTree.parent(8L)).isEqualTo(7L);
        assertThat(condensedTree.lambda(8L)).isEqualTo(12d);
        assertThat(condensedTree.size(8L)).isEqualTo(2L);
        assertThat(condensedTree.parent(9L)).isEqualTo(7L);
        assertThat(condensedTree.lambda(9L)).isEqualTo(12d);
        assertThat(condensedTree.size(9L)).isEqualTo(5L);

        assertThat(condensedTree.parent(10L)).isEqualTo(9L);
        assertThat(condensedTree.size(10L)).isEqualTo(2L);
        assertThat(condensedTree.lambda(10L)).isEqualTo(10d);

        assertThat(condensedTree.parent(11L)).isEqualTo(9L);
        assertThat(condensedTree.lambda(11L)).isEqualTo(10d);
        assertThat(condensedTree.size(11L)).isEqualTo(3L);

        assertThat(condensedTree.fellOutOf(0L)).isEqualTo(8L);
        assertThat(condensedTree.lambda(0L)).isEqualTo(11d);
        assertThat(condensedTree.fellOutOf(1L)).isEqualTo(8L);
        assertThat(condensedTree.lambda(1L)).isEqualTo(11d);

        assertThat(condensedTree.fellOutOf(2L)).isEqualTo(10L);
        assertThat(condensedTree.lambda(2L)).isEqualTo(9d);
        assertThat(condensedTree.fellOutOf(3L)).isEqualTo(10L);
        assertThat(condensedTree.lambda(3L)).isEqualTo(9d);

        assertThat(condensedTree.fellOutOf(4L)).isEqualTo(11L);
        assertThat(condensedTree.lambda(4L)).isEqualTo(8d);
        assertThat(condensedTree.fellOutOf(5L)).isEqualTo(11L);
        assertThat(condensedTree.lambda(5L)).isEqualTo(7d);
        assertThat(condensedTree.fellOutOf(6L)).isEqualTo(11L);
        assertThat(condensedTree.lambda(6L)).isEqualTo(7d);
    }

    @Test
    void minClusterSizeThree() {
        var nodeCount = 7L;
        var root = 12L;
        var left = HugeLongArray.of(5, 4, 2, 9, 0, 11);
        var right = HugeLongArray.of(6, 7, 3, 8, 1, 10);
        var lambda = HugeDoubleArray.of(7d, 8d, 9d, 10d, 11d, 12d);
        var size = HugeLongArray.of(2, 3, 2, 5, 2, 7);

        var clusterHierarchy = new ClusterHierarchy(root, left, right, lambda, size, nodeCount);

        var condensedTree = new CondenseStep(nodeCount,ProgressTracker.NULL_TRACKER)
            .condense(clusterHierarchy, 3L);

        assertThat(condensedTree.root()).isEqualTo(7L);
        assertThat(condensedTree.maximumClusterId()).isEqualTo(7L);

        assertThat(condensedTree.size(7L)).isEqualTo(7L);

        assertThat(condensedTree.fellOutOf(0L)).isEqualTo(7L);
        assertThat(condensedTree.lambda(0L)).isEqualTo(12d);
        assertThat(condensedTree.fellOutOf(1L)).isEqualTo(7L);
        assertThat(condensedTree.lambda(1L)).isEqualTo(12);

        assertThat(condensedTree.fellOutOf(2L)).isEqualTo(7L);
        assertThat(condensedTree.lambda(2L)).isEqualTo(10d);
        assertThat(condensedTree.fellOutOf(3L)).isEqualTo(7L);
        assertThat(condensedTree.lambda(3L)).isEqualTo(10d);

        assertThat(condensedTree.fellOutOf(4L)).isEqualTo(7L);
        assertThat(condensedTree.lambda(4L)).isEqualTo(8d);
        assertThat(condensedTree.fellOutOf(5L)).isEqualTo(7L);
        assertThat(condensedTree.lambda(5L)).isEqualTo(8d);
        assertThat(condensedTree.fellOutOf(6L)).isEqualTo(7L);
        assertThat(condensedTree.lambda(6L)).isEqualTo(8d);
    }

    @Test
    void shouldLogProgress(){
        var nodeCount = 7L;
        var root = 12L;
        var left = HugeLongArray.of(5, 4, 2, 9, 0, 11);
        var right = HugeLongArray.of(6, 7, 3, 8, 1, 10);
        var lambda = HugeDoubleArray.of(7d, 8d, 9d, 10d, 11d, 12d);
        var size = HugeLongArray.of(2, 3, 2, 5, 2, 7);

        var progressTask = HDBScanProgressTrackerCreator.condenseTask("condense",nodeCount);
        var log = new GdsTestLog();
        var progressTracker = new TaskProgressTracker(progressTask, new LoggerForProgressTrackingAdapter(log), new Concurrency(1), EmptyTaskRegistryFactory.INSTANCE);


        var clusterHierarchy = new ClusterHierarchy(root, left, right, lambda, size, nodeCount);

        new CondenseStep(nodeCount,progressTracker).condense(clusterHierarchy, 3L);

        Assertions.assertThat(log.getMessages(TestLog.INFO))
            .extracting(removingThreadId())
            .extracting(replaceTimings())
            .containsExactly(
                "condense :: Start",
                "condense 16%",
                "condense 33%",
                "condense 50%",
                "condense 100%",
                "condense :: Finished"
            );
    }
}
