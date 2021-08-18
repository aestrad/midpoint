/*
 * Copyright (c) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.page.admin;

import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismObjectWrapper;
import com.evolveum.midpoint.gui.impl.prism.panel.SingleContainerPanel;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.application.PanelInstance;
import com.evolveum.midpoint.web.application.PanelType;
import com.evolveum.midpoint.web.application.PanelDisplay;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

@PanelType(name = "basic", defaultContainerPath = "empty")
@PanelInstance(identifier = "basic", applicableFor = AssignmentHolderType.class, defaultPanel = true, notApplicableFor = ResourceType.class)
@PanelDisplay(label = "Basic", icon = GuiStyleConstants.CLASS_CIRCLE_FULL, order = 10)
public class AssignmentHolderBasicPanel<AH extends AssignmentHolderType> extends AbstractObjectMainPanel<AH> {

    private static final String ID_MAIN_PANEL = "properties";
    private static final Trace LOGGER = TraceManager.getTrace(AssignmentHolderBasicPanel.class);
    private static final String ID_VIRTUAL_PANELS = "virtualPanels";

    public AssignmentHolderBasicPanel(String id, LoadableModel<PrismObjectWrapper<AH>> model, ContainerPanelConfigurationType config) {
        super(id, model, config);
    }

    @Override
    protected void initLayout() {
//        try {

//            ItemPanelSettingsBuilder builder = new ItemPanelSettingsBuilder().visibilityHandler(w -> ItemVisibility.AUTO);
//            builder.headerVisibility(false);
//
//            Panel main = getPageBase().initItemPanel(ID_MAIN_PANEL, getModelObject().getTypeName(),
//                    PrismContainerWrapperModel.fromContainerWrapper(getModel(), ItemPath.EMPTY_PATH), builder.build());
//            add(main);
//
//            RepeatingView view = new RepeatingView(ID_VIRTUAL_PANELS);
//            if (getPanelConfiguration() != null) {
//                List<VirtualContainersSpecificationType> virtualContainers = getPanelConfiguration().getContainer();
//                for (VirtualContainersSpecificationType virtualContainer : virtualContainers) {
//                    PrismContainerWrapperModel virtualContainerModel = PrismContainerWrapperModel.fromContainerWrapper(getModel(), virtualContainer.getIdentifier());
//                    Panel virtualPanel = new PrismContainerPanel<>(view.newChildId(), virtualContainerModel, builder.build());
//                    view.add(virtualPanel);
//                }
//
//            }
//            add(view);
            SingleContainerPanel panel = new SingleContainerPanel(ID_MAIN_PANEL, getModel(), getPanelConfiguration());
            add(panel);
//        } catch (SchemaException e) {
//            LOGGER.error("Could not create focus details panel. Reason: {}", e.getMessage(), e);
//        }
    }


}