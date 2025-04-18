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
package org.neo4j.gds.applications.algorithms.embeddings;

import org.neo4j.gds.embeddings.graphsage.algo.GraphSageBaseConfig;

import java.util.Optional;

public class GraphSageAlgorithmProcessing {
    private final GraphSageModelCatalog graphSageModelCatalog;

    public GraphSageAlgorithmProcessing(
        GraphSageModelCatalog graphSageModelCatalog
    ) {
        this.graphSageModelCatalog = graphSageModelCatalog;
    }


   <CONFIGURATION extends GraphSageBaseConfig> GraphSageProcessParameters graphSageValidationHook( CONFIGURATION configuration){
       var model = graphSageModelCatalog.get(configuration.username(), configuration.modelName());
       var relationshipWeightPropertyFromTrainConfiguration = model.trainConfig().relationshipWeightProperty();

       var validationHook = new GraphSageValidationHook(configuration, model);
        return new GraphSageProcessParameters(validationHook,relationshipWeightPropertyFromTrainConfiguration);
    }

    record GraphSageProcessParameters(GraphSageValidationHook validationHook, Optional<String> relationshipWeightPropertyFromTrainConfiguration){}
}
