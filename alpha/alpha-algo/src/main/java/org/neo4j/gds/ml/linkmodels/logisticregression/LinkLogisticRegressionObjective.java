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
package org.neo4j.gds.ml.linkmodels.logisticregression;

import org.apache.commons.lang3.mutable.MutableInt;
import org.neo4j.gds.ml.Objective;
import org.neo4j.gds.ml.core.Dimensions;
import org.neo4j.gds.ml.core.Variable;
import org.neo4j.gds.ml.core.batch.Batch;
import org.neo4j.gds.ml.core.features.FeatureExtractor;
import org.neo4j.gds.ml.core.functions.Constant;
import org.neo4j.gds.ml.core.functions.ConstantScale;
import org.neo4j.gds.ml.core.functions.ElementSum;
import org.neo4j.gds.ml.core.functions.L2NormSquared;
import org.neo4j.gds.ml.core.functions.LogisticLoss;
import org.neo4j.gds.ml.core.functions.MatrixMultiplyWithTransposedSecondOperand;
import org.neo4j.gds.ml.core.functions.Weights;
import org.neo4j.gds.ml.core.tensor.Matrix;
import org.neo4j.gds.ml.core.tensor.Scalar;
import org.neo4j.gds.ml.core.tensor.Tensor;
import org.neo4j.gds.ml.core.tensor.Vector;
import org.neo4j.gds.ml.splitting.EdgeSplitter;
import org.neo4j.graphalgo.api.Graph;

import java.util.List;

import static org.neo4j.graphalgo.utils.StringFormatting.formatWithLocale;

public class LinkLogisticRegressionObjective extends LinkLogisticRegressionBase implements Objective<LinkLogisticRegressionData> {
    private final Graph graph;
    private final double penalty;

    public LinkLogisticRegressionObjective(
        LinkLogisticRegressionData llrData,
        List<String> featureProperties,
        List<FeatureExtractor> extractors,
        double penalty,
        Graph graph
    ) {
        super(llrData, featureProperties, extractors);
        this.graph = graph;
        this.penalty = penalty;
    }

    @SuppressWarnings({"PointlessArithmeticExpression", "UnnecessaryLocalVariable"})
    public static long sizeOfBatchInBytes(int batchSize, int numberOfFeatures) {
        // perThread
        var batchLocalWeightGradient = Weights.sizeInBytes(1, numberOfFeatures);
        var targets = Matrix.sizeInBytes(batchSize, 1);
        var weightedFeatures = MatrixMultiplyWithTransposedSecondOperand.sizeInBytes(
            Dimensions.matrix(batchSize, numberOfFeatures),
            Dimensions.matrix(1, numberOfFeatures)
        );
        var sigmoid = weightedFeatures;
        var unpenalizedLoss = Scalar.sizeInBytes();
        var l2norm = Scalar.sizeInBytes();
        var constantScale = Scalar.sizeInBytes();
        var elementSum = Scalar.sizeInBytes();

        long sizeOfPredictionsVariableInBytes = LinkLogisticRegressionPredictor.sizeOfBatchInBytes(
            batchSize,
            numberOfFeatures
        );

        return
            1 * targets +
            1 * weightedFeatures + // gradient
            1 * sigmoid +          // gradient
            2 * unpenalizedLoss +  // data and gradient
            2 * l2norm +           // data and gradient
            2 * constantScale +    // data and gradient
            2 * elementSum +       // data and gradient
            sizeOfPredictionsVariableInBytes +
            batchLocalWeightGradient;
    }

    static long costOfMakeTargets(int batchSize) {
        return Matrix.sizeInBytes(batchSize, 1);
    }

    @Override
    public List<Weights<? extends Tensor<?>>> weights() {
        return List.of(modelData.weights());
    }

    @Override
    public Variable<Scalar> loss(Batch batch, long trainSize) {
        // assume batching has been done so that relationship count does not overflow int
        int rows = 0;
        for (var nodeId : batch.nodeIds()) {
            rows += graph.degree(nodeId);
        }

        var features = features(graph, batch, rows);
        Variable<Matrix> predictions = predictions(features);
        var targets = makeTargetsArray(batch, rows);
        var penaltyVariable = new ConstantScale<>(new L2NormSquared(modelData.weights()), rows * penalty / trainSize);
        var unpenalizedLoss = new LogisticLoss(modelData.weights(), predictions, features, targets);
        return new ElementSum(List.of(unpenalizedLoss, penaltyVariable));
    }

    @Override
    public LinkLogisticRegressionData modelData() {
        return modelData;
    }

    private Constant<Vector> makeTargetsArray(Batch batch, int rows) {
        var graphCopy = graph.concurrentCopy();
        double[] targets = new double[rows];
        var relationshipOffset = new MutableInt();
        batch.nodeIds().forEach(nodeId -> {
            graphCopy.forEachRelationship(nodeId, -0.66, (src, trg, val) -> {
                if (Double.compare(val, EdgeSplitter.POSITIVE) == 0) {
                    targets[relationshipOffset.getValue()] = 1.0;
                } else if (Double.compare(val, EdgeSplitter.NEGATIVE) == 0) {
                    targets[relationshipOffset.getValue()] = 0.0;
                } else {
                    throw new IllegalArgumentException(formatWithLocale(
                        "The relationship property must have value %s or %s but it has %s",
                        EdgeSplitter.NEGATIVE,
                        EdgeSplitter.POSITIVE,
                        val
                    ));
                }
                relationshipOffset.increment();
                return true;
            });
        });
        return Constant.vector(targets);
    }
}
