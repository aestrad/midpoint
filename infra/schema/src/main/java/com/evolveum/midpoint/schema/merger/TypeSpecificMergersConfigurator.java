/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.schema.merger;

import com.evolveum.midpoint.schema.merger.key.DefaultNaturalKeyImpl;
import com.evolveum.midpoint.schema.merger.key.SingletonItemPathNaturalKeyImpl;
import com.evolveum.midpoint.schema.merger.objdef.LimitationsMerger;
import com.evolveum.midpoint.schema.merger.resource.ObjectTypeDefinitionMerger;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import java.util.Map;
import java.util.function.Supplier;

import static java.util.Map.entry;

/**
 * Separate class to hold the configuration of type-specific item mergers.
 */
class TypeSpecificMergersConfigurator {

    static Map<Class<?>, Supplier<ItemMerger>> createStandardTypeSpecificMergersMap() {
        return Map.ofEntries(
                // for ResourceType
                entry(
                        ConnectorInstanceSpecificationType.class,
                        () -> new GenericItemMerger(DefaultNaturalKeyImpl.of(ConnectorInstanceSpecificationType.F_NAME))),
                entry(
                        ResourceObjectTypeDefinitionType.class,
                        ObjectTypeDefinitionMerger::new),

                // for ResourceObjectTypeDefinitionType (object type definitions and embedded structures)
                entry(
                        ResourceItemDefinitionType.class,
                        () -> new GenericItemMerger(SingletonItemPathNaturalKeyImpl.of(ResourceAttributeDefinitionType.F_REF))),
                entry(
                        PropertyLimitationsType.class,
                        LimitationsMerger::new),
                entry(
                        MappingType.class,
                        () -> new GenericItemMerger(DefaultNaturalKeyImpl.of(MappingType.F_NAME))),
                entry(
                        AbstractCorrelatorType.class,
                        () -> new GenericItemMerger(DefaultNaturalKeyImpl.of(AbstractCorrelatorType.F_NAME))),
                entry(
                        CorrelationItemDefinitionType.class,
                        () -> new GenericItemMerger(DefaultNaturalKeyImpl.of(CorrelationItemDefinitionType.F_NAME))),
                entry(
                        CorrelationItemTargetDefinitionType.class,
                        () -> new GenericItemMerger(DefaultNaturalKeyImpl.of(CorrelationItemTargetDefinitionType.F_QUALIFIER))),
                entry(
                        SynchronizationReactionType.class,
                        () -> new GenericItemMerger(DefaultNaturalKeyImpl.of(SynchronizationReactionType.F_NAME))),
                entry(
                        AbstractSynchronizationActionType.class,
                        () -> new GenericItemMerger(DefaultNaturalKeyImpl.of(AbstractSynchronizationActionType.F_NAME)))
        );
    }
}
