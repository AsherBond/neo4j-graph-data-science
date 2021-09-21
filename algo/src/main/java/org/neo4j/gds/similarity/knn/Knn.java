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
package org.neo4j.gds.similarity.knn;

import com.carrotsearch.hppc.LongArrayList;
import org.jetbrains.annotations.Nullable;
import org.neo4j.gds.Algorithm;
import org.neo4j.gds.annotation.ValueClass;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.core.concurrency.ParallelUtil;
import org.neo4j.gds.core.utils.BiLongConsumer;
import org.neo4j.gds.core.utils.ProgressTimer;
import org.neo4j.gds.core.utils.paged.HugeCursor;
import org.neo4j.gds.core.utils.paged.HugeObjectArray;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.similarity.SimilarityResult;

import java.util.SplittableRandom;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

public class Knn extends Algorithm<Knn, Knn.Result> {

    private final long nodeCount;
    private final KnnBaseConfig config;
    private final KnnContext context;
    private final SplittableRandom random;
    private final SimilarityComputer computer;

    public Knn(Graph graph, KnnBaseConfig config, KnnContext context) {
        this(
            graph.nodeCount(),
            config,
            SimilarityComputer.ofProperty(graph, config.nodeWeightProperty()),
            context
        );
    }

    public Knn(
        long nodeCount,
        KnnBaseConfig config,
        SimilarityComputer similarityComputer,
        KnnContext context
    ) {
        this.nodeCount = nodeCount;
        this.config = config;
        this.context = context;
        this.computer = similarityComputer;

        this.random = this.config.randomSeed().isPresent()
            ? new SplittableRandom(this.config.randomSeed().get())
            : new SplittableRandom();

        this.progressTracker = context.progressTracker();
    }

    public long nodeCount() {
        return this.nodeCount;
    }

    public KnnContext context() {
        return context;
    }

    @Override
    public Result compute() {
        progressTracker.beginSubTask();
        HugeObjectArray<NeighborList> neighbors;
        try (var ignored1 = ProgressTimer.start(this::logOverallTime)) {
            try (var ignored2 = ProgressTimer.start(this::logInitTime)) {
                progressTracker.beginSubTask();
                neighbors = this.initializeRandomNeighbors();
                progressTracker.endSubTask();
            }
            if (neighbors == null) {
                return new EmptyResult();
            }

            var maxIterations = this.config.maxIterations();
            var maxUpdates = (long) Math.ceil(config.sampleRate() * config.topK() * nodeCount);
            var updateThreshold = (long) Math.floor(this.config.deltaThreshold() * maxUpdates);

            long updateCount;
            int iteration = 0;
            boolean didConverge = false;

            progressTracker.beginSubTask();
            for (; iteration < maxIterations; iteration++) {
                int currentIteration = iteration;
                try (var ignored3 = ProgressTimer.start(took -> logIterationTime(currentIteration, took))) {
                    updateCount = this.iteration(neighbors);
                }
                if (updateCount <= updateThreshold) {
                    iteration++;
                    didConverge = true;
                    break;
                }
            }
            progressTracker.endSubTask();

            progressTracker.endSubTask();
            return ImmutableResult.of(neighbors, iteration, didConverge);
        }
    }

    private @Nullable HugeObjectArray<NeighborList> initializeRandomNeighbors() {
        var nodeCount = this.nodeCount;
        var k = this.config.topK();
        // (int) is safe since it is at most k, which is an int
        var boundedK = (int) Math.min(nodeCount - 1, k);

        assert boundedK <= k && boundedK <= nodeCount - 1;

        if (nodeCount < 2 || k == 0) {
            return null;
        }

        var neighbors = HugeObjectArray.newArray(NeighborList.class, nodeCount, this.context.allocationTracker());

        ParallelUtil.readParallel(
            this.config.concurrency(),
            nodeCount,
            this.context.executor(),
            new GenerateRandomNeighbors(
                random,
                this.computer,
                neighbors,
                nodeCount,
                k,
                boundedK,
                progressTracker
            )
        );

        return neighbors;
    }

