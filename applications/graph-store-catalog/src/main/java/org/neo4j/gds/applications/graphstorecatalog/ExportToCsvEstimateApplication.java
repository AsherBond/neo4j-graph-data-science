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
package org.neo4j.gds.applications.graphstorecatalog;

import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.applications.algorithms.machinery.MemoryEstimateResult;
import org.neo4j.gds.applications.algorithms.machinery.MemoryEstimateResultFactory;
import org.neo4j.gds.core.GraphDimensions;
import org.neo4j.gds.core.concurrency.Concurrency;
import org.neo4j.gds.core.io.file.GraphStoreToCsvEstimationConfig;
import org.neo4j.gds.core.io.file.csv.estimation.CsvExportEstimation;

class ExportToCsvEstimateApplication {
    MemoryEstimateResult run(
        GraphStoreToCsvEstimationConfig configuration,
        GraphStore graphStore
    ) {
        var dimensions = GraphDimensions.of(graphStore.nodeCount(), graphStore.relationshipCount());
        var memoryTree = CsvExportEstimation
            .estimate(graphStore, configuration.samplingFactor())
            .estimate(dimensions, new Concurrency(1));

        return MemoryEstimateResultFactory.from(memoryTree, dimensions);
    }
}
