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
package org.neo4j.gds.embeddings.fastrp;

import org.jetbrains.annotations.TestOnly;
import org.neo4j.gds.Algorithm;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.collections.ha.HugeObjectArray;
import org.neo4j.gds.core.concurrency.Concurrency;
import org.neo4j.gds.core.concurrency.DefaultPool;
import org.neo4j.gds.core.concurrency.ParallelUtil;
import org.neo4j.gds.core.concurrency.RunWithConcurrency;
import org.neo4j.gds.core.utils.partition.DegreePartition;
import org.neo4j.gds.core.utils.partition.Partition;
import org.neo4j.gds.core.utils.partition.PartitionConsumer;
import org.neo4j.gds.core.utils.partition.PartitionUtils;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.ml.core.features.FeatureConsumer;
import org.neo4j.gds.ml.core.features.FeatureExtraction;
import org.neo4j.gds.ml.core.features.FeatureExtractor;
import org.neo4j.gds.termination.TerminationFlag;
import org.neo4j.gds.utils.CloseableThreadLocal;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.neo4j.gds.ml.core.tensor.operations.FloatVectorOperations.addInPlace;
import static org.neo4j.gds.ml.core.tensor.operations.FloatVectorOperations.addWeightedInPlace;
import static org.neo4j.gds.ml.core.tensor.operations.FloatVectorOperations.l2Norm;
import static org.neo4j.gds.ml.core.tensor.operations.FloatVectorOperations.scale;
import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

public class FastRP extends Algorithm<FastRPResult> {

    private static final int SPARSITY = 3;
    private static final double ENTRY_PROBABILITY = 1.0 / (2 * SPARSITY);
    private static final float EPSILON = 10f / Float.MAX_VALUE;

    private final Graph graph;
    private final Concurrency concurrency;
    private final float normalizationStrength;
    private final List<FeatureExtractor> featureExtractors;
    private final Optional<String> relationshipWeightProperty;
    private final double relationshipWeightFallback;
    private final int inputDimension;
    private final float[][] propertyVectors;
    private final HugeObjectArray<float[]> embeddings;
    private final HugeObjectArray<float[]> embeddingA;
    private final HugeObjectArray<float[]> embeddingB;
    private final EmbeddingCombiner embeddingCombiner;
    private final long randomSeed;

    private final int embeddingDimension;
    private final int baseEmbeddingDimension;
    private final Number nodeSelfInfluence;
    private final List<Number> iterationWeights;
    private final int minBatchSize;
    private List<DegreePartition> partitions;


    public FastRP(
        Graph graph,
        FastRPParameters parameters,
        int minBatchSize,
        List<FeatureExtractor> featureExtractors,
        ProgressTracker progressTracker,
        TerminationFlag terminationFlag
    ) {
        super(progressTracker);
        this.graph = graph;
        this.featureExtractors = featureExtractors;
        this.relationshipWeightProperty = parameters.relationshipWeightProperty();
        this.relationshipWeightFallback = this.relationshipWeightProperty.map(s -> Double.NaN).orElse(1.0);
        this.inputDimension = FeatureExtraction.featureCount(featureExtractors);
        this.randomSeed = improveSeed(parameters.randomSeed().orElseGet(System::nanoTime));
        this.concurrency = parameters.concurrency();
        this.minBatchSize = minBatchSize;

        this.propertyVectors = new float[inputDimension][parameters.propertyDimension()];
        this.embeddings = HugeObjectArray.newArray(float[].class, graph.nodeCount());
        this.embeddingA = HugeObjectArray.newArray(float[].class, graph.nodeCount());
        this.embeddingB = HugeObjectArray.newArray(float[].class, graph.nodeCount());

        this.embeddingDimension = parameters.embeddingDimension();
        this.baseEmbeddingDimension = parameters.embeddingDimension() - parameters.propertyDimension();
        this.iterationWeights = parameters.iterationWeights();
        this.nodeSelfInfluence = parameters.nodeSelfInfluence();
        this.normalizationStrength = parameters.normalizationStrength();
        this.embeddingCombiner = graph.hasRelationshipProperty()
            ? this::addArrayValuesWeighted
            : (lhs, rhs, ignoreWeight) -> addInPlace(lhs, rhs);
        this.embeddings.setAll((i) -> new float[embeddingDimension]);

        this.terminationFlag = terminationFlag;
    }