    private long iteration(HugeObjectArray<NeighborList> neighbors) {
        // this is a sanity check
        // we check for this before any iteration and return
        // and just make sure that this invariant holds on every iteration
        var nodeCount = this.nodeCount;
        if (nodeCount < 2 || this.config.topK() == 0) {
            return NeighborList.NOT_INSERTED;
        }

        var allocationTracker = this.context.allocationTracker();
        var concurrency = this.config.concurrency();
        var executor = this.context.executor();

        var sampledK = this.config.sampledK(nodeCount);

        // TODO: init in ctor and reuse - benchmark against new allocations
        var allOldNeighbors = HugeObjectArray.newArray(LongArrayList.class, nodeCount, allocationTracker);
        var allNewNeighbors = HugeObjectArray.newArray(LongArrayList.class, nodeCount, allocationTracker);

        progressTracker.beginSubTask();
        ParallelUtil.readParallel(concurrency, nodeCount, executor, new SplitOldAndNewNeighbors(
            this.random,
            neighbors,
            allOldNeighbors,
            allNewNeighbors,
            sampledK,
            progressTracker
        ));
        progressTracker.endSubTask();

        // TODO: init in ctor and reuse - benchmark against new allocations
        var reverseOldNeighbors = HugeObjectArray.newArray(LongArrayList.class, nodeCount, allocationTracker);
        var reverseNewNeighbors = HugeObjectArray.newArray(LongArrayList.class, nodeCount, allocationTracker);

        progressTracker.beginSubTask();
        reverseOldAndNewNeighbors(
            nodeCount,
            allOldNeighbors,
            allNewNeighbors,
            reverseOldNeighbors,
            reverseNewNeighbors,
            progressTracker
        );
        progressTracker.endSubTask();

        var neighborsJoiner = new JoinNeighbors(
            this.random,
            this.computer,
            neighbors,
            allOldNeighbors,
            allNewNeighbors,
            reverseOldNeighbors,
            reverseNewNeighbors,
            nodeCount,
            this.config.topK(),
            sampledK,
            this.config.randomJoins(),
            progressTracker
        );

        progressTracker.beginSubTask();
        ParallelUtil.readParallel(concurrency, nodeCount, executor, neighborsJoiner);
        progressTracker.endSubTask();

        return neighborsJoiner.updateCount.sum();
    }

    private static void reverseOldAndNewNeighbors(
        long nodeCount,
        HugeObjectArray<LongArrayList> allOldNeighbors,
        HugeObjectArray<LongArrayList> allNewNeighbors,
        HugeObjectArray<LongArrayList> reverseOldNeighbors,
        HugeObjectArray<LongArrayList> reverseNewNeighbors,
        ProgressTracker progressTracker
    ) {
        // TODO: cursors
        for (long nodeId = 0; nodeId < nodeCount; nodeId++) {
            reverseNeighbors(nodeId, allOldNeighbors, reverseOldNeighbors);
            reverseNeighbors(nodeId, allNewNeighbors, reverseNewNeighbors);
            progressTracker.logProgress();
        }
    }

    static void reverseNeighbors(
        long nodeId,
        HugeObjectArray<LongArrayList> allNeighbors,
        HugeObjectArray<LongArrayList> reverseNeighbors
    ) {
        var neighbors = allNeighbors.get(nodeId);
        if (neighbors != null) {
            for (var neighbor : neighbors) {
                assert neighbor.value != nodeId;
                var oldReverse = reverseNeighbors.get(neighbor.value);
                if (oldReverse == null) {
                    oldReverse = new LongArrayList();
                    reverseNeighbors.set(neighbor.value, oldReverse);
                }
                oldReverse.add(nodeId);
            }
        }
    }

    private static final class JoinNeighbors implements BiLongConsumer {
        private final SplittableRandom random;
        private final SimilarityComputer computer;
        private final HugeObjectArray<NeighborList> neighbors;
        private final HugeObjectArray<LongArrayList> allOldNeighbors;
        private final HugeObjectArray<LongArrayList> allNewNeighbors;
        private final HugeObjectArray<LongArrayList> allReverseOldNeighbors;
        private final HugeObjectArray<LongArrayList> allReverseNewNeighbors;
        private final long n;
        private final int k;
        private final int sampledK;
        private final int randomJoins;
        private final ProgressTracker progressTracker;
        private final LongAdder updateCount;

