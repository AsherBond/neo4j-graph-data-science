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
package org.neo4j.gds.embeddings.graphsage;

import org.neo4j.gds.embeddings.graphsage.algo.LayerParameters;
import org.neo4j.gds.ml.core.functions.Weights;
import org.neo4j.gds.ml.core.tensor.Matrix;
import org.neo4j.gds.ml.core.tensor.Vector;

import java.util.Random;

import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

public final class LayerFactory {
    private LayerFactory() {}

    public static Layer createLayer(
        LayerParameters layerParameters
    ) {
        int rows = layerParameters.rows();
        int cols = layerParameters.cols();

        var activationFunctionType = layerParameters.activationFunction();
        var activationFunctionWrapper = ActivationFunctionFactory
            .activationFunctionWrapper(activationFunctionType);

        var randomSeed = layerParameters.randomSeed();
        Weights<Matrix> weights = generateWeights(
            rows,
            cols,
            activationFunctionWrapper.weightInitBound(rows, cols),
            randomSeed
        );

        switch (layerParameters.aggregatorType()) {
            case MEAN:
                return new MeanAggregatingLayer(
                    weights,
                    layerParameters.sampleSize(),
                    activationFunctionWrapper
                );
            case POOL:
                Weights<Matrix> poolWeights = weights;

                Weights<Matrix> selfWeights = generateWeights(
                    rows,
                    cols,
                    activationFunctionWrapper.weightInitBound(rows, cols),
                    randomSeed + 1
                );

                Weights<Matrix> neighborsWeights = generateWeights(
                    rows,
                    rows,
                    activationFunctionWrapper.weightInitBound(rows, rows),
                    randomSeed + 2
                );

                Weights<Vector> bias = new Weights<>(Vector.create(0D, rows));

                return new MaxPoolAggregatingLayer(
                    layerParameters.sampleSize(),
                    poolWeights,
                    selfWeights,
                    neighborsWeights,
                    bias,
                    activationFunctionWrapper
                );
            default:
                throw new IllegalArgumentException(formatWithLocale(
                    "Aggregator: %s is unknown",
                    layerParameters.aggregatorType()
                ));
        }
    }

    public static Weights<Matrix> generateWeights(int rows, int cols, double weightBound, long randomSeed) {

        double[] data = new Random(randomSeed)
            .doubles(Math.multiplyExact(rows, cols), -weightBound, weightBound)
            .toArray();

        return new Weights<>(new Matrix(
            data,
            rows,
            cols
        ));
    }

}
