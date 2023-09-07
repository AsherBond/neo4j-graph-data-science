/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 * This file contains proprietary code that is only available via a commercial license from Neo4j.
 * For more information, see https://neo4j.com/contact-us/
 */
package org.neo4j.gds.compat._512;

import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.gds.compat.Neo4jProxyApi;
import org.neo4j.gds.compat.Neo4jProxyFactory;
import org.neo4j.gds.compat.Neo4jVersion;

@ServiceProvider
public final class Neo4jProxyFactoryImpl implements Neo4jProxyFactory {

    @Override
    public boolean canLoad(Neo4jVersion version) {
        return false;
    }

    @Override
    public Neo4jProxyApi load() {
        throw new UnsupportedOperationException("5.12 compatibility requires JDK17");
    }

    @Override
    public String description() {
        return "Neo4j 5.12 (placeholder)";
    }
}
