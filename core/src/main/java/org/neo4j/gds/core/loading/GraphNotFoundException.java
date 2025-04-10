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
package org.neo4j.gds.core.loading;

import java.util.NoSuchElementException;

import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

public class GraphNotFoundException extends NoSuchElementException {
    private final GraphStoreCatalog.UserCatalog.UserCatalogKey userCatalogKey;

    public GraphNotFoundException(GraphStoreCatalog.UserCatalog.UserCatalogKey userCatalogKey) {
        super(
            formatWithLocale(
                "Graph with name `%s` does not exist on database `%s`. It might exist on another database.",
                userCatalogKey.graphName(),
                userCatalogKey.databaseName()
            )
        );
        this.userCatalogKey = userCatalogKey;
    }

    public String graphName() {
        return userCatalogKey.graphName();
    }

    public String databaseName() {
        return userCatalogKey.databaseName();
    }
}
