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
package org.neo4j.gds.kmeans;


import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.annotation.Configuration;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.nodeproperties.ValueType;
import org.neo4j.gds.config.AlgoBaseConfig;
import org.neo4j.gds.config.IterationsConfig;
import org.neo4j.gds.config.RandomSeedConfig;
import org.neo4j.gds.utils.StringFormatting;

import java.util.Collection;
import java.util.List;

public interface KmeansBaseConfig extends AlgoBaseConfig, IterationsConfig, RandomSeedConfig {

    @Configuration.IntegerRange(min = 1)
    @Override
    default int maxIterations() {
        return 10;
    }

    @Configuration.IntegerRange(min = 1)
    default int k() {
        return 10;
    }

    @Configuration.DoubleRange(min = 0, max = 1)
    default double deltaThreshold() {
        return 0.05;
    }

    @Configuration.IntegerRange(min = 1)
    default int numberOfRestarts() {
        return 1;
    }

    default boolean computeSilhouette() {
        return false;
    }

    String nodeProperty();

    @Configuration.GraphStoreValidationCheck
    default void nodePropertyTypeValidation(
        GraphStore graphStore,
        Collection<NodeLabel> selectedLabels,
        Collection<RelationshipType> selectedRelationshipTypes
    ) {
        var valueType = graphStore.nodeProperty(nodeProperty()).valueType();
        if (valueType == ValueType.DOUBLE_ARRAY || valueType == ValueType.FLOAT_ARRAY) {
            return;
        }
        throw new IllegalArgumentException(
            StringFormatting.formatWithLocale(
                "Unsupported node property value type [%s]. Value type required: [%s] or [%s].",
                valueType,
                ValueType.DOUBLE_ARRAY,
                ValueType.FLOAT_ARRAY
            )
        );
    }

    @Configuration.ConvertWith(method = "org.neo4j.gds.kmeans.SamplerType#parse")
    @Configuration.ToMapValue("org.neo4j.gds.kmeans.SamplerType#toString")
    default SamplerType initialSampler() {
        return SamplerType.UNIFORM;
    }

    default List<List<Double>> seedCentroids() {
        return List.of();
    }

    @Configuration.Ignore
    default boolean isSeeded() {
        return !seedCentroids().isEmpty();
    }

    @Configuration.Ignore
    default KmeansParameters toParameters() {
        return new KmeansParameters(
            k(),
            maxIterations(),
            deltaThreshold(),
            numberOfRestarts(),
            computeSilhouette(),
            concurrency(),
            nodeProperty(),
            initialSampler(),
            seedCentroids(),
            randomSeed()
        );
    }

    @Configuration.Check
    default void validateSeedCentroids() {
        var seedCentroids = seedCentroids();
        if (numberOfRestarts() > 1 && !seedCentroids.isEmpty()) {
            throw new IllegalArgumentException("K-Means cannot be run multiple time when seeded");
        }
        if (!seedCentroids.isEmpty() && seedCentroids.size() != k()) {
            throw new IllegalArgumentException("Incorrect number of seeded centroids given for running K-Means");
        }
    }
}
