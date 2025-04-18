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
package org.neo4j.gds.articulationpoints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.gds.assertions.MemoryEstimationAssert;
import org.neo4j.gds.core.concurrency.Concurrency;

import java.util.stream.Stream;

class ArticulationPointsMemoryEstimateDefinitionTest {

    static Stream<Arguments> memoryEstimationTuples() {
        return Stream.of(
            Arguments.of(false, 217920L),
            Arguments.of(true,  222600L)
        );
    }

    @ParameterizedTest
    @MethodSource("memoryEstimationTuples")
    void shouldEstimateMemoryAccurately(boolean should, long expectedMemory) {
        var memoryEstimation = new ArticulationPointsMemoryEstimateDefinition(should).memoryEstimation();

        MemoryEstimationAssert.assertThat(memoryEstimation)
            .memoryRange(100, 6000, new Concurrency(1))
            .hasSameMinAndMaxEqualTo(expectedMemory);
    }
}
