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
package org.neo4j.graphalgo.core.huge;

import org.apache.commons.lang3.mutable.MutableInt;
import org.neo4j.collection.primitive.PrimitiveLongIterable;
import org.neo4j.collection.primitive.PrimitiveLongIterator;
import org.neo4j.graphalgo.api.FilterGraph;
import org.neo4j.graphalgo.api.IdMapGraph;
import org.neo4j.graphalgo.api.NodeProperties;
import org.neo4j.graphalgo.api.RelationshipConsumer;
import org.neo4j.graphalgo.api.RelationshipIntersect;
import org.neo4j.graphalgo.api.RelationshipWithPropertyConsumer;
import org.neo4j.graphalgo.core.loading.IdMap;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.HugeIntArray;

import java.util.Collection;
import java.util.function.LongPredicate;

public class NodeFilteredGraph extends FilterGraph implements IdMapGraph {

    private static final int NO_DEGREE = -1;

    private final IdMap filteredIdMap;
    private long relationshipCount;
    private final HugeIntArray degreeCache;

    public NodeFilteredGraph(HugeGraph originalGraph, IdMap filteredIdMap, AllocationTracker allocationTracker) {
        super(originalGraph);
        this.relationshipCount = -1;
        this.filteredIdMap = filteredIdMap;
        this.degreeCache = HugeIntArray.newArray(filteredIdMap.nodeCount(), allocationTracker);
        degreeCache.fill(NO_DEGREE);
    }

    public NodeFilteredGraph(HugeGraph originalGraph, IdMap filteredIdMap, HugeIntArray degreeCache) {
        super(originalGraph);

        this.degreeCache = degreeCache;
        this.filteredIdMap = filteredIdMap;
    }

    @Override
    public RelationshipIntersect intersection() {
        return new FilteredGraphIntersectImpl(filteredIdMap, super.intersection());
    }

    @Override
    public PrimitiveLongIterator nodeIterator() {
        return filteredIdMap.nodeIterator();
    }

    @Override
    public Collection<PrimitiveLongIterable> batchIterables(int batchSize) {
        return filteredIdMap.batchIterables(batchSize);
    }

    @Override
    public IdMap idMap() {
        return filteredIdMap;
    }

    @Override
    public void forEachNode(LongPredicate consumer) {
        filteredIdMap.forEachNode(consumer);
    }

    @Override
    public int degree(long nodeId) {
        int cachedDegree = degreeCache.get(nodeId);
        if (cachedDegree != NO_DEGREE) {
            return cachedDegree;
        }

        MutableInt degree = new MutableInt();

        forEachRelationship(nodeId, (s, t) -> {
            degree.increment();
            return true;
        });
        degreeCache.set(nodeId, degree.intValue());

        return degree.intValue();
    }

    @Override
    public long nodeCount() {
        return filteredIdMap.nodeCount();
    }

    @Override
    public long toMappedNodeId(long nodeId) {
        return filteredIdMap.toMappedNodeId(super.toMappedNodeId(nodeId));
    }

    @Override
    public boolean contains(long nodeId) {
        return filteredIdMap.contains(nodeId);
    }

    @Override
    public long toOriginalNodeId(long nodeId) {
        return super.toOriginalNodeId(filteredIdMap.toOriginalNodeId(nodeId));
    }

    @Override
    public void forEachRelationship(long nodeId, RelationshipConsumer consumer) {
        super.forEachRelationship(filteredIdMap.toOriginalNodeId(nodeId), (s, t) -> filterAndConsume(s, t, consumer));
    }

    @Override
    public void forEachRelationship(
        long nodeId, double fallbackValue, RelationshipWithPropertyConsumer consumer
    ) {
        super.forEachRelationship(filteredIdMap.toOriginalNodeId(nodeId), fallbackValue, (s, t, p) -> filterAndConsume(s, t, p, consumer));
    }

    @Override
    public long getTarget(long sourceNodeId, long index) {
        HugeGraph.GetTargetConsumer consumer = new HugeGraph.GetTargetConsumer(index);
        forEachRelationship(sourceNodeId, consumer);
        return consumer.target;
    }

    public long getMappedNodeId(long nodeId) {
        return filteredIdMap.toMappedNodeId(nodeId);
    }

    @Override
    public boolean exists(long sourceNodeId, long targetNodeId) {
        return super.exists(filteredIdMap.toOriginalNodeId(sourceNodeId), filteredIdMap.toOriginalNodeId(targetNodeId));
    }

    @Override
    public double relationshipProperty(long sourceNodeId, long targetNodeId, double fallbackValue) {
        return super.relationshipProperty(filteredIdMap.toOriginalNodeId(sourceNodeId), filteredIdMap.toOriginalNodeId(targetNodeId), fallbackValue);
    }

    @Override
    public double relationshipProperty(long sourceNodeId, long targetNodeId) {
        return super.relationshipProperty(filteredIdMap.toOriginalNodeId(sourceNodeId), filteredIdMap.toOriginalNodeId(targetNodeId));
    }

    @Override
    public IdMapGraph concurrentCopy() {
        return new NodeFilteredGraph((HugeGraph) graph.concurrentCopy(), filteredIdMap, degreeCache);
    }

    @Override
    public NodeProperties nodeProperties(String type) {
        NodeProperties properties = graph.nodeProperties(type);
        if (properties == null) {
            return null;
        }
        return new FilteredNodeProperties(properties, filteredIdMap);
    }

    private boolean filterAndConsume(long source, long target, RelationshipConsumer consumer) {
        if (filteredIdMap.contains(source) && filteredIdMap.contains(target)) {
            long internalSourceId = filteredIdMap.toMappedNodeId(source);
            long internalTargetId = filteredIdMap.toMappedNodeId(target);
            return consumer.accept(internalSourceId, internalTargetId);
        }
        return true;
    }

    private boolean filterAndConsume(long source, long target, double propertyValue, RelationshipWithPropertyConsumer consumer) {
        if (filteredIdMap.contains(source) && filteredIdMap.contains(target)) {
            long internalSourceId = filteredIdMap.toMappedNodeId(source);
            long internalTargetId = filteredIdMap.toMappedNodeId(target);
            return consumer.accept(internalSourceId, internalTargetId, propertyValue);
        }
        return true;
    }
}