    @Override
    public FastRPResult compute() {
        progressTracker.beginSubTask();
        initDegreePartition();
        initPropertyVectors();
        initRandomVectors();
        addInitialVectorsToEmbedding();
        propagateEmbeddings();
        progressTracker.endSubTask();
        return new FastRPResult(embeddings);
    }

    public void initDegreePartition() {
        this.partitions = PartitionUtils.degreePartitionStream(
            graph.nodeCount(),
            graph.relationshipCount(),
            concurrency,
            graph::degree
        ).collect(Collectors.toList());
    }

    void initPropertyVectors() {
        int propertyDimension = embeddingDimension - baseEmbeddingDimension;
        float entryValue = (float) Math.sqrt(SPARSITY) / (float) Math.sqrt(embeddingDimension);
        var random = new HighQualityRandom(randomSeed);
        for (int i = 0; i < inputDimension; i++) {
            this.propertyVectors[i] = new float[propertyDimension];
        }
        for (int d = 0; d < propertyDimension; d++) {
            for (int i = 0; i < inputDimension; i++) {
                this.propertyVectors[i][d] = computeRandomEntry(random, entryValue);
            }
        }
    }

    void initRandomVectors() {
        progressTracker.beginSubTask();

        var sqrtEmbeddingDimension = (float) Math.sqrt(embeddingDimension);
        List<Runnable> tasks = PartitionUtils.rangePartition(
            concurrency,
            graph.nodeCount(),
            partition -> new InitRandomVectorTask(
                partition,
                sqrtEmbeddingDimension
            ),
            Optional.of(minBatchSize)
        );
        RunWithConcurrency.builder()
            .concurrency(concurrency)
            .tasks(tasks)
            .run();

        progressTracker.endSubTask();
    }

    void addInitialVectorsToEmbedding() {
        if (Float.compare(nodeSelfInfluence.floatValue(), 0.0f) == 0) return;
        progressTracker.beginSubTask();

        ParallelUtil.parallelForEachNode(
            graph.nodeCount(),
            concurrency,
            terminationFlag,
            this::addInitialStateToEmbedding
        );

        progressTracker.endSubTask();
    }

    void propagateEmbeddings() {
        progressTracker.beginSubTask();

        for (int i = 0; i < iterationWeights.size(); i++) {
            progressTracker.beginSubTask();

            HugeObjectArray<float[]> currentEmbeddings = i % 2 == 0 ? embeddingA : embeddingB;
            HugeObjectArray<float[]> previousEmbeddings = i % 2 == 0 ? embeddingB : embeddingA;
            var iterationWeight = iterationWeights.get(i).floatValue();
            boolean firstIteration = i == 0;

            Supplier<PartitionConsumer<DegreePartition>> taskSupplier = () -> new PropagateEmbeddingsTask(
                currentEmbeddings,
                previousEmbeddings,
                iterationWeight,
                firstIteration
            );

            parallelPartitionsConsume(
                RunWithConcurrency.builder().executor(DefaultPool.INSTANCE).concurrency(concurrency),
                partitions.stream(),
                taskSupplier
            );

            progressTracker.endSubTask();
        }
        progressTracker.endSubTask();
    }

    /**
     * This method is useful, when |partitions| is greatly larger than concurrency as we only create a single consumer per thread.
     * Compared to parallelForEachNode, thread local state does not need to be resolved for each node but only per partition.
     */
    public static <P extends Partition> void parallelPartitionsConsume(
        RunWithConcurrency.Builder runnerBuilder,
        Stream<P> partitions,
        Supplier<PartitionConsumer<P>> taskSupplier
    ) {
        try (var localConsumer = CloseableThreadLocal.withInitial(taskSupplier)) {
            var taskStream = partitions.map(partition -> (Runnable) () -> localConsumer.get().consume(partition));
            runnerBuilder.tasks(taskStream);
            runnerBuilder.run();
        }
    }


