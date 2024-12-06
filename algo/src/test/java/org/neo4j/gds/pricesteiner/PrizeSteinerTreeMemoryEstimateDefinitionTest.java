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
package org.neo4j.gds.pricesteiner;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.gds.assertions.MemoryEstimationAssert;
import org.neo4j.gds.core.concurrency.Concurrency;

import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.arguments;

class PrizeSteinerTreeMemoryEstimateDefinitionTest {

    static Stream<Arguments> memoryEstimationTuples() {
        return Stream.of(
            arguments(100_000,500_000, 56_439_201L, 62_418_808L),
            arguments(100_000,2_000_000, 146_439_201L, 152_418_808L)
        );
    }

    @ParameterizedTest
    @MethodSource("memoryEstimationTuples")
    void memoryEstimation(int nodeCount, long relCount, long expectedMin, long expectedMax) {

        var memoryEstimation = new PrizeSteinerTreeMemoryEstimateDefinition().memoryEstimation();

        MemoryEstimationAssert.assertThat(memoryEstimation).
            memoryRange(nodeCount,relCount, new Concurrency(1))
            .hasRange(expectedMin,expectedMax);

    }


}
