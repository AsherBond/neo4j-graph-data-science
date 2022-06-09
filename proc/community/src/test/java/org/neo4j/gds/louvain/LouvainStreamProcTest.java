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
package org.neo4j.gds.louvain;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.gds.AlgoBaseProc;
import org.neo4j.gds.ConsecutiveIdsConfigTest;
import org.neo4j.gds.GdsCypher;
import org.neo4j.gds.core.CypherMapWrapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.gds.CommunityHelper.assertCommunities;

class LouvainStreamProcTest extends LouvainProcTest<LouvainStreamConfig> implements
    ConsecutiveIdsConfigTest<Louvain, LouvainStreamConfig, Louvain> {

    @Override
    public Class<? extends AlgoBaseProc<Louvain, Louvain, LouvainStreamConfig>> getProcedureClazz() {
        return LouvainStreamProc.class;
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("org.neo4j.gds.louvain.LouvainProcTest#graphVariations")
    void testStream(GdsCypher.QueryBuilder queryBuilder, String testCaseName) {
        @Language("Cypher") String query = queryBuilder
            .algo("louvain")
            .streamMode()
            .yields("nodeId", "communityId", "intermediateCommunityIds");

        List<Long> actualCommunities = new ArrayList<>();
        runQueryWithRowConsumer(query, row -> {
            int id = row.getNumber("nodeId").intValue();
            long community = row.getNumber("communityId").longValue();
            assertNull(row.get("intermediateCommunityIds"));
            actualCommunities.add(id, community);
        });
        assertCommunities(actualCommunities, RESULT);
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("org.neo4j.gds.louvain.LouvainProcTest#graphVariations")
    void testStreamConsecutiveIds(GdsCypher.QueryBuilder queryBuilder, String testCaseName) {
        @Language("Cypher") String query = queryBuilder
            .algo("louvain")
            .streamMode()
            .addParameter("consecutiveIds", true)
            .yields("nodeId", "communityId", "intermediateCommunityIds");

        var communityMap = new HashSet<Long>();

        List<Long> actualCommunities = new ArrayList<>();
        runQueryWithRowConsumer(query, row -> {
            int id = row.getNumber("nodeId").intValue();
            long community = row.getNumber("communityId").longValue();
            communityMap.add(community);
            assertNull(row.get("intermediateCommunityIds"));
            actualCommunities.add(id, community);
        });
        assertCommunities(actualCommunities, RESULT);
        assertThat(communityMap).hasSize(3).containsExactlyInAnyOrder(0L, 1L, 2L);

    }


    @ParameterizedTest(name = "{1}")
    @MethodSource("org.neo4j.gds.louvain.LouvainProcTest#graphVariations")
    void testStreamCommunities(GdsCypher.QueryBuilder queryBuilder, String testCaseName) {
        @Language("Cypher") String query = queryBuilder
            .algo("louvain")
            .streamMode()
            .addParameter("includeIntermediateCommunities", true)
            .yields("nodeId", "communityId", "intermediateCommunityIds");

        runQueryWithRowConsumer(query, row -> {
            Object maybeList = row.get("intermediateCommunityIds");
            assertTrue(maybeList instanceof List);
            List<Long> communities = (List<Long>) maybeList;
            assertEquals(2, communities.size());
            assertEquals(communities.get(1), row.getNumber("communityId").longValue());
        });
    }

    @Test
    void testCreateConfigWithDefaults() {
        LouvainBaseConfig louvainConfig = LouvainStreamConfig.of(
            "",
            Optional.empty(),
            Optional.empty(),
            CypherMapWrapper.empty()
        );
        assertEquals(false, louvainConfig.includeIntermediateCommunities());
        assertEquals(10, louvainConfig.maxLevels());
    }

    @Override
    public LouvainStreamConfig createConfig(CypherMapWrapper mapWrapper) {
        return LouvainStreamConfig.of("", Optional.empty(), Optional.empty(), mapWrapper);
    }
}
