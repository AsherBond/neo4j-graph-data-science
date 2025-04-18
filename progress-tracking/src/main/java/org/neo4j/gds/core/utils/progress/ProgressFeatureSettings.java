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
package org.neo4j.gds.core.utils.progress;

import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.configuration.Description;
import org.neo4j.configuration.SettingsDeclaration;
import org.neo4j.graphdb.config.Setting;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.neo4j.configuration.SettingValueParsers.BOOL;
import static org.neo4j.configuration.SettingValueParsers.DURATION;
import static org.neo4j.gds.compat.SettingProxy.newBuilder;

@ServiceProvider
public final class ProgressFeatureSettings implements SettingsDeclaration {

    @Description("Enable progress logging tracking.")
    public static final Setting<Boolean> progress_tracking_enabled = newBuilder(
        "gds.progress_tracking_enabled",
        BOOL,
        true
    ).build();

    @Description("Retention period for completed progress tracking jobs. This includes failed and successful ones")
    public static final Setting<Duration> task_retention_period = newBuilder(
        "gds.progress_tracking_retention_period",
        DURATION,
        Duration.of(0, ChronoUnit.SECONDS) // default to 0 to avoid breaking behaviour for plugin
    ).build();
}
