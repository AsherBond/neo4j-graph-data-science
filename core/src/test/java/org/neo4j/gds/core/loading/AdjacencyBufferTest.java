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
package org.neo4j.gds.core.loading;

import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Test;
import org.neo4j.gds.Orientation;
import org.neo4j.gds.PropertyMappings;
import org.neo4j.gds.RelationshipProjection;
import org.neo4j.gds.core.Aggregation;
import org.neo4j.gds.core.ImmutableGraphDimensions;
import org.neo4j.gds.core.compression.common.MemoryTracker;
import org.neo4j.gds.core.compression.varlong.CompressedAdjacencyListBuilderFactory;
import org.neo4j.gds.core.compression.varlong.DeltaVarLongCompressor;
import org.neo4j.gds.core.concurrency.Concurrency;
import org.neo4j.gds.core.utils.RawValues;
import org.neo4j.gds.mem.MemoryTree;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AdjacencyBufferTest {

    @Test
    void skipsNodeIdsThatShouldntBeThereWhenBuildingAdjacencyLists() {
        var nodeCount = 7L;
        var metadata = SingleTypeRelationshipImporter.ImportMetaData.of(
            RelationshipProjection.of(
                "T",
                Orientation.NATURAL
            ), 1, Map.of(), false
        );
        var factory = DeltaVarLongCompressor.factory(
            () -> nodeCount,
            CompressedAdjacencyListBuilderFactory.of(),
            PropertyMappings.builder().build(),
            new Aggregation[]{Aggregation.NONE},
            true,
            MemoryTracker.EMPTY
        );
        var adjacencyBuffer = AdjacencyBuffer.of(
            metadata,
            factory,
            ImportSizing.of(new Concurrency(4), nodeCount)
        );

        var relationshipsBatchBuffer = new RelationshipsBatchBufferBuilder<Integer>()
            .capacity(6)
            .propertyReferenceClass(Integer.class)
            .build();

        // more unique original node ids than nodeCount
        relationshipsBatchBuffer.add(0, 1);
        relationshipsBatchBuffer.add(2, 3);
        relationshipsBatchBuffer.add(4, 5);
        relationshipsBatchBuffer.add(4, 7); // should exclude the id 7
        relationshipsBatchBuffer.add(6, 8); // should exclude the id 8
        relationshipsBatchBuffer.add(9, 0); // should exclude the id 9

        var importer = ThreadLocalSingleTypeRelationshipImporter.of(
            adjacencyBuffer,
            relationshipsBatchBuffer,
            metadata,
            null // no properties
        );

        var numberOfElementsImported = importer.importRelationships();
        var numberOfRelationshipsImported = RawValues.getHead(numberOfElementsImported);
        assertThat(numberOfRelationshipsImported).isEqualTo(6); // we get all in the initial run

        var tasks = adjacencyBuffer.adjacencyListBuilderTasks(Optional.empty(), Optional.empty());

        // but when we build the compressed adjacency lists we will skip one node and its two rels
        tasks.forEach(Runnable::run);

        var adjacencyList = factory.build(false).adjacency();
        assertThat(adjacencyList.degree(0)).isEqualTo(1);
        assertThat(adjacencyList.degree(1)).isEqualTo(0);
        assertThat(adjacencyList.degree(2)).isEqualTo(1);
        assertThat(adjacencyList.degree(3)).isEqualTo(0);
        assertThat(adjacencyList.degree(4)).isEqualTo(1);
        assertThat(adjacencyList.degree(5)).isEqualTo(0);
        assertThat(adjacencyList.degree(6)).isEqualTo(0);
    }

    @Test
    void memoryEstimationShouldGrowLinearlyWithNodeCount() {
        var estimationWith1Property = estimate(10_000_000, 10, 1, new Concurrency(4));
        var estimationWith2Property = estimate(100_000_000, 10, 1, new Concurrency(4));
        var estimationWith3Property = estimate(1_000_000_000, 10, 1, new Concurrency(4));

        var min1 = estimationWith1Property.memoryUsage().min;
        var min2 = estimationWith2Property.memoryUsage().min;
        var min3 = estimationWith3Property.memoryUsage().min;
        assertThat((double) min1 / min2).isCloseTo((double) min2 / min3, Percentage.withPercentage(20));

        var max1 = estimationWith1Property.memoryUsage().max;
        var max2 = estimationWith2Property.memoryUsage().max;
        var max3 = estimationWith3Property.memoryUsage().max;
        assertThat((double) max1 / max2).isCloseTo((double) max2 / max3, Percentage.withPercentage(20));
    }

    private MemoryTree estimate(long nodeCount, long avgDegree, int propertyCount, Concurrency concurrency) {
        var dimensions = ImmutableGraphDimensions
            .builder()
            .nodeCount(nodeCount)
            .build();

        return AdjacencyBuffer.memoryEstimation(avgDegree, nodeCount, propertyCount, concurrency).estimate(dimensions, concurrency);
    }
}