    @TestOnly
    HugeObjectArray<float[]> currentEmbedding(int iteration) {
        return iteration % 2 == 0
            ? this.embeddingA
            : this.embeddingB;
    }

    @TestOnly
    float[][] propertyVectors() {
        return propertyVectors;
    }

    @TestOnly
    HugeObjectArray<float[]> embeddings() {
        return embeddings;
    }

    private void addArrayValuesWeighted(float[] lhs, float[] rhs, double weight) {
        for (int i = 0; i < lhs.length; i++) {
            lhs[i] = (float) Math.fma(rhs[i], weight, lhs[i]);
        }
    }

    private static float computeRandomEntry(Random random, float entryValue) {
        double randomValue = random.nextDouble();

        if (randomValue < ENTRY_PROBABILITY) {
            return entryValue;
        } else if (randomValue < ENTRY_PROBABILITY * 2.0) {
            return -entryValue;
        } else {
            return 0.0f;
        }
    }

    private static class HighQualityRandom extends Random {
        private long u;
        private long v;
        private long w;

        public HighQualityRandom(long seed) {
            reseed(seed);
        }

        public void reseed(long seed) {
            v = 4101842887655102017L;
            w = 1;
            u = seed ^ v;
            nextLong();
            v = u;
            nextLong();
            w = v;
            nextLong();
        }

        @Override
        public long nextLong() {
            u = u * 2862933555777941757L + 7046029254386353087L;
            v ^= v >>> 17;
            v ^= v << 31;
            v ^= v >>> 8;
            w = 4294957665L * w + (w >>> 32);
            long x = u ^ (u << 21);
            x ^= x >>> 35;
            x ^= x << 4;
            return (x + v) ^ w;
        }

        @Override
        protected int next(int bits) {
            return (int) (nextLong() >>> (64-bits));
        }
    }

    private long improveSeed(long randomSeed) {
        return new HighQualityRandom(randomSeed).nextLong();
    }

    private interface EmbeddingCombiner {
        void combine(float[] into, float[] add, double weight);
    }

    private final class InitRandomVectorTask implements Runnable {

        final float sqrtSparsity = (float) Math.sqrt(SPARSITY);

        private final Partition partition;
        private final float sqrtEmbeddingDimension;
        private final PropertyVectorAdder propertyVectorAdder;

        private InitRandomVectorTask(
            Partition partition,
            float sqrtEmbeddingDimension
        ) {
            this.partition = partition;
            this.sqrtEmbeddingDimension = sqrtEmbeddingDimension;
            this.propertyVectorAdder = new PropertyVectorAdder();
        }

        @Override
        public void run() {
            // this value currently doesnt matter because of reseeding below
            var random = new HighQualityRandom(randomSeed);
            partition.consume( nodeId -> {
                int degree = graph.degree(nodeId);
                float scaling = degree == 0
                    ? 1.0f
                    : (float) Math.pow(degree, normalizationStrength);

                float entryValue = scaling * sqrtSparsity / sqrtEmbeddingDimension;
                random.reseed(randomSeed ^ graph.toOriginalNodeId(nodeId));
                var randomVector = computeRandomVector(nodeId, random, entryValue, scaling);
                embeddingB.set(nodeId, randomVector);
                embeddingA.set(nodeId, new float[embeddingDimension]);
            });
            progressTracker.logProgress(partition.nodeCount());
        }

        private float[] computeRandomVector(long nodeId, Random random, float entryValue, float scaling) {
            var randomVector = new float[embeddingDimension];
            for (int i = 0; i < baseEmbeddingDimension; i++) {
                randomVector[i] = computeRandomEntry(random, entryValue);
            }

            propertyVectorAdder.setRandomVector(randomVector);
            propertyVectorAdder.setScaling(scaling);
            FeatureExtraction.extract(nodeId, -1, featureExtractors, propertyVectorAdder);

            return randomVector;
        }

