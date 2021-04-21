/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.delta.item;

import java.util.function.Function;

import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.dsl.EnumPath;

import com.evolveum.midpoint.repo.sqale.RootUpdateContext;

/**
 * Delta processor for an attribute path (Prism item) of enum type that is mapped to matching
 * PostgreSQL enum type - this allows to use schema enums directly.
 */
public class EnumItemDeltaProcessor<E extends Enum<E>>
        extends SinglePathItemDeltaProcessor<E, EnumPath<E>> {

    public EnumItemDeltaProcessor(RootUpdateContext<?, ?, ?> context,
            Function<EntityPath<?>, EnumPath<E>> rootToQueryItem) {
        super(context, rootToQueryItem);
    }
}
