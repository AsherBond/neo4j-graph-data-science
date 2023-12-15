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
package org.neo4j.gds.procedures.embeddings.node2vec;

import org.neo4j.gds.result.AbstractResultBuilder;

import java.util.List;
import java.util.Map;

public final class Node2VecWriteResult {

    public final long nodeCount;
    public final long nodePropertiesWritten;
    public final long preProcessingMillis;
    public final long computeMillis;
    public final long writeMillis;
    public final Map<String, Object> configuration;
    public final List<Double> lossPerIteration;

    public Node2VecWriteResult(
        long nodeCount,
        long nodePropertiesWritten,
        long preProcessingMillis,
        long computeMillis,
        long writeMillis,
        Map<String, Object> configuration,
        List<Double> lossPerIteration
    ) {
        this.nodeCount = nodeCount;
        this.nodePropertiesWritten = nodePropertiesWritten;
        this.preProcessingMillis = preProcessingMillis;
        this.computeMillis = computeMillis;
        this.writeMillis = writeMillis;
        this.configuration = configuration;
        this.lossPerIteration = lossPerIteration;
    }

    public static class Builder extends AbstractResultBuilder<Node2VecWriteResult> {

        private List<Double> lossPerIteration;

        @Override
        public Node2VecWriteResult build() {
            return new Node2VecWriteResult(
                nodeCount,
                nodePropertiesWritten,
                preProcessingMillis,
                computeMillis,
                writeMillis,
                config.toMap(),
                lossPerIteration
            );
        }

        public Builder withLossPerIteration(List<Double> lossPerIteration) {
            this.lossPerIteration = lossPerIteration;
            return this;
        }
    }
}
