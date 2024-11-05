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
package org.neo4j.gds.sllp;

import org.neo4j.gds.Algorithm;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.beta.pregel.ImmutablePregelResult;
import org.neo4j.gds.beta.pregel.Pregel;
import org.neo4j.gds.beta.pregel.PregelResult;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;

import java.util.Optional;
import java.util.concurrent.ExecutorService;


public class SpeakerListenerLPA extends Algorithm<PregelResult> {

    private final Graph graph;
    private final SpeakerListenerLPAConfig config;
    private final ExecutorService executorService;
    private final Optional<Long> seed;

    public SpeakerListenerLPA(
        Graph graph,
        SpeakerListenerLPAConfig config,
        ExecutorService executorService,
        ProgressTracker progressTracker,
        Optional<Long> seed
    ) {
        super(progressTracker);
        this.graph = graph;
        this.config = config;
        this.executorService = executorService;
        this.seed = seed;

    }

    @Override
    public PregelResult compute() {

        var computation = new SpeakerListenerLPAComputation(seed);

        var pregelResult = Pregel.create(
            graph,
            config,
            computation,
            executorService,
            progressTracker,
            terminationFlag
        ).run();

        this.progressTracker.endSubTask();

        return ImmutablePregelResult.builder()
            .nodeValues(pregelResult.nodeValues())
            .didConverge(pregelResult.didConverge())
            .ranIterations(pregelResult.ranIterations())
            .build();
    }
}
