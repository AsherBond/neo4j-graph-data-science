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
package org.neo4j.gds.procedures.algorithms.pathfinding;

import org.neo4j.gds.applications.algorithms.machinery.AlgorithmProcessingTimings;
import org.neo4j.gds.procedures.algorithms.results.ModeResult;

import java.util.Map;

public record SpanningTreeWriteResult(
        long preProcessingMillis,
        long computeMillis,
        long writeMillis,
        long effectiveNodeCount,
        long relationshipsWritten,
        double totalCost,
        Map<String, Object> configuration
    ) implements ModeResult {

    public static  SpanningTreeWriteResult emptyFrom(
        AlgorithmProcessingTimings timings,
        Map<String, Object> configuration
    )
    {
        return new SpanningTreeWriteResult(
            timings.preProcessingMillis,
            timings.computeMillis,
            0,
            0,
            0,
            0,
            configuration
        );
    }

}
