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
package org.neo4j.gds.procedures.algorithms.miscellaneous;

import org.neo4j.gds.api.Graph;
import org.neo4j.gds.applications.algorithms.machinery.AlgorithmProcessingTimings;
import org.neo4j.gds.applications.algorithms.machinery.StatsResultBuilder;
import org.neo4j.gds.scaleproperties.ScalePropertiesResult;
import org.neo4j.gds.scaleproperties.ScalePropertiesStatsConfig;

import java.util.Optional;
import java.util.stream.Stream;

class ScalePropertiesResultBuilderForStatsMode implements StatsResultBuilder<ScalePropertiesResult, Stream<ScalePropertiesStatsResult>> {
    private final ScalePropertiesStatsConfig configuration;
    private final boolean shouldDisplayScalerStatistics;

    ScalePropertiesResultBuilderForStatsMode(
        ScalePropertiesStatsConfig configuration,
        boolean shouldDisplayScalerStatistics
    ) {
        this.configuration = configuration;
        this.shouldDisplayScalerStatistics = shouldDisplayScalerStatistics;
    }

    @Override
    public Stream<ScalePropertiesStatsResult> build(
        Graph graph,
        Optional<ScalePropertiesResult> result,
        AlgorithmProcessingTimings timings
    ) {
        if (result.isEmpty()) return Stream.of(ScalePropertiesStatsResult.emptyFrom(timings, configuration.toMap()));

        var scalePropertiesResult = result.get();

        var scalePropertiesStatsResult =  ScalePropertiesStatsResult.create(
            shouldDisplayScalerStatistics ? scalePropertiesResult.scalerStatistics() : null,
            timings,
            configuration.toMap()
        );

        return Stream.of(scalePropertiesStatsResult);
    }
}
