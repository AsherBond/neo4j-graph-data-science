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
package org.neo4j.gds.projection;

import org.neo4j.gds.core.loading.construction.NodeLabelToken;
import org.neo4j.gds.core.loading.construction.NodeLabelTokens;
import org.neo4j.gds.values.CypherNodeLabelTokens;
import org.neo4j.values.AnyValue;
import org.neo4j.values.SequenceValue;
import org.neo4j.values.storable.BooleanValue;
import org.neo4j.values.storable.TextArray;
import org.neo4j.values.storable.TextValue;
import org.neo4j.values.storable.VectorValue;

enum ReadNodeLabels implements PartialValueMapper<NodeLabelToken> {
    INSTANCE;

    @Override
    public NodeLabelToken unsupported(AnyValue value) {
        return NodeLabelTokens.invalid();
    }

    @Override
    public NodeLabelToken mapSequence(SequenceValue value) {
        if (value.isEmpty()) {
            return NodeLabelTokens.empty();
        }

        return CypherNodeLabelTokens.of(value);
    }

    @Override
    public NodeLabelToken mapNoValue() {
        return NodeLabelTokens.missing();
    }

    @Override
    public NodeLabelToken mapBoolean(BooleanValue value) {
        if (value.booleanValue()) {
            throw new IllegalArgumentException(
                "Using `true` to load all labels is deprecated, use `{ sourceNodeLabels: labels(s) }` instead"
            );
        }
        return NodeLabelTokens.empty();
    }

    @Override
    public NodeLabelToken mapVector(VectorValue vectorValue) {
        return NodeLabelTokens.missing();
    }

    @Override
    public NodeLabelToken mapText(TextValue value) {
        return CypherNodeLabelTokens.of(value);
    }

    @Override
    public NodeLabelToken mapTextArray(TextArray value) {
        return CypherNodeLabelTokens.of(value);
    }


}
