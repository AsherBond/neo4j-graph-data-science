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
package org.neo4j.gds.procedures.algorithms.results;

import java.util.Map;

public class StandardMutateResult {
    public final long mutateMillis;
    public final long preProcessingMillis;
    public final long computeMillis;
    public final long postProcessingMillis;
    public final Map<String, Object> configuration;

    public StandardMutateResult(
        long preProcessingMillis,
        long computeMillis,
        long postProcessingMillis,
        long mutateMillis,
        Map<String, Object> configuration
    ) {
        this.mutateMillis = mutateMillis;
        this.postProcessingMillis = postProcessingMillis;
        this.preProcessingMillis = preProcessingMillis;
        this.configuration = configuration;
        this.computeMillis = computeMillis;
    }
}
