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
package org.neo4j.gds.ml.linkmodels;

import org.neo4j.gds.annotation.ValueClass;
import org.neo4j.gds.config.ToMapConvertible;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.ml.linkmodels.logisticregression.LinkLogisticRegressionTrainConfig;
import org.neo4j.gds.ml.linkmodels.logisticregression.LinkLogisticRegressionTrainConfigImpl;
import org.neo4j.gds.ml.linkmodels.metrics.LinkMetric;
import org.neo4j.gds.ml.nodemodels.MetricData;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ValueClass
public interface LinkPredictionModelInfo extends ToMapConvertible {

    LinkLogisticRegressionTrainConfig bestParameters();

    Map<LinkMetric, MetricData<LinkLogisticRegressionTrainConfig>> metrics();

    @Override
    default Map<String, Object> toMap() {
        return Map.of(
            "bestParameters", bestParameters().toMap(),
            "metrics", metrics().entrySet().stream().collect(Collectors.toMap(
                entry -> entry.getKey().name(),
                entry -> entry.getValue().toMap()
            ))
        );
    }

    static LinkPredictionModelInfo of(
        LinkLogisticRegressionTrainConfig bestParameters,
        Map<LinkMetric, MetricData<LinkLogisticRegressionTrainConfig>> metrics
    ) {
        return ImmutableLinkPredictionModelInfo.of(bestParameters, metrics);
    }

    static LinkPredictionModelInfo defaultInfo() {
        return LinkPredictionModelInfo.of(
            new LinkLogisticRegressionTrainConfigImpl(List.of(), CypherMapWrapper.create(Map.of())),
            Map.of()
        );
    }
}
