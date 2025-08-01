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
package org.neo4j.gds.gdl;

import org.immutables.value.Value;
import org.neo4j.gds.Orientation;
import org.neo4j.gds.annotation.Configuration;
import org.neo4j.gds.annotation.ValueClass;
import org.neo4j.gds.api.PropertyState;
import org.neo4j.gds.config.GraphProjectConfig;
import org.neo4j.gds.core.Aggregation;
import org.neo4j.gds.core.Username;

import java.util.List;
import java.util.Map;

@ValueClass
public interface GraphProjectFromGdlConfig extends GraphProjectConfig {

    String gdlGraph();

    @Value.Default
    @Override
    default String username() {
        return Username.EMPTY_USERNAME.username();
    }

    @Value.Default
    default Orientation orientation() {
        return Orientation.NATURAL;
    }

    @Value.Default
    default Aggregation aggregation() {
        return Aggregation.DEFAULT;
    }

    @Value.Default
    default PropertyState propertyState() {
        return PropertyState.TRANSIENT;
    }

    @Value.Default
    default boolean indexInverse() { return false; }

    @Configuration.Ignore
    default Map<String, Object> asProcedureResultConfigurationField() {
        return cleansed(toMap(), List.of());
    }

}
