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
package org.neo4j.gds.doc;

import org.neo4j.gds.catalog.GraphCreateProc;
import org.neo4j.gds.catalog.GraphStreamNodePropertiesProc;
import org.neo4j.gds.core.InjectModelCatalog;
import org.neo4j.gds.core.ModelCatalogExtension;
import org.neo4j.gds.core.loading.GraphStoreCatalog;
import org.neo4j.gds.core.model.ModelCatalog;
import org.neo4j.gds.functions.AsNodeFunc;
import org.neo4j.gds.ml.nodemodels.NodeClassificationPredictMutateProc;
import org.neo4j.gds.ml.nodemodels.NodeClassificationPredictStreamProc;
import org.neo4j.gds.ml.nodemodels.NodeClassificationPredictWriteProc;
import org.neo4j.gds.ml.nodemodels.NodeClassificationTrainProc;

import java.util.List;

@ModelCatalogExtension
class NodeClassificationDocTest extends DocTestBase {

    @InjectModelCatalog
    private ModelCatalog modelCatalog;

    @Override
    List<Class<?>> functions() {
        return List.of(AsNodeFunc.class);
    }

    @Override
    protected List<Class<?>> procedures() {
        return List.of(
            NodeClassificationTrainProc.class,
            NodeClassificationPredictStreamProc.class,
            NodeClassificationPredictMutateProc.class,
            NodeClassificationPredictWriteProc.class,
            GraphCreateProc.class,
            GraphStreamNodePropertiesProc.class
        );
    }

    @Override
    Runnable cleanup() {
        return () -> {
            GraphStoreCatalog.removeAllLoadedGraphs();
            modelCatalog.removeAllLoadedModels();
        };
    }

    @Override
    protected String adocFile() {
        return "algorithms/alpha/nodeclassification/nodeclassification.adoc";
    }
}
