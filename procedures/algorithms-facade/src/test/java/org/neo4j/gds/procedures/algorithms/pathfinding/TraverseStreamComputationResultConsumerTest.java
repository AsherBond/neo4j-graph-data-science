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
package org.neo4j.gds.procedures.algorithms.pathfinding;

import org.junit.jupiter.api.Test;
import org.neo4j.gds.collections.ha.HugeLongArray;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.RelationshipType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class TraverseStreamComputationResultConsumerTest {

    @Test
    void shouldNotComputePath() {
        var pathFactoryFacade = PathFactoryFacade.create(false,null, true);
        var result = TraverseStreamComputationResultConsumer.consume(
            0L,
            HugeLongArray.of(1L, 2L),
            l -> l,
            TestResult::new,
            pathFactoryFacade,
            RelationshipType.withName("TEST")
        );

        assertThat(result)
            .hasSize(1)
            .allSatisfy(r -> {
                assertThat(r.path).isNull();
                assertThat(r.sourceNode).isEqualTo(0);
                assertThat(r.nodeIds).containsExactly(1L, 2L);
            });
    }

    @Test
    void shouldComputePath() {
        var pathFactoryFacadeMock = mock(PathFactoryFacade.class);
        doReturn(mock(Path.class)).when(pathFactoryFacadeMock).createPath(any(), any());
        var result = TraverseStreamComputationResultConsumer.consume(
            0L,
            HugeLongArray.of(1L, 2L),
            l -> l,
            TestResult::new,
            pathFactoryFacadeMock,
            RelationshipType.withName("TEST")
        );

        assertThat(result)
            .hasSize(1)
            .allSatisfy(r -> {
                assertThat(r.path).isNotNull();
                assertThat(r.sourceNode).isEqualTo(0);
                assertThat(r.nodeIds).containsExactly(1L, 2L);
            });
    }

    private static record TestResult(long sourceNode,List<Long> nodeIds, Path path){}

}
