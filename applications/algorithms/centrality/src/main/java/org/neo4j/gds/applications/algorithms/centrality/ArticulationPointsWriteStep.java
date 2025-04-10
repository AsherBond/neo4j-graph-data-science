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
package org.neo4j.gds.applications.algorithms.centrality;

import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.ResultStore;
import org.neo4j.gds.api.properties.nodes.LongNodePropertyValues;
import org.neo4j.gds.applications.algorithms.machinery.WriteStep;
import org.neo4j.gds.applications.algorithms.machinery.WriteToDatabase;
import org.neo4j.gds.applications.algorithms.metadata.NodePropertiesWritten;
import org.neo4j.gds.articulationpoints.ArticulationPointsResult;
import org.neo4j.gds.articulationpoints.ArticulationPointsWriteConfig;
import org.neo4j.gds.core.utils.progress.JobId;

import static org.neo4j.gds.applications.algorithms.machinery.AlgorithmLabel.ArticulationPoints;

class ArticulationPointsWriteStep implements WriteStep<ArticulationPointsResult, NodePropertiesWritten> {
    private final ArticulationPointsWriteConfig configuration;
    private final WriteToDatabase writeToDatabase;

    public ArticulationPointsWriteStep(
        ArticulationPointsWriteConfig configuration, WriteToDatabase writeToDatabase
    ) {
        this.configuration = configuration;
        this.writeToDatabase = writeToDatabase;
    }

    @Override
    public NodePropertiesWritten execute(
        Graph graph,
        GraphStore graphStore,
        ResultStore resultStore,
        ArticulationPointsResult articulationPoints,
        JobId jobId
    ) {
        var bitSet = articulationPoints.articulationPoints();
        var nodePropertyValues = new LongNodePropertyValues() {
            @Override
            public long longValue(long nodeId) {
                return bitSet.get(nodeId) ? 1 : 0;
            }

            @Override
            public long nodeCount() {
                return graph.nodeCount();
            }
        };

        return writeToDatabase.perform(
            graph,
            graphStore,
            resultStore,
            configuration,
            configuration,
            ArticulationPoints,
            jobId,
            nodePropertyValues
        );
    }
}
