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

public interface LoggerForProgressTracking {
    void info(String message);

    void info(String format, Object... arguments);

    void warn(String format, Object... arguments);

    void error(String format, Object... arguments);

    boolean isDebugEnabled();

    void debug(String format, Object... arguments);

    /**
     * Some convenience
     */
    static LoggerForProgressTracking noOpLog() {
        return new LoggerForProgressTracking() {

            @Override
            public void info(String message) {

            }

            @Override
            public void info(String format, Object... arguments) {

            }

            @Override
            public void warn(String format, Object... arguments) {

            }

            @Override
            public void error(String format, Object... arguments) {

            }

            @Override
            public boolean isDebugEnabled() {
                return false;
            }

            @Override
            public void debug(String format, Object... arguments) {

            }
        };
    }
}
