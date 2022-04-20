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
package org.neo4j.gds.ml.pipeline;

import org.jetbrains.annotations.NotNull;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.config.AlgoBaseConfig;
import org.neo4j.gds.config.ToMapConvertible;
import org.neo4j.gds.core.utils.TimeUtil;
import org.neo4j.gds.models.TrainerConfig;
import org.neo4j.gds.models.TrainingMethod;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.neo4j.gds.config.MutatePropertyConfig.MUTATE_PROPERTY_KEY;
import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

public abstract class Pipeline<FEATURE_STEP extends FeatureStep> implements ToMapConvertible {

    protected final List<ExecutableNodePropertyStep> nodePropertySteps;
    protected final List<FEATURE_STEP> featureSteps;
    private final ZonedDateTime creationTime;

    protected Map<TrainingMethod, List<TrainerConfig>> trainingParameterSpace;

    public static Map<String, List<Map<String, Object>>> toMapParameterSpace(Map<TrainingMethod, List<TrainerConfig>> parameterSpace) {
        return parameterSpace.entrySet().stream()
            .collect(Collectors.toMap(
                entry -> entry.getKey().name(),
                entry -> entry.getValue().stream()
                    .map(TrainerConfig::toMapWithTrainerMethod)
                    .collect(Collectors.toList())
            ));
    }

    protected Pipeline() {
        this.nodePropertySteps = new ArrayList<>();
        this.featureSteps = new ArrayList<>();
        this.creationTime = TimeUtil.now();

        this.trainingParameterSpace = new EnumMap<>(TrainingMethod.class);

        Arrays.stream(TrainingMethod.values()).forEach(method -> trainingParameterSpace.put(method, new ArrayList<>()));
    }

    @Override
    public Map<String, Object> toMap() {
        // The pipeline's type and creation is not part of the map.
        Map<String, Object> map = new HashMap<>();
        map.put("featurePipeline", featurePipelineDescription());
        map.put(
            "trainingParameterSpace",
            toMapParameterSpace(trainingParameterSpace)
        );
        map.putAll(additionalEntries());
        return map;
    }

    public abstract String type();

    protected abstract Map<String, List<Map<String, Object>>> featurePipelineDescription();

    protected abstract Map<String, Object> additionalEntries();

    public void validateFeatureProperties(GraphStore graphStore, AlgoBaseConfig config) {
        Set<String> invalidProperties = featurePropertiesMissingFromGraph(graphStore, config);

        if (!invalidProperties.isEmpty()) {
            throw missingNodePropertiesFromFeatureSteps(invalidProperties);
        }
    }

    public void validateBeforeExecution(GraphStore graphStore, AlgoBaseConfig config) {
        Set<String> invalidProperties = featurePropertiesMissingFromGraph(graphStore, config);

        this.nodePropertySteps.stream()
            .flatMap(step -> Stream.ofNullable((String) step.config().get(MUTATE_PROPERTY_KEY)))
            .forEach(invalidProperties::remove);

        if (!invalidProperties.isEmpty()) {
            throw missingNodePropertiesFromFeatureSteps(invalidProperties);
        }
    }

    public int numberOfModelCandidates() {
        return this.trainingParameterSpace()
            .values()
            .stream()
            .mapToInt(List::size)
            .sum();
    }

    @NotNull
    private Set<String> featurePropertiesMissingFromGraph(GraphStore graphStore, AlgoBaseConfig config) {
        var graphProperties = graphStore.nodePropertyKeys(config.nodeLabelIdentifiers(graphStore));

        return featureSteps()
            .stream()
            .flatMap(step -> step.inputNodeProperties().stream())
            .filter(property -> !graphProperties.contains(property))
            .collect(Collectors.toSet());
    }

    @NotNull
    private static IllegalArgumentException missingNodePropertiesFromFeatureSteps(Set<String> invalidProperties) {
        return new IllegalArgumentException(formatWithLocale(
            "Node properties %s defined in the feature steps do not exist in the graph or part of the pipeline",
            invalidProperties.stream().sorted().collect(Collectors.toList())
        ));
    }

    public void addNodePropertyStep(NodePropertyStep step) {
        validateUniqueMutateProperty(step);
        this.nodePropertySteps.add(step);
    }

    public void addFeatureStep(FEATURE_STEP featureStep) {
        this.featureSteps.add(featureStep);
    }

    public List<ExecutableNodePropertyStep> nodePropertySteps() {
        return this.nodePropertySteps;
    }

    public List<FEATURE_STEP> featureSteps() {
        return this.featureSteps;
    }

    public Map<TrainingMethod, List<TrainerConfig>> trainingParameterSpace() {
        return trainingParameterSpace;
    }

    public void setTrainingParameterSpace(TrainingMethod method, List<TrainerConfig> trainingConfigs) {
        this.trainingParameterSpace.put(method, trainingConfigs);
    }

    public void addTrainerConfig(TrainingMethod method, TrainerConfig trainingConfigs) {
        this.trainingParameterSpace.get(method).add(trainingConfigs);
    }

    private void validateUniqueMutateProperty(NodePropertyStep step) {
        this.nodePropertySteps.forEach(nodePropertyStep -> {
            var newMutatePropertyName = step.config().get(MUTATE_PROPERTY_KEY);
            var existingMutatePropertyName = nodePropertyStep.config().get(MUTATE_PROPERTY_KEY);
            if (newMutatePropertyName.equals(existingMutatePropertyName)) {
                throw new IllegalArgumentException(formatWithLocale(
                    "The value of `%s` is expected to be unique, but %s was already specified in the %s procedure.",
                    MUTATE_PROPERTY_KEY,
                    newMutatePropertyName,
                    nodePropertyStep.procName()
                ));
            }
        });
    }

    public ZonedDateTime creationTime() {
        return creationTime;
    }
}
