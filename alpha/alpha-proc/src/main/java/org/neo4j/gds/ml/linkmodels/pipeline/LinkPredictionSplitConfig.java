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
package org.neo4j.gds.ml.linkmodels.pipeline;


import org.immutables.value.Value;
import org.jetbrains.annotations.TestOnly;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.annotation.Configuration;
import org.neo4j.gds.annotation.ValueClass;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.config.ToMapConvertible;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.ml.splitting.SplitRelationshipsBaseConfig;
import org.neo4j.gds.ml.splitting.SplitRelationshipsBaseConfigImpl;
import org.neo4j.gds.utils.StringJoining;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

@ValueClass
@Configuration
public interface LinkPredictionSplitConfig extends ToMapConvertible {

    String TEST_FRACTION_KEY = "testFraction";
    String TRAIN_FRACTION_KEY = "trainFraction";
    LinkPredictionSplitConfig DEFAULT_CONFIG = LinkPredictionSplitConfig.of(CypherMapWrapper.empty());

    @Value.Default
    @Configuration.IntegerRange(min = 2)
    default int validationFolds() {
        return 3;
    }

    @Value.Default
    @Configuration.Key(TEST_FRACTION_KEY)
    @Configuration.DoubleRange(min = 0.0, minInclusive = false)
    default double testFraction() {
        return 0.1;
    }

    @Value.Default
    @Configuration.Key(TRAIN_FRACTION_KEY)
    @Configuration.DoubleRange(min = 0.0, minInclusive = false)
    default double trainFraction() {
        return 0.1;
    }

    @Value.Default
    @Configuration.DoubleRange(min = 0.0, minInclusive = false)
    default double negativeSamplingRatio() {
        return 1.0;
    }

    @Value.Default
    @Configuration.Ignore
    default String testRelationshipType() {
        return "_TEST_";
    }

    @Value.Default
    @Configuration.Ignore
    default String testComplementRelationshipType() {
        return "_TEST_COMPLEMENT_";
    }

    @Value.Default
    @Configuration.Ignore
    default String trainRelationshipType() {
        return  "_TRAIN_";
    }

    @Value.Default
    @Configuration.Ignore
    default String featureInputRelationshipType() {
        return  "_FEATURE_INPUT_";
    }

    @Override
    @Configuration.ToMap
    Map<String, Object> toMap();

    @Configuration.CollectKeys
    default Collection<String> configKeys() {
        return Collections.emptyList();
    }

    @Value.Derived
    @Configuration.Ignore
    default SplitRelationshipsBaseConfig testSplit() {
        return new SplitRelationshipsBaseConfigImpl(CypherMapWrapper.create(Map.of(
            "holdoutRelationshipType", testRelationshipType(),
            "remainingRelationshipType", testComplementRelationshipType(),
            "holdOutFraction", testFraction(),
            "negativeSamplingRatio", negativeSamplingRatio()
        )));
    }

    @Value.Derived
    @Configuration.Ignore
    default SplitRelationshipsBaseConfig trainSplit() {
        return new SplitRelationshipsBaseConfigImpl(CypherMapWrapper.create(Map.of(
            "holdoutRelationshipType", trainRelationshipType(),
            "remainingRelationshipType", featureInputRelationshipType(),
            "holdOutFraction", trainFraction(),
            "negativeSamplingRatio", negativeSamplingRatio()
        )));
    }

    static LinkPredictionSplitConfig of(CypherMapWrapper config) {
        return new LinkPredictionSplitConfigImpl(config);
    }

    @TestOnly
    static ImmutableLinkPredictionSplitConfig.Builder builder() {
        return ImmutableLinkPredictionSplitConfig.builder();
    }

    @Configuration.Ignore
    default void validateAgainstGraphStore(GraphStore graphStore) {
        var reservedTypes = Stream.of(
            testRelationshipType(),
            trainRelationshipType(),
            featureInputRelationshipType(),
            testComplementRelationshipType()
        );

        var invalidTypes = reservedTypes
            .filter(reservedType -> graphStore.hasRelationshipType(RelationshipType.of(reservedType)))
            .collect(Collectors.toList());

        if (!invalidTypes.isEmpty()) {
            throw new IllegalArgumentException(formatWithLocale(
                "The relationship types %s are in the input graph, but are reserved for splitting.",
                StringJoining.join(invalidTypes)
            ));
        }
    }
}
