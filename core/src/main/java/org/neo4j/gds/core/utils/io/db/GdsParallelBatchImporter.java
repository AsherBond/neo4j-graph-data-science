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
package org.neo4j.gds.core.utils.io.db;

import org.neo4j.common.DependencyResolver;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.gds.compat.CompatInput;
import org.neo4j.gds.compat.Neo4jProxy;
import org.neo4j.gds.core.Settings;
import org.neo4j.gds.core.utils.ProgressTimer;
import org.neo4j.internal.batchimport.AdditionalInitialIds;
import org.neo4j.internal.batchimport.BatchImporter;
import org.neo4j.internal.batchimport.BatchImporterFactory;
import org.neo4j.internal.batchimport.input.Collector;
import org.neo4j.internal.batchimport.input.Collectors;
import org.neo4j.internal.batchimport.staging.ExecutionMonitor;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.io.layout.Neo4jLayout;
import org.neo4j.io.pagecache.tracing.PageCacheTracer;
import org.neo4j.kernel.impl.scheduler.JobSchedulerFactory;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.kernel.lifecycle.LifeSupport;
import org.neo4j.logging.Log;
import org.neo4j.logging.internal.LogService;
import org.neo4j.logging.internal.NullLogService;
import org.neo4j.scheduler.JobScheduler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

import static org.neo4j.configuration.GraphDatabaseSettings.SYSTEM_DATABASE_NAME;
import static org.neo4j.gds.core.utils.io.GraphStoreExporter.DIRECTORY_IS_WRITABLE;
import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;
import static org.neo4j.internal.batchimport.input.BadCollector.UNLIMITED_TOLERANCE;

public final class GdsParallelBatchImporter {

    private final GraphStoreToDatabaseExporterConfig config;
    private final Log log;
    private final ExecutionMonitor executionMonitor;

    private final DependencyResolver dependencyResolver;
    private final FileSystemAbstraction fs;
    private final LogService logService;
    private final Config databaseConfig;
    private final DatabaseManagementService dbms;

    public static GdsParallelBatchImporter fromDb(
        GraphDatabaseAPI db,
        GraphStoreToDatabaseExporterConfig config,
        Log log,
        ExecutionMonitor executionMonitor
    ) {
        var dependencyResolver = db.getDependencyResolver();
        var dbms = dependencyResolver.resolveDependency(DatabaseManagementService.class);
        var fs = dependencyResolver.resolveDependency(FileSystemAbstraction.class);
        var logService = dependencyResolver.resolveDependency(LogService.class);
        var databaseConfig = dependencyResolver.resolveDependency(Config.class);
        return new GdsParallelBatchImporter(
            config,
            log,
            executionMonitor,
            dbms,
            dependencyResolver,
            fs,
            logService,
            databaseConfig
        );
    }

    public static GdsParallelBatchImporter fromDbms(
        DatabaseManagementService dbms,
        GraphStoreToDatabaseExporterConfig config,
        Log log,
        ExecutionMonitor executionMonitor
    ) {
        var db = (GraphDatabaseAPI) dbms.database(SYSTEM_DATABASE_NAME);
        var dependencyResolver = db.getDependencyResolver();
        var fs = dependencyResolver.resolveDependency(FileSystemAbstraction.class);
        var logService = dependencyResolver.resolveDependency(LogService.class);
        var databaseConfig = dependencyResolver.resolveDependency(Config.class);
        return new GdsParallelBatchImporter(
            config,
            log,
            executionMonitor,
            dbms,
            dependencyResolver,
            fs,
            logService,
            databaseConfig
        );
    }

    private GdsParallelBatchImporter(
        GraphStoreToDatabaseExporterConfig config,
        Log log,
        ExecutionMonitor executionMonitor,
        DatabaseManagementService dbms,
        DependencyResolver dependencyResolver,
        FileSystemAbstraction fs,
        LogService logService,
        Config databaseConfig
    ) {
        this.config = config;
        this.log = log;
        this.executionMonitor = executionMonitor;
        this.dbms = dbms;
        this.dependencyResolver = dependencyResolver;
        this.fs = fs;
        this.logService = logService;

        var configBuilder = Config
            .newBuilder()
            .fromConfig(databaseConfig)
            .set(Settings.neo4jHome(), databaseConfig.get(Settings.neo4jHome()))
            .set(GraphDatabaseSettings.data_directory, databaseConfig.get(GraphDatabaseSettings.data_directory));

        Neo4jProxy.setAllowUpgrades(configBuilder, true);
        Neo4jProxy.configureRecordFormat(configBuilder, config.recordFormat());

        this.databaseConfig = configBuilder.build();
    }

