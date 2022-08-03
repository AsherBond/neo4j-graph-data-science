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
package org.neo4j.gds.leiden;

import org.neo4j.gds.api.Graph;
import org.neo4j.gds.core.concurrency.ParallelUtil;
import org.neo4j.gds.core.concurrency.RunWithConcurrency;
import org.neo4j.gds.core.utils.paged.HugeAtomicDoubleArray;
import org.neo4j.gds.core.utils.paged.HugeDoubleArray;
import org.neo4j.gds.core.utils.paged.HugeLongArray;
import org.neo4j.gds.core.utils.partition.Partition;
import org.neo4j.gds.core.utils.partition.PartitionUtils;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.stream.LongStream;

public final class ModularityComputer {

    private ModularityComputer() {}

    public static double modularity(Graph graph, HugeLongArray finalCommunities, double gamma) {
        double modularity = 0d;
        HugeDoubleArray sumOfEdges = HugeDoubleArray.newArray(graph.nodeCount());
        HugeDoubleArray insideEdges = HugeDoubleArray.newArray(graph.nodeCount());
        double coefficient = 1.0 / graph.relationshipCount();
        graph.forEachNode(
            nodeId -> {
                long communityId = finalCommunities.get(nodeId);
                graph.forEachRelationship(nodeId, 1.0, (s, t, w) -> {
                    long tCommunityId = finalCommunities.get(t);
                    if (communityId == tCommunityId)
                        insideEdges.addTo(communityId, w);
                    sumOfEdges.addTo(communityId, w);
                    return true;
                });
                return true;
            }
        );
        for (long community = 0; community < graph.nodeCount(); ++community) {
            double ec = insideEdges.get(community);
            double Kc = sumOfEdges.get(community);
            modularity += (ec - Kc * Kc * gamma);
        }
        return modularity * coefficient;
    }

    static double modularity(
        Graph workingGraph,
        HugeLongArray communities,
        HugeDoubleArray communityVolumes,
        double gamma,
        double coefficient,
        int concurrency,
        ExecutorService executorService
    ) {
        HugeAtomicDoubleArray relationshipsOutsideCommunity = HugeAtomicDoubleArray.newArray(workingGraph.nodeCount());
        var tasks = PartitionUtils.rangePartition(
            concurrency,
            workingGraph.nodeCount(),
            partition -> new OutsideRelationshipCalculator(
                partition,
                workingGraph,
                relationshipsOutsideCommunity,
                communities
            ), Optional.empty()
        );
        RunWithConcurrency.builder()
            .concurrency(concurrency)
            .tasks(tasks)
            .executor(executorService)
            .run();

        double modularity = ParallelUtil.parallelStream(
            LongStream.range(0, workingGraph.nodeCount()),
            concurrency,
            nodeStream ->
                nodeStream
                    .mapToDouble(communityId -> {
                        double oc = relationshipsOutsideCommunity.get(communityId);
                        double Kc = communityVolumes.get(communityId);
                        double ec = Kc - oc;
                        return ec - Kc * Kc * gamma;
                    })
                    .reduce(Double::sum)
                    .orElseThrow(() -> new RuntimeException("Error while computing modularity"))
        );
        //we do not have the self-loops from  previous merges so we settle from calculating the outside edges between relationships
        //from that and the total sum of weights in communityVolumes we can calculate all inside edges

        return modularity * coefficient;
    }

    static class OutsideRelationshipCalculator implements Runnable {
        private final Partition partition;
        private final Graph localGraph;
        private final HugeAtomicDoubleArray relationshipsOutsideCommunity;
        private final HugeLongArray communities;

        OutsideRelationshipCalculator(
            Partition partition,
            Graph graph,
            HugeAtomicDoubleArray relationshipsOutsideCommunity,
            HugeLongArray communities
        ) {
            this.partition = partition;
            this.localGraph = graph.concurrentCopy();
            this.relationshipsOutsideCommunity = relationshipsOutsideCommunity;
            this.communities = communities;
        }

        @Override
        public void run() {
            long startNode = partition.startNode();
            long endNode = startNode + partition.nodeCount();
            for (long nodeId = startNode; nodeId < endNode; ++nodeId) {
                long communityId = communities.get(nodeId);
                localGraph.forEachRelationship(nodeId, 1.0, (s, t, w) -> {
                    long tCommunityId = communities.get(t);
                    if (tCommunityId != communityId) {
                        relationshipsOutsideCommunity.getAndAdd(communityId, w);
                    }
                    return true;
                });
            }
        }
    }
}
