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
package org.neo4j.gds.spanningtree;

import org.neo4j.gds.annotation.Configuration;
import org.neo4j.gds.config.AlgoBaseConfig;
import org.neo4j.gds.config.RelationshipWeightConfig;
import org.neo4j.gds.config.SourceNodeConfig;

import java.util.function.DoubleUnaryOperator;

@Configuration
public interface SpanningTreeBaseConfig extends
    AlgoBaseConfig,
    RelationshipWeightConfig,
    SourceNodeConfig {

    @Configuration.ConvertWith(method = "org.neo4j.gds.spanningtree.SpanningTreeCompanion#parse")
    @Configuration.ToMapValue("org.neo4j.gds.spanningtree.SpanningTreeCompanion#toString")
    default DoubleUnaryOperator objective() {
        return PrimOperators.MIN_OPERATOR;
    }

    @Configuration.Ignore
    default SpanningTreeParameters toParameters() {
        return new SpanningTreeParameters(objective(), sourceNode());
    }
}
