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
package org.neo4j.gds.procedures.operations;

import org.neo4j.gds.applications.ApplicationsFacade;
import org.neo4j.gds.core.utils.progress.JobId;
import org.neo4j.gds.core.utils.progress.tasks.Status;
import org.neo4j.gds.core.utils.warnings.UserLogEntry;

import java.util.stream.Stream;

public class LocalOperationsProcedureFacade implements OperationsProcedureFacade {
    private final ApplicationsFacade applicationsFacade;

    public LocalOperationsProcedureFacade(ApplicationsFacade applicationsFacade) {
        this.applicationsFacade = applicationsFacade;
    }

    @Override
    public void enableAdjacencyCompressionMemoryTracking(boolean value) {
        applicationsFacade.operations().enableAdjacencyCompressionMemoryTracking(value);
    }

    @Override
    public void enableArrowDatabaseImport(boolean value) {
        applicationsFacade.operations().enableArrowDatabaseImport(value);
    }

    @Override
    public Stream<ProgressResult> listProgress(String jobIdAsString, boolean showCompleted) {
        if (jobIdAsString.isBlank()) {
            var result = summaryView();
            if (!showCompleted) {
                return result.filter(i -> Status.valueOf(i.status).isOngoing());
            }
            return result;
        }
        var jobId = new JobId(jobIdAsString);
        return detailView(jobId);
    }

    @Override
    public Stream<UserLogEntry> queryUserLog(String jobId) {
        return applicationsFacade.operations().queryUserLog(jobId);
    }

    @Override
    public Stream<FeatureStringValue> resetAdjacencyPackingStrategy() {
        var canonicalStrategyIdentifier = applicationsFacade.operations().resetAdjacencyPackingStrategy();

        return Stream.of(new FeatureStringValue(canonicalStrategyIdentifier));
    }

    @Override
    public Stream<FeatureState> resetEnableAdjacencyCompressionMemoryTracking() {
        var isEnabled = applicationsFacade.operations().resetEnableAdjacencyCompressionMemoryTracking();

        return Stream.of(new FeatureState(isEnabled));
    }

    @Override
    public Stream<FeatureState> resetEnableArrowDatabaseImport() {
        var isEnabled = applicationsFacade.operations().resetEnableArrowDatabaseImport();

        return Stream.of(new FeatureState(isEnabled));
    }

    @Override
    public Stream<FeatureLongValue> resetPagesPerThread() {
        var pagesPerThread = applicationsFacade.operations().resetPagesPerThread();

        return Stream.of(new FeatureLongValue(pagesPerThread));
    }

    @Override
    public Stream<FeatureState> resetUseMixedAdjacencyList() {
        var isEnabled = applicationsFacade.operations().resetUseMixedAdjacencyList();

        return Stream.of(new FeatureState(isEnabled));
    }

    @Override
    public Stream<FeatureState> resetUsePackedAdjacencyList() {
        var isEnabled = applicationsFacade.operations().resetUsePackedAdjacencyList();

        return Stream.of(new FeatureState(isEnabled));
    }

    @Override
    public Stream<FeatureState> resetUseReorderedAdjacencyList() {
        var isEnabled = applicationsFacade.operations().resetUseReorderedAdjacencyList();

        return Stream.of(new FeatureState(isEnabled));
    }

    @Override
    public Stream<FeatureState> resetUseUncompressedAdjacencyList() {
        var isEnabled = applicationsFacade.operations().resetUseUncompressedAdjacencyList();

        return Stream.of(new FeatureState(isEnabled));
    }

    @Override
    public void setAdjacencyPackingStrategy(String strategyIdentifier) {
        applicationsFacade.operations().setAdjacencyPackingStrategy(strategyIdentifier);
    }

    @Override
    public void setPagesPerThread(long value) {
        applicationsFacade.operations().setPagesPerThread(value);
    }

    @Override
    public void setUseMixedAdjacencyList(boolean value) {
        applicationsFacade.operations().setUseMixedAdjacencyList(value);
    }

    @Override
    public void setUsePackedAdjacencyList(boolean value) {
        applicationsFacade.operations().setUsePackedAdjacencyList(value);
    }

    @Override
    public void setUseReorderedAdjacencyList(boolean value) {
        applicationsFacade.operations().setUseReorderedAdjacencyList(value);
    }

    @Override
    public void setUseUncompressedAdjacencyList(boolean value) {
        applicationsFacade.operations().setUseUncompressedAdjacencyList(value);
    }


    private Stream<ProgressResult> detailView(JobId jobId) {
        var resultRenderer = new DefaultResultRenderer(jobId);

        return applicationsFacade.operations().listProgress(jobId, resultRenderer);
    }

    private Stream<ProgressResult> summaryView() {
        var results = applicationsFacade.operations().listProgress();

        return results.map(ProgressResult::fromTaskStoreEntry);
    }
}
