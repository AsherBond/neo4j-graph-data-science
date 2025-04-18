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
package org.neo4j.gds.hdbscan;

import org.junit.jupiter.api.Test;
import org.neo4j.gds.api.properties.nodes.DoubleArrayNodePropertyValues;
import org.neo4j.gds.api.properties.nodes.FloatArrayNodePropertyValues;

import static org.assertj.core.api.Assertions.assertThat;

class KDNodeSupportFactoryTest {

    @Test
    void shouldGenerateCorrect(){

        var doubleProps = new DoubleArrayNodePropertyValues() {
            @Override
            public double[] doubleArrayValue(long nodeId) {
                return new double[0];
            }

            @Override
            public long nodeCount() {
                return 5;
            }
        };

        var floatProps = new FloatArrayNodePropertyValues() {

            @Override
            public float[] floatArrayValue(long nodeId) {
                return new float[0];
            }

            @Override
            public long nodeCount() {
                return 5;
            }
        };

        assertThat(KDNodeSupportFactory.create(doubleProps,null,1)).isInstanceOf(DoubleKDNodeSupport.class);
        assertThat(KDNodeSupportFactory.create(floatProps,null,1)).isInstanceOf(FloatKDNodeSupport.class);

    }

}
