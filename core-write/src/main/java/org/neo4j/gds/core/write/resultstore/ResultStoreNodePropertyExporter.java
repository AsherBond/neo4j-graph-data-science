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
package org.neo4j.gds.core.write.resultstore;

import org.neo4j.gds.api.ResultStore;
import org.neo4j.gds.api.ResultStoreEntry;
import org.neo4j.gds.api.properties.nodes.NodePropertyRecord;
import org.neo4j.gds.api.properties.nodes.NodePropertyValues;
import org.neo4j.gds.core.utils.progress.JobId;
import org.neo4j.gds.core.write.NodePropertyExporter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.LongUnaryOperator;

public class ResultStoreNodePropertyExporter implements NodePropertyExporter {

    private final JobId jobId;
    private final ResultStore resultStore;
    private final List<String> nodeLabels;
    private final LongUnaryOperator toOriginalId;
    private long writtenProperties;

    ResultStoreNodePropertyExporter(JobId jobId, ResultStore resultStore, List<String> nodeLabels, LongUnaryOperator toOriginalId) {
        this.jobId = jobId;
        this.resultStore = resultStore;
        this.nodeLabels = nodeLabels;
        this.toOriginalId = toOriginalId;
    }

    @Override
    public void write(String property, NodePropertyValues properties) {
        write(NodePropertyRecord.of(property, properties));
    }

    @Override
    public void write(NodePropertyRecord nodeProperty) {
        write(List.of(nodeProperty));
    }

    @Override
    public void write(Collection<NodePropertyRecord> nodeProperties) {
        var propertyKeys = new ArrayList<String>();
        var propertyValues = new ArrayList<NodePropertyValues>();
        nodeProperties.forEach(nodeProperty -> {
            propertyKeys.add(nodeProperty.key());
            propertyValues.add(nodeProperty.values());
            writtenProperties += nodeProperty.values().nodeCount();
        });

        resultStore.add(jobId, new ResultStoreEntry.NodeProperties(nodeLabels, propertyKeys, propertyValues, toOriginalId));
    }

    @Override
    public long propertiesWritten() {
        return writtenProperties;
    }
}
