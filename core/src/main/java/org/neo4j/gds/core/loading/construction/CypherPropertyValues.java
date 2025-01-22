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
package org.neo4j.gds.core.loading.construction;

import org.neo4j.gds.core.loading.GdsNeo4jValueConverter;
import org.neo4j.gds.values.GdsValue;
import org.neo4j.values.virtual.MapValue;

import java.util.function.BiConsumer;

final class CypherPropertyValues extends PropertyValues {
    private final MapValue properties;

    CypherPropertyValues(MapValue properties) {
        this.properties = properties;
    }

    @Override
    public void forEach(BiConsumer<String, GdsValue> consumer) {
        this.properties.foreach((k, v) -> {
            consumer.accept(k, GdsNeo4jValueConverter.toValue(v));
        });
    }

    @Override
    public boolean isEmpty() {
        return this.properties.isEmpty();
    }

    @Override
    public int size() {
        return this.properties.size();
    }

    @Override
    public Iterable<String> propertyKeys() {
        return this.properties.keySet();
    }

    @Override
    public GdsValue get(String key) {
        return GdsNeo4jValueConverter.toValue(properties.get(key));
    }
}
