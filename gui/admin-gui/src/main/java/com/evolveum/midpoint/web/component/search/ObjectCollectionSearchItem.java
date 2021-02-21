/*
 * Copyright (C) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.component.search;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.model.api.authentication.CompiledObjectCollectionView;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DisplayableValue;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SearchItemType;

import com.evolveum.prism.xml.ns._public.query_3.SearchFilterType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;

import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * @author skublik
 */
public class ObjectCollectionSearchItem extends SearchItem {

    private static final long serialVersionUID = 1L;
    private static final Trace LOGGER = TraceManager.getTrace(ObjectCollectionSearchItem.class);

    private CompiledObjectCollectionView objectCollectionView;

    public ObjectCollectionSearchItem(Search search, @NotNull CompiledObjectCollectionView objectCollectionView) {
        super(search);
        Validate.notNull(objectCollectionView, "Collection must not be null.");
        this.objectCollectionView = objectCollectionView;
    }

    @Override
    public String getName() {
        if (objectCollectionView.getDisplay() != null) {
            if (objectCollectionView.getDisplay().getPluralLabel() != null) {
                return WebComponentUtil.getTranslatedPolyString(objectCollectionView.getDisplay().getPluralLabel());
            } else if (objectCollectionView.getDisplay().getSingularLabel() != null) {
                return WebComponentUtil.getTranslatedPolyString(objectCollectionView.getDisplay().getSingularLabel());
            } else if (objectCollectionView.getDisplay().getLabel() != null) {
                return WebComponentUtil.getTranslatedPolyString(objectCollectionView.getDisplay().getLabel());
            }
        }
        if (objectCollectionView.getCollection() != null) {
            ObjectReferenceType collectionRef = null;
            if (objectCollectionView.getCollection().getCollectionRef() != null) {
                collectionRef = objectCollectionView.getCollection().getCollectionRef();

            }
            if (objectCollectionView.getCollection().getBaseCollectionRef() != null
                    && objectCollectionView.getCollection().getBaseCollectionRef().getCollectionRef() != null) {
                collectionRef = objectCollectionView.getCollection().getBaseCollectionRef().getCollectionRef();
            }
            if (collectionRef != null) {
                PolyStringType label = objectCollectionView.getCollection().getCollectionRef().getTargetName();
                if (label == null) {
                    return objectCollectionView.getCollection().getCollectionRef().getOid();
                }
                return WebComponentUtil.getTranslatedPolyString(label);
            }
        }
        return null;
    }

    @Override
    public Type getSearchItemType() {
        return Type.OBJECT_COLLECTION;
    }

    @Override
    protected String getTitle(PageBase pageBase) {
        if (objectCollectionView.getFilter() == null) {
            return null;
        }
        try {
            SearchFilterType filter = pageBase.getQueryConverter().createSearchFilterType(objectCollectionView.getFilter());
            return pageBase.getPrismContext().xmlSerializer().serializeRealValue(filter);
        } catch (SchemaException e) {
            LoggingUtils.logUnexpectedException(LOGGER, "Cannot serialize filter", e);
        }
        return null;
    }

    @Override
    public String getHelp(PageBase pageBase) {
        if (objectCollectionView == null) {
            return null;
        }

        return objectCollectionView.getObjectCollectionDescription();
    }

    public CompiledObjectCollectionView getObjectCollectionView() {
        return objectCollectionView;
    }

    @Override
    public String toString() {
        return "ObjectCollectionSearchItem{" +
                "objectCollectionView=" + objectCollectionView +
                '}';
    }
}
