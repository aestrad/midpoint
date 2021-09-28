/*
 * Copyright (C) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.gui.api.page.PageBase;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.assignment.AssignmentsUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

public class GuiDisplayNameUtil {

    private static final Trace LOGGER = TraceManager.getTrace(GuiDisplayNameUtil.class);

    public static <C extends Containerable> String getDisplayName(PrismContainerValue<C> prismContainerValue) {
        if (prismContainerValue == null) {
            return "ContainerPanel.containerProperties";
        }

        Method displayNameMethod = findDisplayNameMethod(prismContainerValue);
        if (displayNameMethod != null) {
            try {
                C containerable = prismContainerValue.asContainerable();
                String displayName = (String) displayNameMethod.invoke(null, containerable);
                return StringEscapeUtils.escapeHtml4(displayName);
            } catch (IllegalAccessException | InvocationTargetException e) {
                LOGGER.warn("Cannot invoge getDisplayName() method for {}, fallback to default displayName", prismContainerValue);
            }
        }

        String displayName = getDefaultDisplayName(prismContainerValue.getCompileTimeClass());
        return StringEscapeUtils.escapeHtml4(displayName);

    }

    private static <C extends Containerable> String getDefaultDisplayName(Class<C> cValClass) {
        if (cValClass != null) {
            return cValClass.getSimpleName() + ".details";
        }
        return "ContainerPanel.containerProperties";
    }
    private static <C extends Containerable> Method findDisplayNameMethod(PrismContainerValue<C> pcv) {
        for (Method method : GuiDisplayNameUtil.class.getMethods()) {
            if (method.getName().equals("getDisplayName")) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 1 && pcv.canRepresent(parameterTypes[0])) {
                    return method;
                }
            }
        }
        return null;
    }

    public static String getDisplayName(LifecycleStateType lifecycleStateType) {
        String name = lifecycleStateType.getDisplayName();
        if (name == null || name.isEmpty()) {
            name = lifecycleStateType.getName();
        }

        return name;
    }

    public static String getDisplayName(ItemConstraintType propertyConstraintType) {
        if (propertyConstraintType.getPath() != null) {
            return propertyConstraintType.getPath().getItemPath().toString();
        }
        return null;
    }

    public static String getDisplayName(AssignmentType assignmentType) {
        String displayName = AssignmentsUtil.getName(assignmentType, null);
        if (StringUtils.isBlank(displayName)) {
            displayName = "AssignmentTypeDetailsPanel.containerTitle";
        }
        return displayName;
    }

    public static String getDisplayName(ExclusionPolicyConstraintType exclusionConstraint) {
        String exclusionConstraintName = getExclusionConstraintName(exclusionConstraint);
        return exclusionConstraintNameExists(exclusionConstraintName, exclusionConstraint) ? exclusionConstraintName : "ExclusionPolicyConstraintType.details";
    }

    private static String getExclusionConstraintName(ExclusionPolicyConstraintType exclusionConstraint) {
        if (StringUtils.isNotBlank(exclusionConstraint.getName())) {
            return exclusionConstraint.getName();
        }

        return exclusionConstraint.asPrismContainerValue().getParent().getPath().last() + " - "
                + StringUtils.defaultIfEmpty(WebComponentUtil.getName(exclusionConstraint.getTargetRef()), "");
    }

    private static boolean exclusionConstraintNameExists(String name, ExclusionPolicyConstraintType exclusionConstraint) {
        return StringUtils.isNotEmpty(name) && StringUtils.isNotEmpty(WebComponentUtil.getName(exclusionConstraint.getTargetRef()));
    }

    public static String getDisplayName(AbstractPolicyConstraintType constraint) {
        String constraintName = constraint.getName();
        if (StringUtils.isNotEmpty(constraintName)) {
            return constraintName;
        }
        return constraint.asPrismContainerValue().getParent().getPath().last().toString() + ".details";
    }

    public static String getDisplayName(RichHyperlinkType richHyperlink) {
        String label = richHyperlink.getLabel();
        String description = richHyperlink.getDescription();
        String targetUrl = richHyperlink.getTargetUrl();
        if (StringUtils.isNotEmpty(label)) {
            return PageBase.createStringResourceStatic(null, label).getString()
                    + (StringUtils.isNotEmpty(description) ? (" - " + PageBase.createStringResourceStatic(null, description).getString()) : "");
        } else if (StringUtils.isNotEmpty(targetUrl)) {
            return targetUrl;
        }

        return null;
    }

    public static String getDisplayName(UserInterfaceFeatureType userInterfaceFeature) {
        String identifier = userInterfaceFeature.getIdentifier();

        if (StringUtils.isNotBlank(identifier)) {
            return identifier;
        }

        String displayName = null;

        DisplayType uifDisplay = userInterfaceFeature.getDisplay();
        if (uifDisplay != null) {
            displayName = WebComponentUtil.getOrigStringFromPoly(uifDisplay.getLabel());
        }

        if (displayName == null) {
            displayName = "UserInterfaceFeatureType.containerTitle";
        }
        return displayName;
    }

    public static String getDisplayName(GuiObjectColumnType guiObjectColumn) {
        return guiObjectColumn.getName();
    }

    public static String getDisplayName(GenericPcpAspectConfigurationType genericPcpAspectConfiguration) {
        return genericPcpAspectConfiguration.getName();
    }

    public static String getDisplayName(RelationDefinitionType relationDefinition) {
        QName relationQName = relationDefinition.getRef();
        if (relationDefinition == null) {
            return null;
        }

        String name = relationQName.getLocalPart();
        String description = relationDefinition.getDescription();
        if (StringUtils.isNotEmpty(name)) {
            return name + (StringUtils.isNotEmpty(description) ? (" - " + description) : "");
        }

        return description;
    }

    public static String getDisplayName(ResourceItemDefinitionType resourceItemDefinition) {
         if (resourceItemDefinition.getDisplayName() != null && !resourceItemDefinition.getDisplayName().isEmpty()) {
             return resourceItemDefinition.getDisplayName();
         }
         return resourceItemDefinition.asPrismContainerValue().getParent().getPath().last().toString();
    }

    public static String getDisplayName(MappingType mapping) {
        String mappingName = mapping.getName();
        if (StringUtils.isNotBlank(mappingName)) {
            String description = mapping.getDescription();
            return mappingName + (StringUtils.isNotEmpty(description) ? (" - " + description) : "");
        }
        List<VariableBindingDefinitionType> sources = mapping.getSource();
        String sourceDescription = "";
        if (CollectionUtils.isNotEmpty(sources)) {
            Iterator<VariableBindingDefinitionType> iterator = sources.iterator();
            while (iterator.hasNext()) {
                VariableBindingDefinitionType source = iterator.next();
                if (source == null || source.getPath() == null) {
                    continue;
                }
                String sourcePath = source.getPath().toString();
                sourceDescription += sourcePath;
                if (iterator.hasNext()) {
                    sourceDescription += ",";
                }
            }
        }
        VariableBindingDefinitionType target = mapping.getTarget();
        String targetDescription = target.getPath() != null ? target.getPath().toString() : null;
        if (StringUtils.isBlank(sourceDescription)) {
            sourceDescription = "(no sources)";
        }
        if (StringUtils.isBlank(targetDescription)) {
            targetDescription = "(no targets)";
        }
        return sourceDescription + " - " + targetDescription;
    }

    public static String getDisplayName(ProvenanceAcquisitionType acquisition) {
         return "ProvenanceAcquisitionType.details";
    }

    public static String getDisplayName(TaskErrorHandlingStrategyEntryType taskErrorHandlingStrategy) {
        return "Strategy (order " + (taskErrorHandlingStrategy.getOrder() == null ? " not defined)" : Integer.toString(taskErrorHandlingStrategy.getOrder()) + ")");
    }

    //TODO improve
    public static String getDisplayName(ActivityDefinitionType partitionDefinition) {
//        Integer index = partitionDefinition.getIndex();
//        WorkDistributionType workManagementType = partitionDefinition.getWorkManagement();
//
//        String string = "";
//        if (index != null) {
//            string += "Partition " + index;
//        }
//
//        // FIXME
//        if (workManagementType != null) {
//            TaskKindType taskKindType = workManagementType.getTaskKind();
//            if (taskKindType != null) {
//                string = appendKind(string, taskKindType);
//            }
//        }
//        return string;
        return "";
    }

    private static String appendKind(String string, TaskKindType taskKindType) {
        if (string.isBlank()) {
            return string += taskKindType;
        }

        return string += ", " + taskKindType;
    }
}
