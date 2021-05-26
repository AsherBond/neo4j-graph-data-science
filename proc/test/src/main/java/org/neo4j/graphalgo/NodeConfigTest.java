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
package org.neo4j.graphalgo;

import org.neo4j.graphalgo.config.AlgoBaseConfig;
import org.neo4j.graphalgo.config.ImmutableGraphCreateFromStoreConfig;
import org.neo4j.graphalgo.config.NodeConfig;
import org.neo4j.graphalgo.core.CypherMapWrapper;
import org.neo4j.graphalgo.core.loading.GraphStoreCatalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.neo4j.graphalgo.QueryRunner.runQuery;

public interface NodeConfigTest<ALGORITHM extends Algorithm<ALGORITHM, RESULT>, CONFIG extends NodeConfig & AlgoBaseConfig, RESULT> extends AlgoBaseProcTest<ALGORITHM, CONFIG, RESULT> {

    default void testNodeValidation(String configKey, String... expectedMessageSubstrings) {
        runQuery(graphDb(), "CREATE (:A)");

        var graphCreateConfig = ImmutableGraphCreateFromStoreConfig.of(
            "",
            "loadedGraph",
            NodeProjections.all(),
            RelationshipProjections.all()
        );

        long nodeId;

        try (var tx = graphDb().beginTx()) {
            nodeId = tx.getAllNodes().stream().findFirst().orElseThrow().getId();
        }

        GraphStoreCatalog.set(graphCreateConfig, graphLoader(graphCreateConfig).graphStore());

        var config = createMinimalConfig(CypherMapWrapper.empty())
            .withNumber(configKey, nodeId + 42L)
            .toMap();

        applyOnProcedure(proc -> {
            assertThatThrownBy(() -> proc.compute("loadedGraph", config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContainingAll(expectedMessageSubstrings);
        });
    }
}
