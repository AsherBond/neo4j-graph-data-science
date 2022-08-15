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
package org.neo4j.gds.compat;

import org.neo4j.gds.annotation.SuppressForbidden;
import org.neo4j.gds.annotation.ValueClass;
import org.neo4j.logging.Log;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ProxyUtil {

    private static final AtomicBoolean LOG_ENVIRONMENT = new AtomicBoolean(true);

    private static final Map<Class<?>, ProxyInfo<?>> PROXY_INFO_CACHE = new ConcurrentHashMap<>();

    public static <PROXY, FACTORY extends ProxyFactory<PROXY>> PROXY findProxy(Class<FACTORY> factoryClass) {
        return findProxyInfo(factoryClass)
            .factory()
            .orElseThrow(() -> new IllegalStateException("We already validated that the factory class exists"))
            .load();
    }

    public static <PROXY, FACTORY extends ProxyFactory<PROXY>> ProxyInfo<FACTORY> findProxyInfo(Class<FACTORY> factoryClass) {
        // we know that this type is correct due to the signature of loadAndValidateProxyInfo
        // we lose the type information because of the map API
        //noinspection unchecked
        return (ProxyInfo<FACTORY>) PROXY_INFO_CACHE.computeIfAbsent(
            factoryClass,
            fc -> loadAndValidateProxyInfo(factoryClass)
        );
    }

    @SuppressForbidden(reason = "This is the best we can do at the moment")
    private static <PROXY, FACTORY extends ProxyFactory<PROXY>> ProxyInfo<FACTORY> loadAndValidateProxyInfo(Class<FACTORY> factoryClass) {
        var log = new OutputStreamLogBuilder(System.out).build();
        var availabilityLog = new StringJoiner(", ", "GDS compatibility: ", "");
        availabilityLog.setEmptyValue("");

        var proxyInfo = loadProxyInfo(factoryClass);

        // log any errors while looking for the GDS version, but continue since we have a fallback value
        proxyInfo.gdsVersion().error().ifPresent(e -> e.log(log));

        try {

            // log any errors while looking for the Neo4j version
            // stop execution since we don't have a valid Neo4j version fallback value
            var neo4jVersionError = proxyInfo.neo4jVersion().error();
            if (neo4jVersionError.isPresent()) {
                neo4jVersionError.get().log(log);
                throw new RuntimeException(neo4jVersionError.get().reason());
            }

            // log any errors while trying to find all proxies
            // stop execution since we don't have any proxy instances
            var proxyError = proxyInfo.error();
            if (proxyError.isPresent()) {
                proxyError.get().log(log);
                throw new RuntimeException(proxyError.get().reason());
            }

            // log availability of all proxy implementations
            proxyInfo.availability().forEach((name, availability) -> {
                availabilityLog.add(String.format(
                    Locale.ENGLISH,
                    "for %s -- %s",
                    name,
                    availability ? "available" : "not available"
                ));
            });

            // make sure that we have a proxy available
            var factory = proxyInfo.factory();
            if (factory.isPresent()) {
                availabilityLog.add("selected: " + factory.get().description());
            } else {
                throw new LinkageError(String.format(
                    Locale.ENGLISH,
                    "GDS %s is not compatible with Neo4j version: %s",
                    proxyInfo.gdsVersion().gdsVersion(),
                    proxyInfo.neo4jVersion().neo4jVersion()
                ));
            }

            return proxyInfo;
        } finally {
            if (LOG_ENVIRONMENT.getAndSet(false)) {
                log.debug(
                    "Java vendor: [%s] Java version: [%s] Java home: [%s] GDS version: [%s] Detected Neo4j version: [%s]",
                    proxyInfo.javaInfo().javaVendor(),
                    proxyInfo.javaInfo().javaVersion(),
                    proxyInfo.javaInfo().javaHome(),
                    proxyInfo.gdsVersion().gdsVersion(),
                    proxyInfo.neo4jVersion().neo4jVersion()
                );
            }
            var availability = availabilityLog.toString();
            if (!availability.isEmpty()) {
                log.info(availability);
            }
        }
    }

    private static <PROXY, FACTORY extends ProxyFactory<PROXY>> ProxyInfo<FACTORY> loadProxyInfo(Class<FACTORY> factoryClass) {
        var builder = ImmutableProxyInfo
            .<FACTORY>builder()
            .factoryType(factoryClass)
            .neo4jVersion(NEO4J_VERSION_INFO)
            .gdsVersion(GDS_VERSION_INFO)
            .javaInfo(JAVA_INFO);

        try {
            Optional<FACTORY> proxyFactory = ServiceLoader
                .load(factoryClass)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(f -> {
                    var canLoad = f.canLoad(NEO4J_VERSION_INFO.neo4jVersion());
                    builder.putAvailability(f.description(), canLoad);
                    return canLoad;
                })
                .findFirst();

            builder.factory(proxyFactory);
        } catch (Exception e) {
            builder.error(ImmutableErrorInfo
                .builder()
                .logLevel(LogLevel.ERROR)
                .message("Could not load GDS proxy: " + e.getMessage())
                .reason(e)
                .build()
            );
        }

        return builder.build();
    }

    private static final Neo4jVersionInfo NEO4J_VERSION_INFO = loadNeo4jVersion();

    private static Neo4jVersionInfo loadNeo4jVersion() {
        try {
            var neo4jVersion = GraphDatabaseApiProxy.neo4jVersion();
            return ImmutableNeo4jVersionInfo.builder().neo4jVersion(neo4jVersion).build();
        } catch (Exception e) {
            return ImmutableNeo4jVersionInfo.builder()
                .error(ImmutableErrorInfo
                    .builder()
                    .logLevel(LogLevel.WARN)
                    .message("Could not determine Neo4j version: " + e.getMessage())
                    .reason(e)
                    .build()
                )
                .neo4jVersion(Neo4jVersion.V_Dev)
                .build();
        }
    }

    private static final GdsVersionInfo GDS_VERSION_INFO = loadGdsVersion();

    private static GdsVersionInfo loadGdsVersion() {
        var builder = ImmutableGdsVersionInfo.builder();
        try {
            // The class that we use to get the GDS version lives in proc-sysinfo, which is part of the released GDS jar,
            // but we don't want to depend on that here. One reason is that this class gets generated and re-generated
            // on every build and having it at the top of the dependency graph would cause a lot of recompilation.
            // Let's do a bit of class loading and reflection to get the version.
            var lookup = MethodHandles.lookup();

            var buildInfoPropertiesClass = Class.forName("org.neo4j.gds.BuildInfoProperties");

            // equivalent to: BuildInfoProperties.get()
            var buildInfoPropertiesHandle = lookup.findStatic(
                buildInfoPropertiesClass,
                "get",
                MethodType.methodType(buildInfoPropertiesClass)
            );

            // equivalent to: buildInfoProperties.gdsVersion()
            var gdsVersionHandle = lookup.findVirtual(
                buildInfoPropertiesClass,
                "gdsVersion",
                MethodType.methodType(String.class)
            );

            // var buildInfoProperties = BuildInfoProperties.get()
            var buildInfoProperties = buildInfoPropertiesHandle.invoke();
            // var gdsVersion = buildInfoProperties.gdsVersion()
            var gdsVersion = gdsVersionHandle.invoke(buildInfoProperties);

            return builder
                .gdsVersion(String.valueOf(gdsVersion))
                .build();
        } catch (ClassNotFoundException e) {
            builder.error(ImmutableErrorInfo.builder()
                .logLevel(LogLevel.DEBUG)
                .message(
                    "Could not determine GDS version, BuildInfoProperties is missing. " +
                    "This is likely due to not running GDS as a plugin, " +
                    "for example when running tests or using GDS as a Java module dependency."
                )
                .reason(e)
                .build()
            );
        } catch (NoSuchMethodException | IllegalAccessException e) {
            builder.error(ImmutableErrorInfo.builder()
                .logLevel(LogLevel.WARN)
                .message(
                    "Could not determine GDS version, the according methods on BuildInfoProperties could not be found.")
                .reason(e)
                .build()
            );
        } catch (Throwable e) {
            builder.error(ImmutableErrorInfo.builder()
                .logLevel(LogLevel.WARN)
                .message("Could not determine GDS version, the according methods on BuildInfoProperties failed.")
                .reason(e)
                .build()
            );
        }

        return builder.gdsVersion("Unknown").build();
    }

    private static final JavaInfo JAVA_INFO = loadJavaInfo();

    private static JavaInfo loadJavaInfo() {
        return ImmutableJavaInfo.builder()
            .javaVendor(System.getProperty("java.vendor"))
            .javaVersion(System.getProperty("java.version"))
            .javaHome(System.getProperty("java.home"))
            .build();
    }

    @ValueClass
    public interface ProxyInfo<T> {
        Class<T> factoryType();

        Neo4jVersionInfo neo4jVersion();

        GdsVersionInfo gdsVersion();

        JavaInfo javaInfo();

        Map<String, Boolean> availability();

        Optional<T> factory();

        Optional<ErrorInfo> error();
    }

    @ValueClass
    public interface Neo4jVersionInfo {

        Neo4jVersion neo4jVersion();

        Optional<ErrorInfo> error();
    }

    @ValueClass
    public interface GdsVersionInfo {

        String gdsVersion();

        Optional<ErrorInfo> error();
    }

    @ValueClass
    public interface ErrorInfo {

        String message();

        LogLevel logLevel();

        Throwable reason();

        default void log(Log log) {
            switch (logLevel()) {
                case DEBUG:
                    log.debug(message(), reason());
                    break;
                case INFO:
                    log.info(message(), reason());
                    break;
                case WARN:
                    log.warn(message(), reason());
                    break;
                case ERROR:
                    log.error(message(), reason());
                    break;
            }
        }
    }

    public enum LogLevel {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    @ValueClass
    public interface JavaInfo {
        String javaVendor();

        String javaVersion();

        String javaHome();
    }

    private ProxyUtil() {}
}