        private class PropertyVectorAdder implements FeatureConsumer {
            private float[] randomVector;
            private float scaling = 1.0f;

            void setRandomVector(float[] randomVector) {
                this.randomVector = randomVector;
            }
            void setScaling(float scaling) {
                this.scaling = scaling;
            }

            @Override
            public void acceptScalar(long ignored, int offset, double value) {
                float floatValue = (float) value;
                for (int i = baseEmbeddingDimension; i < embeddingDimension; i++) {
                    randomVector[i] += scaling * floatValue * propertyVectors[offset][i - baseEmbeddingDimension];
                }
            }

            @Override
            public void acceptArray(long ignored, int offset, double[] values) {
                for (int j = 0; j < values.length; j++) {
                    var value = (float) values[j];
                    float[] propertyVector = propertyVectors[offset + j];
                    for (int i = baseEmbeddingDimension; i < embeddingDimension; i++) {
                        randomVector[i] += scaling * value * propertyVector[i - baseEmbeddingDimension];
                    }
                }
            }
        }
    }

    private void addInitialStateToEmbedding(long nodeId) {
        var initialVector = embeddingB.get(nodeId);
        var l2Norm = l2Norm(initialVector);
        float adjustedL2Norm = l2Norm < EPSILON ? 1f : l2Norm;
        addWeightedInPlace(embeddings.get(nodeId), initialVector, nodeSelfInfluence.floatValue() / adjustedL2Norm);

        progressTracker.logProgress(1);
    }

    private final class PropagateEmbeddingsTask implements PartitionConsumer<DegreePartition> {

        private final HugeObjectArray<float[]> currentEmbeddings;
        private final HugeObjectArray<float[]> previousEmbeddings;
        private final float iterationWeight;
        private final Graph localGraph;
        private final boolean firstIteration;

        private PropagateEmbeddingsTask(
            HugeObjectArray<float[]> currentEmbeddings,
            HugeObjectArray<float[]> previousEmbeddings,
            float iterationWeight,
            boolean firstIteration
        ) {
            this.currentEmbeddings = currentEmbeddings;
            this.previousEmbeddings = previousEmbeddings;
            this.iterationWeight = iterationWeight;
            this.localGraph = graph.concurrentCopy();
            this.firstIteration = firstIteration;
        }

        public void consume(DegreePartition partition) {
            partition.consume(nodeId -> {
                var embedding = embeddings.get(nodeId);
                var currentEmbedding = currentEmbeddings.get(nodeId);
                Arrays.fill(currentEmbedding, 0.0f);

                // Collect and combine the neighbour embeddings
                localGraph.forEachRelationship(nodeId, relationshipWeightFallback, (source, target, weight) -> {
                    if (firstIteration && Double.isNaN(weight)) {
                        throw new IllegalArgumentException(formatWithLocale(
                            "Missing relationship property `%s` on relationship between nodes with ids `%d` and `%d`.",
                            relationshipWeightProperty.orElse(""),
                            graph.toOriginalNodeId(source), graph.toOriginalNodeId(target)
                        ));
                    }
                    embeddingCombiner.combine(currentEmbedding, previousEmbeddings.get(target), weight);
                    return true;
                });

                // Normalize neighbour embeddings
                var degree = graph.degree(nodeId);
                int adjustedDegree = degree == 0 ? 1 : degree;
                float degreeScale = 1.0f / adjustedDegree;
                scale(currentEmbedding, degreeScale);
                var invL2Norm = 1.0f / l2Norm(currentEmbedding);
                var safeInvL2Norm = Float.isFinite(invL2Norm) ? invL2Norm : 1.0f;

                // Update the result embedding
                addWeightedInPlace(embedding, currentEmbedding, safeInvL2Norm * iterationWeight);
            });
            progressTracker.logProgress(partition.relationshipCount());
        }
    }

}
