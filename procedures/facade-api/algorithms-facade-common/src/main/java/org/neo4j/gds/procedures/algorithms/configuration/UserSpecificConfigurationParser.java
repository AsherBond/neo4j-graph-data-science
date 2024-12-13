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
package org.neo4j.gds.procedures.algorithms.configuration;

import org.neo4j.gds.api.User;
import org.neo4j.gds.config.BaseConfig;
import org.neo4j.gds.core.CypherMapWrapper;

import java.util.Map;
import java.util.function.Function;

public class UserSpecificConfigurationParser {

    private final ConfigurationParser configurationParser;
    private final User user;

    public UserSpecificConfigurationParser(ConfigurationParser configurationParser, User user) {
        this.configurationParser = configurationParser;
        this.user = user;
    }

    public <CONFIG extends BaseConfig> CONFIG parseConfiguration(
        Map<String, Object> configuration,
        Function<CypherMapWrapper, CONFIG> lexer
    ) {
        return configurationParser.parseConfiguration(
            configuration,
            (__, cypherMapWrapper) -> lexer.apply(cypherMapWrapper),
            user
        );
    }

    public <CONFIG extends BaseConfig> CONFIG parseConfigurationWithoutDefaultsAndLimits(
        Map<String, Object> configuration,
        Function<CypherMapWrapper, CONFIG> lexer
    ) {
        return configurationParser.parseConfigurationWithoutDefaultsAndLimits(
            configuration,
            (__, cypherMapWrapper) -> lexer.apply(cypherMapWrapper),
            user.getUsername()
        );
    }
}
