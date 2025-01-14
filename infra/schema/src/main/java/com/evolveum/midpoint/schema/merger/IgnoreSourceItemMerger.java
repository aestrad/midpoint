/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.schema.merger;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.SchemaException;

/**
 * Ignores the value in the source object.
 */
public class IgnoreSourceItemMerger implements ItemMerger {

    public static final IgnoreSourceItemMerger INSTANCE = new IgnoreSourceItemMerger();

    @Override
    public void merge(@NotNull PrismValue target, @NotNull PrismValue source) throws ConfigurationException, SchemaException {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public void merge(@NotNull ItemName itemName, @NotNull PrismContainerValue<?> target, @NotNull PrismContainerValue<?> source)
            throws ConfigurationException {
        // No op
    }
}
