/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.delta.item;

import java.util.Collection;
import java.util.Objects;

import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.repo.sqale.ExtensionProcessor;
import com.evolveum.midpoint.repo.sqale.delta.ItemDeltaProcessor;
import com.evolveum.midpoint.repo.sqale.qmodel.ext.MExtItem;
import com.evolveum.midpoint.repo.sqale.qmodel.ext.MExtItemCardinality;
import com.evolveum.midpoint.repo.sqale.qmodel.ext.MExtItemHolderType;
import com.evolveum.midpoint.repo.sqale.update.ExtensionUpdateContext;
import com.evolveum.midpoint.repo.sqale.update.SqaleUpdateContext;
import com.evolveum.midpoint.repo.sqlbase.RepositoryException;
import com.evolveum.midpoint.util.exception.SchemaException;

public class ExtensionItemDeltaProcessor implements ItemDeltaProcessor {

    private final ExtensionUpdateContext<?, ?> context;
    private final MExtItemHolderType holderType;

    /**
     * Constructs delta processor for extension item inside JSONB column.
     * Takes more general context type for caller's sake, but it is {@link ExtensionUpdateContext}.
     */
    public ExtensionItemDeltaProcessor(
            SqaleUpdateContext<?, ?, ?> context, MExtItemHolderType holderType) {
        this.context = (ExtensionUpdateContext<?, ?>) context;
        this.holderType = holderType;
    }

    @Override
    public void process(ItemDelta<?, ?> modification) throws RepositoryException, SchemaException {
        ItemPath itemPath = modification.getPath();
        if (modification.getDefinition() instanceof PrismContainerDefinition<?>) {
            return; // We do not index containers
        }

        Item<PrismValue, ?> item = context.findValueOrItem(itemPath);
        Collection<?> realValues = item != null ? item.getRealValues() : null;
        ItemDefinition<?> definition = modification.getDefinition();

        Objects.requireNonNull(definition, "Item '" + itemPath + "' without definition can't be saved.");
        ExtensionProcessor extProcessor = new ExtensionProcessor(context.repositoryContext());
        ExtensionProcessor.ExtItemInfo extItemInfo =
                extProcessor.findExtensionItem(definition, holderType);
        if (extItemInfo == null) {
            return; // not-indexed, no action
        }

        // If the extension is single value (and we know it now, we should proceed with deletion of
        // multivalue variant and vice-versa (this variants may be indroduced during raw import
        // or changes in multiplicity of extension definition or resource definition
        context.deleteItem(reverseCardinality(extProcessor, extItemInfo.item));

        if (realValues == null || realValues.isEmpty()) {
            context.deleteItem(extItemInfo.getId());
            return;
        }

        // changed value
        context.setChangedItem(extItemInfo.getId(), extProcessor.extItemValue(item, extItemInfo));
    }

    private String reverseCardinality(ExtensionProcessor extProcessor, MExtItem extItem) {
        var reverseCardinality = MExtItemCardinality.SCALAR.equals(extItem.cardinality) ? MExtItemCardinality.ARRAY : MExtItemCardinality.SCALAR;
        var reverseKey = new MExtItem.Key();
        reverseKey.cardinality = reverseCardinality;
        reverseKey.itemName = extItem.itemName;
        reverseKey.valueType = extItem.valueType;
        reverseKey.holderType = extItem.holderType;
        // FIXME: Use search rather (do not create additional MExtItem)
        var reverseItem = context.repositoryContext().resolveExtensionItem(reverseKey);
        return String.valueOf(reverseItem.id);
    }
}
