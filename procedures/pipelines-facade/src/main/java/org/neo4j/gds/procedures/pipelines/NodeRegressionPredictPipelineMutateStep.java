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
package org.neo4j.gds.procedures.pipelines;

import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.properties.nodes.NodePropertyRecord;
import org.neo4j.gds.api.properties.nodes.NodePropertyValuesAdapter;
import org.neo4j.gds.applications.algorithms.machinery.GraphStoreService;
import org.neo4j.gds.applications.algorithms.machinery.MutateStep;
import org.neo4j.gds.applications.algorithms.metadata.NodePropertiesWritten;
import org.neo4j.gds.collections.ha.HugeDoubleArray;

import java.util.List;

class NodeRegressionPredictPipelineMutateStep implements MutateStep<HugeDoubleArray, NodePropertiesWritten> {
    private final GraphStoreService graphStoreService;
    private final NodeRegressionPredictPipelineMutateConfig configuration;

    NodeRegressionPredictPipelineMutateStep(
        GraphStoreService graphStoreService,
        NodeRegressionPredictPipelineMutateConfig configuration
    ) {
        this.graphStoreService = graphStoreService;
        this.configuration = configuration;
    }

    @Override
    public NodePropertiesWritten execute(Graph graph, GraphStore graphStore, HugeDoubleArray result) {
        var nodeProperties = List.of(
            NodePropertyRecord.of(
                configuration.mutateProperty(),
                NodePropertyValuesAdapter.adapt(result)
            )
        );

        return graphStoreService.addNodeProperties(graph, graphStore, configuration, nodeProperties);
    }
}
