/*
 * Copyright (c) 2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.api.component.wizard;

import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.IModel;

import com.evolveum.midpoint.gui.api.component.BasePanel;

/**
 * Created by Viliam Repan (lazyman).
 */
public class WizardStepPanel extends BasePanel<String> {

    private static final long serialVersionUID = 1L;

    private static final String ID_CIRCLE = "circle";

    private static final String ID_LABEL = "label";

    private int index;

    public WizardStepPanel(String id, int index, IModel<String> model) {
        super(id, model);

        this.index = index;

        initLayout();
    }

    @Override
    protected void onComponentTag(ComponentTag tag) {
        checkComponentTag(tag, "div");

        super.onComponentTag(tag);
    }

    private void initLayout() {
        add(AttributeAppender.prepend("class", "step"));

        add(new Label(ID_CIRCLE, () -> index));
        add(new Label(ID_LABEL, () -> getModelObject()));
    }
}
