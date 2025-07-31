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
package org.neo4j.gds.cliquecounting;

import org.neo4j.gds.annotation.Configuration;
import org.neo4j.gds.config.AlgoBaseConfig;

import java.util.List;

@Configuration
public interface CliqueCountingBaseConfig extends AlgoBaseConfig {

//    @Configuration.ToMapValue("org.neo4j.gds.similarity.knn.KnnSampler.SamplerType#toString")
    @Configuration.ToMapValue("org.neo4j.gds.cliquecounting.CliqueCountingMode#toString")
    CliqueCountingMode getMode();

    //todo: fix type
    default List<long[]> subcliques() {
        return List.of();
    }

    @Configuration.Ignore
    default CliqueCountingParameters toParameters() {
        return new CliqueCountingParameters(
            getMode(),
            subcliques(),
            concurrency()
        );
    }


}
