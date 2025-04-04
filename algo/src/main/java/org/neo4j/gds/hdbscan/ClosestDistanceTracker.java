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

import org.neo4j.gds.collections.ha.HugeDoubleArray;
import org.neo4j.gds.collections.ha.HugeLongArray;

final class ClosestDistanceTracker {

    private final HugeDoubleArray componentClosestDistance;
    private final HugeLongArray componentInsideBestNode;
    private final HugeLongArray componentOutsideBestNode;
    private boolean updated = false;


    private ClosestDistanceTracker(
        HugeDoubleArray componentClosestDistance,
        HugeLongArray componentInsideBestNode,
        HugeLongArray componentOutsideBestNode
    ) {
        this.componentClosestDistance = componentClosestDistance;
        this.componentInsideBestNode = componentInsideBestNode;
        this.componentOutsideBestNode = componentOutsideBestNode;
        reset(componentClosestDistance.size());
    }

    static ClosestDistanceTracker create(long size) {

        var componentClosestDistance = HugeDoubleArray.newArray(size);
        var componentInsideBestNode = HugeLongArray.newArray(size);
        var componentOutsideBestNode = HugeLongArray.newArray(size);

        return new ClosestDistanceTracker(
            componentClosestDistance,
            componentInsideBestNode,
            componentOutsideBestNode
        );
    }

    static ClosestDistanceTracker create(long size, HugeDoubleArray cores, CoreResult coreResult) {
        var tracker = create(size);
        tracker.reset(size);
        for (long u = 0; u < size; ++u) {
            var neighbors = coreResult.neighboursOf(u);
            for (int k = neighbors.length - 1; k >= 0; --k) {
                var neighbor = neighbors[k].id();
                var adaptedDistance = Math.max(cores.get(u), cores.get(neighbor));
                tracker.tryToAssign(u, u, neighbor, adaptedDistance);
            }
        }
        tracker.updated();

        return tracker;

    }

    private void updated() {
        this.updated = true;
    }

    private void notUpdated() {
        this.updated = false;
    }

    boolean isNotUpdated() {
        return !updated;
    }

    void reset(long upTo) {
        for (long u = 0; u < upTo; ++u) {
            resetComponent(u);
        }
        notUpdated();
    }

    void resetComponent(long u) {
        componentClosestDistance.set(u, Double.MAX_VALUE);
        componentInsideBestNode.set(u, -1);
        componentOutsideBestNode.set(u, -1);
    }

    boolean consider(long comp1, long comp2, long p1, long p2, double distance) {
        var assigned = tryToAssign(comp1, p1, p2, distance);
        tryToAssign(comp2, p2, p1, distance);
        return  assigned;
    }

    synchronized boolean tryToAssign(long comp, long pInside, long pOutside, double distance) {
        var best = componentClosestDistance.get(comp);
        if (best > distance) {
            componentClosestDistance.set(comp, distance);
            componentInsideBestNode.set(comp, pInside);
            componentOutsideBestNode.set(comp, pOutside);
            return true;
        }
        return false;

    }

    double componentClosestDistance(long componentId) {
        return componentClosestDistance.get(componentId);
    }

    long componentInsideBestNode(long componentId) {
        return componentInsideBestNode.get(componentId);
    }

    long componentOutsideBestNode(long componentId) {
        return componentOutsideBestNode.get(componentId);
    }

}
