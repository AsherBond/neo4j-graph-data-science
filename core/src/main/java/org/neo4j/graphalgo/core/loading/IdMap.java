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
package org.neo4j.graphalgo.core.loading;

import com.carrotsearch.hppc.BitSet;
import org.neo4j.collection.primitive.PrimitiveLongIterable;
import org.neo4j.collection.primitive.PrimitiveLongIterator;
import org.neo4j.graphalgo.NodeLabel;
import org.neo4j.graphalgo.api.BatchNodeIterable;
import org.neo4j.graphalgo.api.IdMapping;
import org.neo4j.graphalgo.api.NodeIterator;
import org.neo4j.graphalgo.core.utils.LazyBatchCollection;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimation;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimations;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.HugeLongArray;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongPredicate;
import java.util.stream.Stream;

/**
 * This is basically a long to int mapper. It sorts the id's in ascending order so its
 * guaranteed that there is no ID greater then nextGraphId / capacity
 */
public class IdMap implements IdMapping, NodeIterator, BatchNodeIterable {

    private static final MemoryEstimation ESTIMATION = MemoryEstimations
            .builder(IdMap.class)
            .perNode("Neo4j identifiers", HugeLongArray::memoryEstimation)
            .rangePerGraphDimension(
                    "Mapping from Neo4j identifiers to internal identifiers",
                    (dimensions, concurrency) -> SparseNodeMapping.memoryEstimation(dimensions.highestNeoId(), dimensions.nodeCount()))
        // TODO memory estimation for labelInformation
            .build();

    protected long nodeCount;
    protected HugeLongArray graphIds;
    protected SparseNodeMapping nodeToGraphIds;
    protected final Optional<Map<NodeLabel, BitSet>> maybeLabelInformation;

    public static MemoryEstimation memoryEstimation() {
        return ESTIMATION;
    }

    public IdMap(HugeLongArray graphIds, SparseNodeMapping nodeToGraphIds, long nodeCount) {
        this(graphIds, nodeToGraphIds, Optional.empty(), nodeCount);
    }

    /**
     * initialize the map with pre-built sub arrays
     */
    public IdMap(HugeLongArray graphIds, SparseNodeMapping nodeToGraphIds, Optional<Map<NodeLabel, BitSet>> maybeLabelInformation, long nodeCount) {
        this.graphIds = graphIds;
        this.nodeToGraphIds = nodeToGraphIds;
        this.maybeLabelInformation = maybeLabelInformation;
        this.nodeCount = nodeCount;
    }

    @Override
    public long toMappedNodeId(long nodeId) {
        return nodeToGraphIds.get(nodeId);
    }

    @Override
    public long toOriginalNodeId(long nodeId) {
        return graphIds.get(nodeId);
    }

    @Override
    public boolean contains(final long nodeId) {
        return nodeToGraphIds.contains(nodeId);
    }

    @Override
    public long nodeCount() {
        return nodeCount;
    }

    @Override
    public void forEachNode(LongPredicate consumer) {
        final long count = nodeCount();
        for (long i = 0L; i < count; i++) {
            if (!consumer.test(i)) {
                return;
            }
        }
    }

    @Override
    public PrimitiveLongIterator nodeIterator() {
        return new IdIterator(nodeCount());
    }

    @Override
    public Collection<PrimitiveLongIterable> batchIterables(int batchSize) {
        return LazyBatchCollection.of(
                nodeCount(),
                batchSize,
                IdIterable::new);
    }

    public IdMap withFilteredLabels(BitSet unionedBitSet, int concurrency) {
        if (!maybeLabelInformation.isPresent()) {
            return this;
        }

        long nodeId = -1L;
        long cursor = 0L;
        long newNodeCount = unionedBitSet.cardinality();
        HugeLongArray newGraphIds = HugeLongArray.newArray(newNodeCount, AllocationTracker.EMPTY);
        while((nodeId = unionedBitSet.nextSetBit(nodeId+1)) != -1) {
            newGraphIds.set(cursor, nodeId);
            cursor++;
        }

        SparseNodeMapping newNodeToGraphIds = IdMapBuilder.buildSparseNodeMapping(
            newGraphIds,
            newGraphIds.size(),
            nodeToGraphIds.getCapacity(),
            concurrency,
            AllocationTracker.EMPTY
        );
        return new IdMap(newGraphIds, newNodeToGraphIds, newNodeCount);
    }

    public Stream<NodeLabel> labels(long nodeId) {
        return maybeLabelInformation
            .map(elementIdentifierBitSetMap ->
                elementIdentifierBitSetMap
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getValue().get(nodeId))
                    .map(Map.Entry::getKey))
            .orElseGet(() -> Stream.of(NodeLabel.ALL_NODES));
    }

    public Optional<Map<NodeLabel, BitSet>> maybeLabelInformation() {
        return maybeLabelInformation;
    }

    public static final class IdIterable implements PrimitiveLongIterable {
        private final long start;
        private final long length;

        public IdIterable(long start, long length) {
            this.start = start;
            this.length = length;
        }

        @Override
        public PrimitiveLongIterator iterator() {
            return new IdIterator(start, length);
        }
    }

    public static final class IdIterator implements PrimitiveLongIterator {

        private long current;
        private long limit; // exclusive upper bound

        public IdIterator(long length) {
            this.current = 0;
            this.limit = length;
        }

        private IdIterator(long start, long length) {
            this.current = start;
            this.limit = start + length;
        }

        @Override
        public boolean hasNext() {
            return current < limit;
        }

        @Override
        public long next() {
            return current++;
        }
    }
}
