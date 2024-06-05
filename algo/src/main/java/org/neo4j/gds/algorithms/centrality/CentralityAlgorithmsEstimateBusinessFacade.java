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
package org.neo4j.gds.algorithms.centrality;

import org.neo4j.gds.algorithms.estimation.AlgorithmEstimator;
import org.neo4j.gds.applications.algorithms.machinery.MemoryEstimateResult;
import org.neo4j.gds.pagerank.PageRankConfig;
import org.neo4j.gds.pagerank.PageRankMemoryEstimateDefinition;

public class CentralityAlgorithmsEstimateBusinessFacade {

    private final AlgorithmEstimator algorithmEstimator;

    public CentralityAlgorithmsEstimateBusinessFacade(
        AlgorithmEstimator algorithmEstimator
    ) {
        this.algorithmEstimator = algorithmEstimator;
    }

    public <C extends PageRankConfig> MemoryEstimateResult pageRank(
        Object graphNameOrConfiguration,
        C configuration
    ) {
        return pageRankVariant(graphNameOrConfiguration, configuration);
    }

    private <C extends PageRankConfig> MemoryEstimateResult pageRankVariant(
        Object graphNameOrConfiguration,
        C configuration
    ) {
        return algorithmEstimator.estimate(
            graphNameOrConfiguration,
            configuration,
            configuration.relationshipWeightProperty(),
            new PageRankMemoryEstimateDefinition()
        );
    }
}
