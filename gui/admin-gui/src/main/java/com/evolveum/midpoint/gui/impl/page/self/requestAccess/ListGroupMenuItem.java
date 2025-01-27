/*
 * Copyright (c) 2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.page.self.requestAccess;

import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Viliam Repan (lazyman).
 */
public class ListGroupMenuItem implements Serializable {

    private String iconCss;

    private String label;

    private String badgeCss;

    private String badge;

    private boolean active;

    private boolean disabled;

    private IModel<List<ListGroupMenuItem>> items = Model.ofList(new ArrayList<>());

    public ListGroupMenuItem() {
    }

    public ListGroupMenuItem(String label) {
        this(null, label);
    }

    public ListGroupMenuItem(String iconCss, String label) {
        this.iconCss = iconCss;
        this.label = label;
    }

    public String getIconCss() {
        return iconCss;
    }

    public void setIconCss(String iconCss) {
        this.iconCss = iconCss;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getBadgeCss() {
        return badgeCss;
    }

    public void setBadgeCss(String badgeCss) {
        this.badgeCss = badgeCss;
    }

    public String getBadge() {
        return badge;
    }

    public void setBadge(String badge) {
        this.badge = badge;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public List<ListGroupMenuItem> getItems() {
        return items.getObject();
    }

    public void setItems(List<ListGroupMenuItem> items) {
        this.items.setObject(items);
    }

    public IModel<List<ListGroupMenuItem>> getItemsModel() {
        return this.items;
    }

    public void setItemsModel(@NotNull IModel<List<ListGroupMenuItem>> items) {
        this.items = items;
    }
}
