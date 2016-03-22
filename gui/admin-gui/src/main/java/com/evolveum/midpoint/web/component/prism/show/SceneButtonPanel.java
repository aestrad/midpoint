/*
 * Copyright (c) 2010-2016 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.web.component.prism.show;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;

/**
 * @author mserbak
 * @author lazyman
 */
public class SceneButtonPanel extends Panel {

    public static final String ID_MINIMIZE_BUTTON = "minimizeButton";
    public static final String ID_ICON = "icon";

    public SceneButtonPanel(String id, IModel<SceneDto> model) {
        super(id);

        initLayout(model);
    }

    private void initLayout(final IModel<SceneDto> model) {
        AjaxLink minimize = new AjaxLink(ID_MINIMIZE_BUTTON) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                minimizeOnClick(target);
            }
        };
        add(minimize);

        Label icon = new Label(ID_ICON);
        icon.add(AttributeModifier.append("class", new AbstractReadOnlyModel<String>() {

            @Override
            public String getObject() {
                SceneDto dto = model.getObject();
                if (dto.isMinimized()) {
                    return "fa fa-expand";
                }

                return "fa fa-compress";
            }
        }));
        minimize.add(icon);

        icon.add(new AttributeAppender("title", new AbstractReadOnlyModel() {

            @Override
            public String getObject() {
                SceneDto dto = model.getObject();
                if (dto.isMinimized()) {
                    return getString("prismOptionButtonPanel.maximize");
                }
                return getString("prismOptionButtonPanel.minimize");
            }
        }, ""));
    }

    public void minimizeOnClick(AjaxRequestTarget target) {
    }
}
