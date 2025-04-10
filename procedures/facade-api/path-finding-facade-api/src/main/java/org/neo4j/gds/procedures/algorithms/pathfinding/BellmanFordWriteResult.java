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

import org.neo4j.gds.procedures.algorithms.results.StandardWriteResult;
import org.neo4j.gds.result.AbstractResultBuilder;

import java.util.Map;

public class BellmanFordWriteResult extends StandardWriteResult {
    public final long relationshipsWritten;
    public final boolean containsNegativeCycle;

    public BellmanFordWriteResult(
        long preProcessingMillis,
        long computeMillis,
        long postProcessingMillis,
        long writeMillis,
        long relationshipsWritten,
        boolean containsNegativeCycle,
        Map<String, Object> configuration
    ) {
        super(preProcessingMillis, computeMillis, postProcessingMillis, writeMillis, configuration);
        this.relationshipsWritten = relationshipsWritten;
        this.containsNegativeCycle = containsNegativeCycle;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractResultBuilder<BellmanFordWriteResult> {
        private boolean containsNegativeCycle;

        public Builder withContainsNegativeCycle(boolean containsNegativeCycle) {
            this.containsNegativeCycle = containsNegativeCycle;
            return this;
        }

        @Override
        public BellmanFordWriteResult build() {
            return new BellmanFordWriteResult(
                preProcessingMillis,
                computeMillis,
                0L,
                writeMillis,
                relationshipsWritten,
                containsNegativeCycle,
                config.toMap()
            );
        }
    }
}
