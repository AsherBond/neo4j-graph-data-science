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
package org.neo4j.gds.beta.pregel;

import org.neo4j.gds.annotation.Configuration;
import org.neo4j.gds.config.AlgoBaseConfig;
import org.neo4j.gds.config.ConcurrencyConfig;
import org.neo4j.gds.config.IterationsConfig;
import org.neo4j.gds.config.RelationshipWeightConfig;

@Configuration
public interface PregelConfig extends
    AlgoBaseConfig,
    RelationshipWeightConfig,
    IterationsConfig,
    ConcurrencyConfig {

    @Configuration.Key("isAsynchronous")
    default boolean isAsynchronous() {
        return false;
    }

    @Configuration.ConvertWith(method = "org.neo4j.gds.beta.pregel.Partitioning#parse")
    @Configuration.ToMapValue("org.neo4j.gds.beta.pregel.Partitioning#toString")
    default Partitioning partitioning() {
        return Partitioning.RANGE;
    }

    @Configuration.Ignore
    default boolean useForkJoin() {
        return partitioning() == Partitioning.AUTO;
    }

    @Configuration.Ignore
    default boolean trackSender() {
        return false;
    }
}
