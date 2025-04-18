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
package org.neo4j.gds.procedures.algorithms.community;

import org.neo4j.gds.applications.algorithms.machinery.AlgorithmProcessingTimings;

import java.util.Map;

public final class HDBScanStatsResult  {
    public final long nodeCount;
    public final long numberOfClusters;
    public final long numberOfNoisePoints;
    public final long postProcessingMillis;
    public final long preProcessingMillis;
    public final long computeMillis;
    public final Map<String, Object> configuration;

    public HDBScanStatsResult(
        long nodeCount,
        long numberOfClusters,
        long numberOfNoisePoints,
        long preProcessingMillis,
        long computeMillis,
        long postProcessingMillis,
        Map<String, Object> configuration

    ) {
        this.nodeCount = nodeCount;
        this.numberOfClusters = numberOfClusters;
        this.numberOfNoisePoints = numberOfNoisePoints;
        this.preProcessingMillis =  preProcessingMillis;
        this.computeMillis = computeMillis;
        this.postProcessingMillis = postProcessingMillis;
        this.configuration = configuration;
    }

    static HDBScanStatsResult emptyFrom(AlgorithmProcessingTimings timings, Map<String, Object> configurationMap) {
        return new HDBScanStatsResult(
            0,
            0,
            0,
            timings.preProcessingMillis,
            timings.computeMillis,
            0,
            configurationMap
        );
    }
}
