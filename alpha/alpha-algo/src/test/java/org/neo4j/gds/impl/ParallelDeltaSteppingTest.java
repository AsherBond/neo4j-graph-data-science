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
package org.neo4j.gds.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.gds.AlgoTestBase;
import org.neo4j.gds.PropertyMapping;
import org.neo4j.gds.StoreLoaderBuilder;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.graphbuilder.GraphBuilder;
import org.neo4j.gds.graphbuilder.GridBuilder;

import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * The test creates a grid of nodes and computes a reference array
 * of shortest paths using one thread. It then compares the reference
 * against the result of several parallel computations to provoke
 * concurrency errors if any.
 */
class ParallelDeltaSteppingTest extends AlgoTestBase {

    private static final String PROPERTY = "property";
    private static final String LABEL = "Node";
    private static final String RELATIONSHIP = "REL";

    private static GridBuilder gridBuilder;
    private static Graph graph;
    private static double[] reference;
    private static long rootNodeId;

    @BeforeEach
    void setup() {
        gridBuilder = GraphBuilder.create(db)
            .setLabel(LABEL)
            .setRelationship(RELATIONSHIP)
            .newGridBuilder()
            .createGrid(50, 50)
            .forEachRelInTx(rel -> {
                rel.setProperty(PROPERTY, Math.random() * 5); // (0-5)
            });
        gridBuilder.close();

        rootNodeId = gridBuilder.getLineNodes()
            .get(0)
            .get(0)
            .getId();

        graph = new StoreLoaderBuilder()
            .api(db)
            .addNodeLabel(LABEL)
            .addRelationshipType(RELATIONSHIP)
            .addRelationshipProperty(PropertyMapping.of(PROPERTY, 1.0))
            .build()
            .graph();

        reference = compute(1);
    }

    @Test
    void testParallelBehaviour() {
        final int n = 20;
        for (int i = 0; i < n; i++) {
            assertArrayEquals(
                reference,
                compute((n % 7) + 2),
                0.001,
                "error in iteration " + i
            );
        }
    }

    private double[] compute(int threads) {
        return new ShortestPathDeltaStepping(graph, rootNodeId, 2.5, ProgressTracker.NULL_TRACKER)
            .withExecutorService(Executors.newFixedThreadPool(threads))
            .compute()
            .getShortestPaths();
    }
}
