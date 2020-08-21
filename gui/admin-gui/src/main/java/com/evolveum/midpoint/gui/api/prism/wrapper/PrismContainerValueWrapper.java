/*
 * Copyright (c) 2010-2018 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.api.prism.wrapper;

import java.util.List;

import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.web.component.prism.ValueStatus;
import com.evolveum.midpoint.xml.ns._public.common.common_3.VirtualContainerItemSpecificationType;

/**
 * @author katka
 *
 */
public interface PrismContainerValueWrapper<C extends Containerable> extends PrismValueWrapper<C> {

    String getDisplayName();
    String getHelpText();

    boolean isExpanded();

    void setExpanded(boolean expanded);

    boolean isSorted();
    void setSorted(boolean sorted);

    List<PrismContainerDefinition<C>> getChildContainers() throws SchemaException;

    ValueStatus getStatus();
    void setStatus(ValueStatus status);

    <T extends Containerable> List<PrismContainerWrapper<T>> getContainers();

    List<ItemWrapper<?, ?>> getNonContainers();

    List<? extends ItemWrapper<?, ?>> getItems();

    <T extends Containerable> PrismContainerWrapper<T> findContainer(ItemPath path) throws SchemaException;
    <X> PrismPropertyWrapper<X> findProperty(ItemPath propertyPath) throws SchemaException;
    <R extends Referencable> PrismReferenceWrapper<R> findReference(ItemPath path) throws SchemaException;
    <IW extends ItemWrapper> IW findItem(ItemPath path, Class<IW> type) throws SchemaException;

    ItemPath getPath();

    boolean isSelected();
    boolean setSelected(boolean selected); //TODO why return boolean?

    boolean isReadOnly();
    void setReadOnly(boolean readOnly, boolean recursive);

    @Deprecated
    boolean hasChanged();

    boolean isShowEmpty();
    void setShowEmpty(boolean setShowEmpty);

    //void sort();

    <ID extends ItemDelta> void applyDelta(ID delta) throws SchemaException;
    PrismContainerValue<C> getValueToAdd() throws SchemaException;

    boolean isHeterogenous();
    void setHeterogenous(boolean heterogenous);

    void setVirtualContainerItems(List<VirtualContainerItemSpecificationType> virtualItems);
    boolean isVirtual();

    boolean isMetadata();
    void setMetadata(boolean metadata);

    PrismContainerDefinition<C> getDefinition();

    @Override
    PrismContainerValue<C> getNewValue();
}

