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
package org.neo4j.gds.core.huge;

import org.neo4j.gds.api.IdMapping;

public class DirectIdMapping implements IdMapping {
    private final long nodeCount;

    public DirectIdMapping(long nodeCount) {
        this.nodeCount = nodeCount;
    }

    @Override
    public long toMappedNodeId(long nodeId) {
        return nodeId;
    }

    @Override
    public long toOriginalNodeId(long nodeId) {
        return nodeId;
    }

    @Override
    public long toRootNodeId(long nodeId) {
        return nodeId;
    }

    @Override
    public long highestNeoId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean contains(final long nodeId) {
        return nodeId < nodeCount;
    }

    @Override
    public long nodeCount() {
        return nodeCount;
    }

    @Override
    public long rootNodeCount() {
        return nodeCount;
    }
}
