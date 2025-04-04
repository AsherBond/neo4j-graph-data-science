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
package org.neo4j.gds.leiden;

import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.annotation.Configuration;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.config.AlgoBaseConfig;
import org.neo4j.gds.config.ConsecutiveIdsConfig;
import org.neo4j.gds.config.RandomSeedConfig;
import org.neo4j.gds.config.RelationshipWeightConfig;
import org.neo4j.gds.config.SeedConfig;
import org.neo4j.gds.config.ToleranceConfig;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

public interface LeidenBaseConfig extends
    AlgoBaseConfig,
    ConsecutiveIdsConfig,
    RelationshipWeightConfig,
    RandomSeedConfig,
    SeedConfig,
    ToleranceConfig {

    default double gamma() {
        return 1.0;
    }

    default double theta() {
        return 0.01;
    }

    default int maxLevels() {
        return 10;
    }

    default boolean includeIntermediateCommunities() {
        return false;
    }

    @Override
    @Configuration.DoubleRange(min = 0D)
    default double tolerance() {
        return 0.0001;
    }

    @Configuration.Check
    default void validate() {
        if (includeIntermediateCommunities() && consecutiveIds()) {
            throw new IllegalArgumentException(
                "`includeIntermediateResults` and the `consecutiveIds` option cannot be used at the same time.");
        }
    }

    @Configuration.GraphStoreValidationCheck
    default void validateUndirectedGraph(
        GraphStore graphStore,
        Collection<NodeLabel> ignored,
        Collection<RelationshipType> selectedRelationshipTypes
    ) {
        if (!graphStore.schema().filterRelationshipTypes(Set.copyOf(selectedRelationshipTypes)).isUndirected()) {
            throw new IllegalArgumentException(formatWithLocale(
                "Leiden requires relationship projections to be UNDIRECTED. " +
                    "Selected relationships `%s` are not all undirected.",
                selectedRelationshipTypes.stream().map(RelationshipType::name).collect(Collectors.toSet())
            ));
        }
    }


    @Configuration.Ignore
    default LeidenParameters toParameters() {
        return new LeidenParameters(concurrency(), tolerance(), seedProperty(), maxLevels(), gamma(), theta(), includeIntermediateCommunities(), randomSeed());
    }

    @Configuration.Ignore
    default LeidenMemoryEstimationParameters toMemoryEstimationParameters() {
        return new LeidenMemoryEstimationParameters(seedProperty(), includeIntermediateCommunities(), maxLevels());
    }
}
