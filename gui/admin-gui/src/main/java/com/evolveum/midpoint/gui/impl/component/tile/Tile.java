/*
 * Copyright (c) 2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.component.tile;

import java.io.Serializable;

/**
 * Created by Viliam Repan (lazyman).
 */
public class Tile<T extends Serializable> implements Serializable {

    private String icon;

    private String title;

    private boolean selected;

    private T value;

    public Tile() {
        this(null, null);
    }

    public Tile(String icon, String title) {
        this.icon = icon;
        this.title = title;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public void toggle() {
        setSelected(!isSelected());
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }
}
