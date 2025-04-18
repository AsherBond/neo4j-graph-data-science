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
package org.neo4j.gds.procedures.integration;

import org.neo4j.gds.concurrency.ConcurrencyValidator;
import org.neo4j.gds.concurrency.ConcurrencyValidatorService;
import org.neo4j.gds.concurrency.PoolSizes;
import org.neo4j.gds.concurrency.PoolSizesService;
import org.neo4j.gds.core.IdMapBehavior;
import org.neo4j.gds.core.IdMapBehaviorServiceProvider;

/**
 * We have some services that sit as singletons. While that is unfortunate,
 * this is one centralised place where we configure them. You cannot have GDS without specifying these.
 */
class SingletonConfigurer {
    void configureSingletons(
        ConcurrencyValidator concurrencyValidator,
        IdMapBehavior idMapBehavior,
        PoolSizes poolSizes
    ) {
        configureConcurrencyValidator(concurrencyValidator);
        configureIdMapBehaviour(idMapBehavior);
        configurePoolSizes(poolSizes);
    }

    private void configureConcurrencyValidator(ConcurrencyValidator concurrencyValidator) {
        ConcurrencyValidatorService.validator(concurrencyValidator);
    }

    private void configureIdMapBehaviour(IdMapBehavior idMapBehavior) {
        IdMapBehaviorServiceProvider.idMapBehavior(idMapBehavior);
    }

    private void configurePoolSizes(PoolSizes poolSizes) {
        PoolSizesService.poolSizes(poolSizes);
    }
}
