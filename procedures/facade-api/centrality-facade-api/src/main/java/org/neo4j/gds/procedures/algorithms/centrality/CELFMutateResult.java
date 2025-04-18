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
package org.neo4j.gds.procedures.algorithms.centrality;

import org.neo4j.gds.applications.algorithms.machinery.AlgorithmProcessingTimings;
import org.neo4j.gds.result.AbstractResultBuilder;

import java.util.Map;

public final class CELFMutateResult {
    public final long mutateMillis;
    public final long nodePropertiesWritten;
    public final long computeMillis;
    public final double totalSpread;
    public final long nodeCount;
    public final Map<String, Object> configuration;

    public CELFMutateResult(
        long mutateMillis,
        long nodePropertiesWritten,
        long computeMillis,
        double totalSpread,
        long nodeCount,
        Map<String, Object> configuration
    ) {
        this.mutateMillis = mutateMillis;
        this.nodePropertiesWritten = nodePropertiesWritten;
        this.computeMillis = computeMillis;
        this.totalSpread = totalSpread;
        this.nodeCount = nodeCount;
        this.configuration = configuration;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CELFMutateResult emptyFrom(AlgorithmProcessingTimings timings, Map<String, Object> configurationMap) {
        return new CELFMutateResult(
            timings.sideEffectMillis,
            0,
            timings.computeMillis,
            0,
            0,
            configurationMap
        );
    }

    public static class Builder extends AbstractResultBuilder<CELFMutateResult> {
        private double totalSpread;

        public Builder withTotalSpread(double totalSpread) {
            this.totalSpread = totalSpread;
            return this;
        }

        public CELFMutateResult build() {
            return new CELFMutateResult(mutateMillis, nodePropertiesWritten,
                computeMillis,
                totalSpread,
                nodeCount,
                config.toMap()
            );
        }
    }
}