    public void writeDatabase(CompatInput compatInput, boolean startDatabase) {
        log.info("Database import started");

        var importTimer = ProgressTimer.start();

        var databaseLayout = Neo4jLayout.of(databaseConfig).databaseLayout(config.dbName());

        validateWritableDirectories(databaseLayout);
        validateDatabaseDoesNotExist(databaseLayout);

        var lifeSupport = new LifeSupport();

        try {
            if (config.force()) {
                fs.deleteRecursively(databaseLayout.databaseDirectory());
                fs.deleteRecursively(databaseLayout.getTransactionLogsDirectory());
            }

            var logService = getLogService();
            var collector = getCollector();
            var jobScheduler = lifeSupport.add(JobSchedulerFactory.createScheduler());

            lifeSupport.start();

            var input = Neo4jProxy.batchInputFrom(compatInput);
            var batchImporter = instantiateBatchImporter(databaseLayout, logService, collector, jobScheduler);
            batchImporter.doImport(input);
            log.info(formatWithLocale("Database import finished after %s ms", importTimer.stop().getDuration()));

            if (startDatabase) {
                var dbStartTimer = ProgressTimer.start();
                if (createAndStartDatabase()) {
                    log.info(formatWithLocale(
                        "Database created and started after %s ms",
                        dbStartTimer.stop().getDuration()
                    ));
                } else {
                    log.error("Unable to start database " + config.dbName());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            lifeSupport.shutdown();
        }
    }

    private void validateWritableDirectories(DatabaseLayout databaseLayout) {
        DIRECTORY_IS_WRITABLE.validate(databaseLayout.databaseDirectory());
        DIRECTORY_IS_WRITABLE.validate(databaseLayout.getTransactionLogsDirectory());
    }

    private void validateDatabaseDoesNotExist(DatabaseLayout databaseLayout) {
        var metaDataPath = databaseLayout.metadataStore();
        var dbExists = Files.exists(metaDataPath) && Files.isReadable(metaDataPath);
        if (dbExists) {
            throw new IllegalArgumentException(formatWithLocale(
                "The database [%s] already exists. The graph export procedure can only create new databases.",
                config.dbName()
            ));
        }
    }

    private LogService getLogService() {
        return config.enableDebugLog()
            ? logService
            : NullLogService.getInstance();
    }

    private Collector getCollector() {
        return config.useBadCollector()
            ? Collectors.badCollector(new LoggingOutputStream(log), UNLIMITED_TOLERANCE)
            : Collector.EMPTY;
    }

    private BatchImporter instantiateBatchImporter(
        DatabaseLayout databaseLayout,
        LogService logService,
        Collector collector,
        JobScheduler jobScheduler
    ) {
        return Neo4jProxy.instantiateBatchImporter(
            BatchImporterFactory.withHighestPriority(),
            databaseLayout,
            fs,
            PageCacheTracer.NULL,
            config.toBatchImporterConfig(),
            logService,
            executionMonitor,
            AdditionalInitialIds.EMPTY,
            databaseConfig,
            Neo4jProxy.recordFormatSelector(
                config.dbName(),
                databaseConfig,
                fs,
                logService,
                dependencyResolver
            ),
            jobScheduler,
            collector
        );
    }

    private boolean createAndStartDatabase() {
        var databaseName = config.dbName();
        dbms.createDatabase(databaseName);
        dbms.startDatabase(databaseName);

        var db = dbms.database(databaseName);

        var numRetries = 10;
        for (int i = 0; i < numRetries; i++) {
            if (db.isAvailable(1000)) {
                return true;
            }
            log.info(formatWithLocale("Database not available, retry %d of %d", i, numRetries));
        }
        return false;
    }
}
