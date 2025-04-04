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
package org.neo4j.gds.procedures.pipelines;

import org.neo4j.gds.mem.MemoryEstimateDefinition;
import org.neo4j.gds.mem.MemoryEstimation;
import org.neo4j.gds.mem.MemoryEstimations;
import org.neo4j.gds.similarity.knn.KnnMemoryEstimateDefinition;
import org.neo4j.gds.similarity.knn.KnnMemoryEstimationParametersBuilder;
import org.neo4j.gds.similarity.knn.KnnSampler;

public class ApproximateLinkPredictionEstimateDefinition implements MemoryEstimateDefinition {

    private final LinkPredictionPredictPipelineBaseConfig config;

    public ApproximateLinkPredictionEstimateDefinition(LinkPredictionPredictPipelineBaseConfig config) {
        this.config = config;
    }

    @Override
    public MemoryEstimation memoryEstimation() {

        var knnMemoryHolder = new KnnMemoryEstimationParametersBuilder(
            config.sampleRate(),
            config.topK().orElse(10),
            config.derivedInitialSampler().orElse(KnnSampler.SamplerType.UNIFORM)
        );

        var knnEstimation = new KnnMemoryEstimateDefinition(knnMemoryHolder).memoryEstimation();

        return MemoryEstimations.builder(ApproximateLinkPrediction.class.getSimpleName())
            .add(knnEstimation)
            .build();
    }
}
