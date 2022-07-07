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
package org.neo4j.gds.kmeans;

import org.junit.jupiter.api.Test;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.extension.GdlExtension;
import org.neo4j.gds.extension.GdlGraph;
import org.neo4j.gds.extension.IdFunction;
import org.neo4j.gds.extension.Inject;
import org.neo4j.gds.extension.TestGraph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@GdlExtension
class KmeansTest {
    @GdlGraph
    private static final String DB_CYPHER =
        "CREATE" +
        "  (a {  kmeans: [1.0, 1.0]} )" +
        "  (b {  kmeans: [1.0, 2.0]} )" +
        "  (c {  kmeans: [102.0, 100.0]} )" +
        "  (d {  kmeans: [100.0, 102.0]} )";
    @Inject
    private Graph graph;

    @GdlGraph(graphNamePrefix = "float")
    private static final String floatQuery =
        "CREATE" +
        "  (a {  kmeans: [1.0f, 1.0f]} )" +
        "  (b {  kmeans: [1.0f, 2.0f]} )" +
        "  (c {  kmeans: [102.0f, 100.0f]} )" +
        "  (d {  kmeans: [100.0f, 102.0f]} )";
    @Inject
    private Graph floatGraph;

    @GdlGraph(graphNamePrefix = "line")
    private static final String LineQuery =
        "CREATE" +
        "  (a {  kmeans: [0.21, 0.0]} )" +
        "  (b {  kmeans: [2.0, 0.0]} )" +
        "  (c {  kmeans: [2.1, 0.0]} )" +
        "  (d {  kmeans: [3.8, 0.0]} )" +
        "  (e {  kmeans: [2.1, 0.0]} )";

    @Inject
    private TestGraph lineGraph;

    @GdlGraph(graphNamePrefix = "nan")
    private static final String nanQuery =
        "CREATE" +
        "  (a {  kmeans: [0.21d, 0.0d]} )" +
        "  (b {  kmeans: [2.0d, NaN]} )" +
        "  (c {  kmeans: [2.1d, 0.0d]} )";

    @Inject
    private TestGraph nanGraph;


    @GdlGraph(graphNamePrefix = "miss")
    private static final String missQuery =
        "CREATE" +
        "  (a {  kmeans: [0.21d, 0.0d]} )" +
        "  (b {  kmeans: [2.0d]} )" +
        "  (c {  kmeans: [2.1d, 0.0d]} )";

    @Inject
    private TestGraph missGraph;

    @Inject
    private IdFunction idFunction;

    @Test
    void shouldThrowOnNan() {
        var kmeansConfig = ImmutableKmeansStreamConfig.builder()
            .nodeProperty("kmeans")
            .concurrency(1)
            .randomSeed(19L)
            .k(2)
            .build();
        var kmeansContext = ImmutableKmeansContext.builder().build();
        var kmeans = Kmeans.createKmeans(nanGraph, kmeansConfig, kmeansContext);
        assertThatThrownBy(kmeans::compute).hasMessageContaining(
            "Input for K-Means should not contain any NaN values");

    }

    @Test
    void shouldThrowOnDifferentDimensions() {
        var kmeansConfig = ImmutableKmeansStreamConfig.builder()
            .nodeProperty("kmeans")
            .concurrency(1)
            .randomSeed(19L)
            .k(2)
            .build();
        var kmeansContext = ImmutableKmeansContext.builder().build();
        var kmeans = Kmeans.createKmeans(missGraph, kmeansConfig, kmeansContext);
        assertThatThrownBy(kmeans::compute).hasMessageContaining(
            "All property arrays for K-Means should have the same number of dimensions");

    }

    @Test
    void shouldRun() {
        var kmeansConfig = ImmutableKmeansStreamConfig.builder()
            .nodeProperty("kmeans")
            .concurrency(1)
            .randomSeed(19L)
            .k(2)
            .build();
        var kmeansContext = ImmutableKmeansContext.builder().build();

        var kmeans = Kmeans.createKmeans(graph, kmeansConfig, kmeansContext);
        var result = kmeans.compute().communities();
        assertThat(result.get(0)).isEqualTo(result.get(1));
        assertThat(result.get(2)).isEqualTo(result.get(3));
        assertThat(result.get(0)).isNotEqualTo(result.get(2));
    }

    @Test
    void shouldRunOnFloatGraph() {
        var kmeansConfig = ImmutableKmeansStreamConfig.builder()
            .nodeProperty("kmeans")
            .concurrency(1)
            .randomSeed(19L)
            .k(2)
            .build();
        var kmeansContext = ImmutableKmeansContext.builder().build();

        var kmeans = Kmeans.createKmeans(floatGraph, kmeansConfig, kmeansContext);
        var result = kmeans.compute().communities();
        assertThat(result.get(0)).isEqualTo(result.get(1));
        assertThat(result.get(2)).isEqualTo(result.get(3));
        assertThat(result.get(0)).isNotEqualTo(result.get(2));
    }

    @Test
    void shouldWorkOnLineGraphWithOneIteration() {
        var kmeansConfig = ImmutableKmeansStreamConfig.builder()
            .nodeProperty("kmeans")
            .concurrency(1)
            .randomSeed(19L)
            .k(2)
            .maxIterations(1)
            .build();
        var kmeansContext = ImmutableKmeansContext.builder().build();

        var kmeans = Kmeans.createKmeans(lineGraph, kmeansConfig, kmeansContext);
        var result = kmeans.compute().communities();
        assertThat(result.get(0)).isEqualTo(result.get(1));
        assertThat(result.get(2)).isEqualTo(result.get(3)).isEqualTo(result.get(4));
        assertThat(result.get(0)).isNotEqualTo(result.get(2));
    }

    @Test
    void shouldChangeOnLineGraphWithTwoIterations() {
        var kmeansConfig = ImmutableKmeansStreamConfig.builder()
            .nodeProperty("kmeans")
            .concurrency(1)
            .randomSeed(19L) //init clusters 0.21 and 3.8
            .k(2)
            .maxIterations(2)
            .build();
        var kmeansContext = ImmutableKmeansContext.builder().build();

        var kmeans = Kmeans.createKmeans(lineGraph, kmeansConfig, kmeansContext);
        var result = kmeans.compute().communities();

        assertThat(result.get(1)).isEqualTo(result.get(2)).isEqualTo(result.get(3)).isEqualTo(result.get(4));
        assertThat(result.get(0)).isNotEqualTo(result.get(1));
    }
}
