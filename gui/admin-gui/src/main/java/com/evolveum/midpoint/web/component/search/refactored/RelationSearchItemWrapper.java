/*
 * Copyright (c) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.component.search.refactored;

import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.util.DisplayableValue;
import com.evolveum.midpoint.web.component.search.SearchValue;

import org.apache.commons.collections4.CollectionUtils;

import javax.xml.namespace.QName;

public class RelationSearchItemWrapper extends AbstractRoleSearchItemWrapper {

    public RelationSearchItemWrapper(SearchConfigurationWrapper searchConfig) {
        super(searchConfig);
    }

    @Override
    public boolean isEnabled() {
        return CollectionUtils.isNotEmpty(getSearchConfig().getAvailableRelations());
    }

    public boolean isVisible() {
        return true;
    }

    @Override
    public Class<RelationSearchItemPanel> getSearchItemPanelClass() {
        return RelationSearchItemPanel.class;
    }

    @Override
    public DisplayableValue<QName> getDefaultValue() {
        return new SearchValue<>();
    }

    @Override
    public String getName() {
        return WebComponentUtil.getTranslatedPolyString(getSearchConfig().getConfig().getRelationConfiguration().getDisplay().getLabel());
    }

    @Override
    public String getHelp() {
        return WebComponentUtil.getTranslatedPolyString(getSearchConfig().getConfig().getRelationConfiguration().getDisplay().getHelp());
    }

    @Override
    public String getTitle() {
        return ""; //todo
    }

//    @Override
//            public boolean isApplyFilter() {
//                return !memberPanelStorage.isSearchScopeVisible()
//                        || !memberPanelStorage.isSearchScope(SearchBoxScopeType.SUBTREE);
//            }
}
