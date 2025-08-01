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
package org.neo4j.gds.applications.algorithms.community;

import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.ResultStore;
import org.neo4j.gds.api.properties.nodes.NodePropertyValuesAdapter;
import org.neo4j.gds.applications.algorithms.machinery.WriteNodePropertyService;
import org.neo4j.gds.applications.algorithms.machinery.WriteStep;
import org.neo4j.gds.cliqueCounting.CliqueCountingResult;
import org.neo4j.gds.cliquecounting.CliqueCountingWriteConfig;
import org.neo4j.gds.core.utils.progress.JobId;

import static org.neo4j.gds.applications.algorithms.machinery.AlgorithmLabel.CliqueCounting;

class CliqueCountingWriteStep implements WriteStep<CliqueCountingResult, Void> {
    private final WriteNodePropertyService writeNodePropertyService;
    private final CliqueCountingWriteConfig configuration;

    CliqueCountingWriteStep(WriteNodePropertyService writeNodePropertyService, CliqueCountingWriteConfig configuration) {
        this.writeNodePropertyService = writeNodePropertyService;
        this.configuration = configuration;
    }

    @Override
    public Void execute(
        Graph graph,
        GraphStore graphStore,
        ResultStore resultStore,
        CliqueCountingResult result,
        JobId jobId
    ) {

        var nodePropertyValues = NodePropertyValuesAdapter.adapt(result.perNodeCount());

        writeNodePropertyService.perform(
            graph,
            graphStore,
            resultStore,
            configuration,
            configuration,
            CliqueCounting,
            jobId,
            nodePropertyValues
        );

        return null;
    }
}
