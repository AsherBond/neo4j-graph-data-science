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
import org.neo4j.gds.procedures.algorithms.results.WriteNodePropertiesResult;

import java.util.Map;

public record ApproxMaxKCutWriteResult(
    double cutCost,
    long preProcessingMillis,
    long computeMillis,
    long postProcessingMillis,
    long writeMillis,
    long nodePropertiesWritten,
    Map<String, Object> configuration
) implements WriteNodePropertiesResult {

    public static ApproxMaxKCutWriteResult emptyFrom(
        AlgorithmProcessingTimings timings,
        Map<String, Object> configurationMap
    ) {
        return new ApproxMaxKCutWriteResult(
            0,
            timings.preProcessingMillis,
            timings.computeMillis,
            0,
            timings.sideEffectMillis,
            0,
            configurationMap
        );
    }
}
