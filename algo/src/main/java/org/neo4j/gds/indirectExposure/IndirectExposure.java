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
package org.neo4j.gds.indirectExposure;

import org.eclipse.collections.api.block.function.primitive.LongToBooleanFunction;
import org.neo4j.gds.Algorithm;
import org.neo4j.gds.Orientation;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.beta.pregel.Pregel;
import org.neo4j.gds.core.utils.paged.HugeAtomicBitSet;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.degree.DegreeCentrality;
import org.neo4j.gds.degree.DegreeCentralityParameters;
import org.neo4j.gds.degree.DegreeFunction;

import java.util.concurrent.ExecutorService;

public class IndirectExposure extends Algorithm<IndirectExposureResult> {

    private final Graph graph;
    private final IndirectExposureConfig config;
    private final ExecutorService executorService;

    public IndirectExposure(
        Graph graph,
        IndirectExposureConfig config,
        ExecutorService executorService,
        ProgressTracker progressTracker
    ) {
        super(progressTracker);
        this.graph = graph;
        this.config = config;
        this.executorService = executorService;
    }

    @Override
    public IndirectExposureResult compute() {
        this.progressTracker.beginSubTask();

        var sanctionedProperty = graph.nodeProperties(config.sanctionedProperty());
        LongToBooleanFunction isSanctionedFn = (node) -> sanctionedProperty.longValue(node) == 1L;
        DegreeFunction totalTransfersFn = totalTransfersFunction();
        HugeAtomicBitSet visited = HugeAtomicBitSet.create(graph.nodeCount());

        var pregelResult = Pregel.create(
            graph,
            config,
            new IndirectExposureComputation(isSanctionedFn, totalTransfersFn, visited),
            executorService,
            progressTracker,
            terminationFlag
        ).run();

        this.progressTracker.endSubTask();

        return new IndirectExposureResult(
            pregelResult.nodeValues().doubleProperties(IndirectExposureComputation.EXPOSURE),
            pregelResult.ranIterations(),
            pregelResult.didConverge()
        );
    }

    private DegreeFunction totalTransfersFunction() {
        return new DegreeCentrality(
            this.graph,
            this.executorService,
            this.config.concurrency(),
            Orientation.NATURAL,
            this.config.hasRelationshipWeightProperty(),
            DegreeCentralityParameters.DEFAULT_MIN_BATCH_SIZE,
            this.progressTracker
        ).compute().degreeFunction();
    }
}