        private JoinNeighbors(
            SplittableRandom random,
            SimilarityComputer computer,
            HugeObjectArray<NeighborList> neighbors,
            HugeObjectArray<LongArrayList> allOldNeighbors,
            HugeObjectArray<LongArrayList> allNewNeighbors,
            HugeObjectArray<LongArrayList> allReverseOldNeighbors,
            HugeObjectArray<LongArrayList> allReverseNewNeighbors,
            long n,
            int k,
            int sampledK,
            int randomJoins,
            ProgressTracker progressTracker
        ) {
            this.random = random;
            this.computer = computer;
            this.neighbors = neighbors;
            this.allOldNeighbors = allOldNeighbors;
            this.allNewNeighbors = allNewNeighbors;
            this.allReverseOldNeighbors = allReverseOldNeighbors;
            this.allReverseNewNeighbors = allReverseNewNeighbors;
            this.n = n;
            this.k = k;
            this.sampledK = sampledK;
            this.randomJoins = randomJoins;
            this.progressTracker = progressTracker;
            this.updateCount = new LongAdder();
        }

        @Override
        public void apply(long start, long end) {
            var rng = random.split();
            var computer = this.computer;
            var n = this.n;
            var k = this.k;
            var sampledK = this.sampledK;
            var allNeighbors = this.neighbors;
            var allNewNeighbors = this.allNewNeighbors;
            var allOldNeighbors = this.allOldNeighbors;
            var allReverseNewNeighbors = this.allReverseNewNeighbors;
            var allReverseOldNeighbors = this.allReverseOldNeighbors;

            long updateCount = 0;
            for (long nodeId = start; nodeId < end; nodeId++) {
                // old[v] ∪ Sample(old′[v], ρK)
                var oldNeighbors = allOldNeighbors.get(nodeId);
                if (oldNeighbors != null) {
                    joinOldNeighbors(rng, sampledK, allReverseOldNeighbors, nodeId, oldNeighbors);
                }


                // new[v] ∪ Sample(new′[v], ρK)
                var newNeighbors = allNewNeighbors.get(nodeId);
                if (newNeighbors != null) {
                    updateCount += joinNewNeighbors(
                        rng,
                        computer,
                        n,
                        k,
                        sampledK,
                        allNeighbors,
                        allReverseNewNeighbors,
                        nodeId,
                        oldNeighbors,
                        newNeighbors
                    );
                }

                // this isn't in the paper
                randomJoins(rng, computer, n, k, allNeighbors, nodeId, this.randomJoins);
                progressTracker.logProgress();
            }

            this.updateCount.add(updateCount);
        }

        private void joinOldNeighbors(
            SplittableRandom rng,
            int sampledK,
            HugeObjectArray<LongArrayList> allReverseOldNeighbors,
            long nodeId,
            LongArrayList oldNeighbors
        ) {
            var reverseOldNeighbors = allReverseOldNeighbors.get(nodeId);
            if (reverseOldNeighbors != null) {
                var numberOfReverseOldNeighbors = reverseOldNeighbors.size();
                for (var elem : reverseOldNeighbors) {
                    if (rng.nextInt(numberOfReverseOldNeighbors) < sampledK) {
                        // TODO: this could add nodes twice, maybe? should this be a set?
                        oldNeighbors.add(elem.value);
                    }
                }
            }
        }

        private long joinNewNeighbors(
            SplittableRandom rng,
            SimilarityComputer computer,
            long n,
            int k,
            int sampledK,
            HugeObjectArray<NeighborList> allNeighbors,
            HugeObjectArray<LongArrayList> allReverseNewNeighbors,
            long nodeId,
            LongArrayList oldNeighbors,
            LongArrayList newNeighbors
        ) {
            long updateCount = 0;

            joinOldNeighbors(rng, sampledK, allReverseNewNeighbors, nodeId, newNeighbors);

            var newNeighborElements = newNeighbors.buffer;
            var newNeighborsCount = newNeighbors.elementsCount;

            for (int i = 0; i < newNeighborsCount; i++) {
                var elem1 = newNeighborElements[i];
                assert elem1 != nodeId;

                // join(u1, v), this isn't in the paper
                updateCount += join(rng, computer, allNeighbors, n, k, elem1, nodeId);

                // join(new_nbd, new_ndb)
                for (int j = i + 1; j < newNeighborsCount; j++) {
                    var elem2 = newNeighborElements[i];
                    if (elem1 == elem2) {
                        continue;
                    }

                    updateCount += join(rng, computer, allNeighbors, n, k, elem1, elem2);
                    updateCount += join(rng, computer, allNeighbors, n, k, elem2, elem1);
                }

                // join(new_nbd, old_ndb)
                if (oldNeighbors != null) {
                    for (var oldElemCursor : oldNeighbors) {
                        var elem2 = oldElemCursor.value;

                        if (elem1 == elem2) {
                            continue;
                        }

                        updateCount += join(rng, computer, allNeighbors, n, k, elem1, elem2);
                        updateCount += join(rng, computer, allNeighbors, n, k, elem2, elem1);
                    }
                }
            }
            return updateCount;
        }

