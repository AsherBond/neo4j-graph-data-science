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
package org.neo4j.gds.ml.linkmodels;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.gds.BaseProcTest;
import org.neo4j.gds.GdsCypher;
import org.neo4j.gds.Orientation;
import org.neo4j.gds.RelationshipProjection;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.api.schema.GraphSchema;
import org.neo4j.gds.catalog.GraphCreateProc;
import org.neo4j.gds.catalog.GraphStreamRelationshipPropertiesProc;
import org.neo4j.gds.core.loading.GraphStoreCatalog;
import org.neo4j.gds.core.model.Model;
import org.neo4j.gds.core.model.ModelCatalog;
import org.neo4j.gds.extension.Inject;
import org.neo4j.gds.extension.Neo4jModelCatalogExtension;
import org.neo4j.gds.ml.core.functions.Weights;
import org.neo4j.gds.ml.core.tensor.Matrix;
import org.neo4j.gds.ml.linkmodels.logisticregression.LinkFeatureCombiners;
import org.neo4j.gds.ml.linkmodels.logisticregression.LinkLogisticRegressionData;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.isA;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.neo4j.gds.Orientation.UNDIRECTED;

@Neo4jModelCatalogExtension
class LinkPredictionPredictWriteProcTest extends BaseProcTest {

    private static final String GRAPH =
        "CREATE " +
        "(a:N {a: 0}), " +
        "(b:N {a: 0}), " +
        "(c:N {a: 0}), " +
        "(d:N {a: 100}), " +
        "(e:N {a: 100}), " +
        "(f:N {a: 100}), " +
        "(g:N {a: 200}), " +
        "(h:N {a: 200}), " +
        "(i:N {a: 200}), " +
        "(j:N {a: 300}), " +
        "(k:N {a: 300}), " +
        "(l:N {a: 300}), " +
        "(m:N {a: 400}), " +
        "(n:N {a: 400}), " +
        "(o:N {a: 400})";

    @Inject
    private ModelCatalog modelCatalog;

    @BeforeEach
    void setUp() throws Exception {
        registerProcedures(LinkPredictionPredictWriteProc.class, GraphStreamRelationshipPropertiesProc.class, GraphCreateProc.class);
        runQuery(GRAPH);

        runQuery(createQuery("g", UNDIRECTED));
    }

    private String createQuery(String graphName, Orientation orientation) {
        return GdsCypher
            .call()
            .withNodeLabel("N")
            .withNodeProperty("a")
            .withRelationshipType("IGNORED", RelationshipProjection.of("*", orientation))
            .graphCreate(graphName)
            .yields();
    }

    @Test
    void canWrite() {
        var graphStore = GraphStoreCatalog
            .get(getUsername(), db.databaseId(), "g")
            .graphStore();
        addModel("model", graphStore.schema());

        var query = GdsCypher
            .call()
            .explicitCreation("g")
            .algo("gds.alpha.ml.linkPrediction.predict")
            .writeMode()
            .addParameter("writeRelationshipType", "PREDICTED")
            .addParameter("modelName", "model")
            .addParameter("threshold", 0.5)
            .addParameter("topN", 10)
            .yields();

        assertCypherResult(query, List.of(Map.of(
            "createMillis", greaterThan(-1L),
            "computeMillis", greaterThan(-1L),
            "writeMillis", greaterThan(-1L),
            "postProcessingMillis", 0L,
            // we are writing undirected rels so we get 2x topN
            "relationshipsWritten", 20L,
            "configuration", isA(Map.class)
        )));
        var validationQuery =
            "MATCH ()-[r:PREDICTED]->() " +
            "RETURN r.probability AS probability " +
            "ORDER BY probability DESC";

        assertCypherResult(validationQuery, List.of(
            Map.of("probability", 0.5646362918030292),
            Map.of("probability", 0.5646362918030292),
            Map.of("probability", 0.5646362918030292),
            Map.of("probability", 0.5646362918030292),
            Map.of("probability", 0.5646362918030292),
            Map.of("probability", 0.5646362918030292),
            Map.of("probability", 0.5646362918030292),
            Map.of("probability", 0.5646362918030292),
            Map.of("probability", 0.5646362918030292),
            Map.of("probability", 0.5646362918030292),
            Map.of("probability", 0.5646362918030292),
            Map.of("probability", 0.5646362918030292),
            Map.of("probability", 0.5646362918030292),
            Map.of("probability", 0.5646362918030292),
            Map.of("probability", 0.5646362918030292),
            Map.of("probability", 0.5646362918030292),
            Map.of("probability", 0.5646362918030292),
            Map.of("probability", 0.5646362918030292),
            Map.of("probability", 0.5473576181430894),
            Map.of("probability", 0.5473576181430894)
        ));
    }

    @Test
    void requiresUndirectedGraph() {
        runQuery(createQuery("g2", Orientation.NATURAL));

        addModel("model", GraphSchema.empty());

        var query = GdsCypher
            .call()
            .explicitCreation("g2")
            .algo("gds.alpha.ml.linkPrediction.predict")
            .writeMode()
            .addParameter("writeRelationshipType", "PREDICTED")
            .addParameter("modelName", "model")
            .addParameter("threshold", 0.5)
            .addParameter("topN", 10)
            .yields();

        assertError(query, "Procedure requires relationship projections to be UNDIRECTED.");
    }

    private void addModel(String modelName, GraphSchema graphSchema) {
        List<String> featureProperties = List.of("a");
        modelCatalog.set(Model.of(
            getUsername(),
            modelName,
            LinkPredictionTrain.MODEL_TYPE,
            graphSchema,
            LinkLogisticRegressionData
                .builder()
                .weights(
                    new Weights<>(new Matrix(
                        new double[]{0.000001, 0.1}, 1, 2)
                    )
                )
                .linkFeatureCombiner(LinkFeatureCombiners.L2)
                .nodeFeatureDimension(2)
                .build(),
            ImmutableLinkPredictionTrainConfig.builder()
                .modelName("model")
                .validationFolds(1)
                .trainRelationshipType(RelationshipType.ALL_RELATIONSHIPS)
                .testRelationshipType(RelationshipType.ALL_RELATIONSHIPS)
                .featureProperties(featureProperties)
                .negativeClassWeight(1d)
                .build(),
            LinkPredictionModelInfo.defaultInfo()
        ));
    }
}
