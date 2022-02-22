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
package org.neo4j.gds;

import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.NodeProperties;
import org.neo4j.gds.config.MutatePropertyConfig;
import org.neo4j.gds.core.huge.FilteredNodeProperties;
import org.neo4j.gds.core.utils.ProgressTimer;
import org.neo4j.gds.core.write.ImmutableNodeProperty;
import org.neo4j.gds.core.write.NodeProperty;
import org.neo4j.gds.result.AbstractResultBuilder;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public abstract class MutatePropertyProc<
    ALGO extends Algorithm<ALGO, ALGO_RESULT>,
    ALGO_RESULT,
    PROC_RESULT,
    CONFIG extends MutatePropertyConfig> extends MutateProc<ALGO, ALGO_RESULT, PROC_RESULT, CONFIG> {

    @Override
    protected NodeProperties nodeProperties(ComputationResult<ALGO, ALGO_RESULT, CONFIG> computationResult) {
        throw new UnsupportedOperationException(
            "Mutate procedures must implement either `nodeProperties` or `nodePropertyList`.");
    }

    protected List<NodeProperty> nodePropertyList(ComputationResult<ALGO, ALGO_RESULT, CONFIG> computationResult) {
        return List.of(ImmutableNodeProperty.of(
            computationResult.config().mutateProperty(),
            nodeProperties(computationResult)
        ));
    }

    @Override
    protected void updateGraphStore(
        AbstractResultBuilder<?> resultBuilder,
        ComputationResult<ALGO, ALGO_RESULT, CONFIG> computationResult
    ) {
        Graph graph = computationResult.graph();

        final var nodeProperties = nodePropertyList(computationResult);

        var maybeTranslatedProperties = graph.asNodeFilteredGraph()
            .map(filteredGraph -> nodeProperties.stream()
                .map(nodeProperty ->
                    ImmutableNodeProperty.of(
                        nodeProperty.propertyKey(),
                        new FilteredNodeProperties.OriginalToFilteredNodeProperties(
                            nodeProperty.properties(),
                            filteredGraph
                        )
                    )
                ).collect(Collectors.toList())
            ).orElse(nodeProperties);

        MutatePropertyConfig mutatePropertyConfig = computationResult.config();

        try (ProgressTimer ignored = ProgressTimer.start(resultBuilder::withMutateMillis)) {
            log.debug("Updating in-memory graph store");
            GraphStore graphStore = computationResult.graphStore();
            Collection<NodeLabel> labelsToUpdate = mutatePropertyConfig.nodeLabelIdentifiers(graphStore);

            maybeTranslatedProperties.forEach(nodeProperty -> {
                for (NodeLabel label : labelsToUpdate) {
                    graphStore.addNodeProperty(
                        label,
                        nodeProperty.propertyKey(),
                        nodeProperty.properties()
                    );
                }
            });

            resultBuilder.withNodePropertiesWritten(maybeTranslatedProperties.size() * computationResult.graph().nodeCount());
        }
    }
}
