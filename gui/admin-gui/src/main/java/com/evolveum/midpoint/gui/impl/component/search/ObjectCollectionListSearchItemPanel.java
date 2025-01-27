/*
 * Copyright (C) 2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.component.search;

import com.evolveum.midpoint.gui.api.util.WebComponentUtil;

import org.apache.wicket.Component;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;

public class ObjectCollectionListSearchItemPanel extends AbstractSearchItemPanel<ObjectCollectionListSearchItemWrapper> {

    public ObjectCollectionListSearchItemPanel(String id, IModel<ObjectCollectionListSearchItemWrapper> searchItemModel) {
        super(id, searchItemModel);
    }

    @Override
    protected Component initSearchItemField() {
        return WebComponentUtil.createDropDownChoices(
                ID_SEARCH_ITEM_FIELD, new PropertyModel(getModel(), "value"),
                Model.ofList(getModelObject().getViewNameList()), true, getPageBase());
    }
}
