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
package org.neo4j.gds.hdbscan;

import org.neo4j.gds.api.properties.nodes.NodePropertyValues;
import org.neo4j.gds.collections.ha.HugeLongArray;

import java.util.function.LongToDoubleFunction;

class FloatKDNodeSupport implements KDNodeSupport {

     private final NodePropertyValues nodePropertyValues;
     private final HugeLongArray ids;
     private final int dimension;

    FloatKDNodeSupport(NodePropertyValues nodePropertyValues, HugeLongArray ids, int dimension) {
        this.nodePropertyValues = nodePropertyValues;
        this.ids = ids;
        this.dimension = dimension;
    }

    @Override
     public AABB create(long start, long end) {
         return AABB.createFromFloat(nodePropertyValues,ids,start,end,dimension);
     }

    @Override
    public LongToDoubleFunction valueAt(int dimensionIndex) {
       return v -> (double)nodePropertyValues.floatArrayValue(ids.get(v))[dimensionIndex];

    }


}
