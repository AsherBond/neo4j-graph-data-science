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
package org.neo4j.gds.core.utils.progress.tasks;

import org.immutables.value.Value;
import org.neo4j.gds.annotation.ValueClass;

@ValueClass
public interface Progress {
    long progress();

    long volume();

    @Value.Lazy
    default double relativeProgress() {
        if (volume() == Task.UNKNOWN_VOLUME) {
            return Task.UNKNOWN_VOLUME;
        }

        // progress can be larger if the volume was estimated too low (such as for cypher projections)
        return progress() >= volume()
            ? 1.0D
            : ((double) progress()) / volume();
    }
}