        private void randomJoins(
            SplittableRandom rng,
            SimilarityComputer computer,
            long n,
            int k,
            HugeObjectArray<NeighborList> allNeighbors,
            long nodeId,
            int randomJoins
        ) {
            for (int i = 0; i < randomJoins; i++) {
                var randomNodeId = rng.nextLong(n - 1);
                if (randomNodeId >= nodeId) {
                    ++randomNodeId;
                }
                // random joins are not counted towards the actual update counter
                join(rng, computer, allNeighbors, n, k, nodeId, randomNodeId);
            }
        }

        private long join(
            SplittableRandom splittableRandom,
            SimilarityComputer computer,
            HugeObjectArray<NeighborList> allNeighbors,
            long n,
            int k,
            long base,
            long joiner
        ) {
            assert base != joiner;
            assert n > 1 && k > 0;

            var similarity = computer.safeSimilarity(base, joiner);
            var neighbors = allNeighbors.get(base);
            synchronized (neighbors) {
                var k2 = neighbors.size();

                assert k2 > 0;
                assert k2 <= k;
                assert k2 <= n - 1;

                return neighbors.add(joiner, similarity, splittableRandom);
            }
        }
    }

    private void logInitTime(long ms) {
        progressTracker.progressLogger().logMessage(() -> formatWithLocale("KNN-G Graph init took %d ms", ms));
    }

    private void logIterationTime(int iteration, long ms) {
        progressTracker.progressLogger().logMessage(() -> formatWithLocale("KNN-G Graph iteration %d took %d ms", iteration, ms));
    }

    private void logOverallTime(long ms) {
        progressTracker.progressLogger().logMessage(() -> formatWithLocale("KNN-G Graph execution took %d ms", ms));
    }

    @Override
    public Knn me() {
        return this;
    }

    @Override
    public void release() {

    }

    @ValueClass
    public abstract static class Result {
        abstract HugeObjectArray<NeighborList> neighborList();

        abstract int ranIterations();

        abstract boolean didConverge();

        public LongStream neighborsOf(long nodeId) {
            return neighborList().get(nodeId).elements().map(NeighborList::clearCheckedFlag);
        }

        // http://www.flatmapthatshit.com/
        public Stream<SimilarityResult> streamSimilarityResult() {
            var neighborList = neighborList();
            return Stream.iterate(neighborList.initCursor(neighborList.newCursor()), HugeCursor::next, UnaryOperator.identity())
                .flatMap(cursor -> IntStream.range(cursor.offset, cursor.limit)
                    .mapToObj(index -> cursor.array[index].similarityStream(index + cursor.base))
                    .flatMap(Function.identity())
                );
        }

        public long totalSimilarityPairs() {
            var neighborList = neighborList();
            return Stream.iterate(neighborList.initCursor(neighborList.newCursor()), HugeCursor::next, UnaryOperator.identity())
                .flatMapToLong(cursor -> IntStream.range(cursor.offset, cursor.limit)
                    .mapToLong(index -> cursor.array[index].size()))
                .sum();
        }

        public long size() {
            return neighborList().size();
        }
    }

    private static final class EmptyResult extends Result {

        @Override
        HugeObjectArray<NeighborList> neighborList() {
            return HugeObjectArray.of();
        }

        @Override
        int ranIterations() {
            return 0;
        }

        @Override
        boolean didConverge() {
            return false;
        }

        @Override
        public LongStream neighborsOf(long nodeId) {
            return LongStream.empty();
        }

        @Override
        public long size() {
            return 0;
        }
    }
}
