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
package org.neo4j.gds.bridges;

import org.neo4j.gds.collections.ha.HugeLongArray;
import org.neo4j.gds.collections.ha.HugeObjectArray;
import org.neo4j.gds.mem.Estimate;
import org.neo4j.gds.mem.MemoryEstimateDefinition;
import org.neo4j.gds.mem.MemoryEstimation;
import org.neo4j.gds.mem.MemoryEstimations;
import org.neo4j.gds.mem.MemoryRange;

public class BridgesMemoryEstimateDefinition implements MemoryEstimateDefinition {

    private final boolean shouldComputeComponents;

    public BridgesMemoryEstimateDefinition(boolean shouldComputeComponents) {
        this.shouldComputeComponents = shouldComputeComponents;
    }

    @Override
    public MemoryEstimation memoryEstimation() {

        var builder = MemoryEstimations.builder(Bridges.class);
        builder
            .perNode("tin", HugeLongArray::memoryEstimation)
            .perNode("low", HugeLongArray::memoryEstimation)
            .perNode("visited", Estimate::sizeOfBitset)
                .perNode("bridge", (v)->  v * Estimate.sizeOfInstance(Bridge.class));

        if (shouldComputeComponents){
            builder.add(
                "component split",
                 MemoryEstimations.builder(TreeSizeTracker.class)
                    .perNode("treeSize", HugeLongArray::memoryEstimation)
                     .build());
        }
        builder.rangePerGraphDimension("stack", ((graphDimensions, concurrency) -> {
            long relationshipCount = graphDimensions.relCountUpperBound();
            return MemoryRange.of(
                HugeObjectArray.memoryEstimation(relationshipCount, Estimate.sizeOfInstance(Bridges.StackEvent.class))
            );


        }));

        return builder.build();
    }
}
