/*
 * Copyright (c) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.authentication.api.util;

import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.security.api.MidPointPrincipal;
import com.evolveum.midpoint.authentication.api.StateOfModule;
import com.evolveum.midpoint.authentication.api.authentication.MidpointAuthentication;
import com.evolveum.midpoint.authentication.api.authentication.ModuleAuthentication;
import com.evolveum.midpoint.model.api.ModelInteractionService;
import com.evolveum.midpoint.model.api.authentication.GuiProfiledPrincipal;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.RegistrationsPolicyType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SelfRegistrationPolicyType;

import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class AuthUtil {

    private static final Trace LOGGER = TraceManager.getTrace(AuthUtil.class);

    private static final String DOT_CLASS = AuthUtil.class.getName() + ".";
    private static final String OPERATION_LOAD_FLOW_POLICY = DOT_CLASS + "loadFlowPolicy";

    public static GuiProfiledPrincipal getPrincipalUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        return getPrincipalUser(authentication);
    }

    public static GuiProfiledPrincipal getPrincipalUser(Authentication authentication) {
        if (authentication == null) {
            LOGGER.trace("Authentication not available in security context.");
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal == null) {
            return null;
        }
        if (principal instanceof GuiProfiledPrincipal) {
            return (GuiProfiledPrincipal) principal;
        }
        if (AuthorizationConstants.ANONYMOUS_USER_PRINCIPAL.equals(principal)) {
            // silently ignore to avoid filling the logs
            return null;
        }
        LOGGER.debug("Principal user in security context holder is {} ({}) but not type of {}",
                principal, principal.getClass(), GuiProfiledPrincipal.class.getName());
        return null;
    }

    public static boolean isPostAuthenticationEnabled(TaskManager taskManager, ModelInteractionService modelInteractionService) {
        MidPointPrincipal midpointPrincipal = getPrincipalUser();
        if (midpointPrincipal != null) {
            FocusType focus = midpointPrincipal.getFocus();
            Task task = taskManager.createTaskInstance(OPERATION_LOAD_FLOW_POLICY);
            OperationResult parentResult = new OperationResult(OPERATION_LOAD_FLOW_POLICY);
            RegistrationsPolicyType registrationPolicyType;
            try {
                registrationPolicyType = modelInteractionService.getFlowPolicy(focus.asPrismObject(), task, parentResult);
                if (registrationPolicyType == null) {
                    return false;
                }
                SelfRegistrationPolicyType postAuthenticationPolicy = registrationPolicyType.getPostAuthentication();
                if (postAuthenticationPolicy == null) {
                    return false;
                }
                String requiredLifecycleState = postAuthenticationPolicy.getRequiredLifecycleState();
                if (StringUtils.isNotBlank(requiredLifecycleState) && requiredLifecycleState.equals(focus.getLifecycleState())) {
                    return true;
                }
            } catch (CommonException e) {
                LoggingUtils.logException(LOGGER, "Cannot determine post authentication policies", e);
            }
        }
        return false;
    }

    public static ModuleAuthentication getAuthenticatedModule() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof MidpointAuthentication) {
            MidpointAuthentication mpAuthentication = (MidpointAuthentication) authentication;
            for (ModuleAuthentication moduleAuthentication : mpAuthentication.getAuthentications()) {
                if (StateOfModule.SUCCESSFULLY.equals(moduleAuthentication.getState())) {
                    return moduleAuthentication;
                }
            }
        } else {
            String message = "Unsupported type " + (authentication == null ? null : authentication.getClass().getName())
                    + " of authentication for MidpointLogoutRedirectFilter, supported is only MidpointAuthentication";
            throw new IllegalArgumentException(message);
        }
        return null;
    }

    public static ModuleAuthentication getProcessingModule(boolean required) {
        Authentication actualAuthentication = SecurityContextHolder.getContext().getAuthentication();

        if (actualAuthentication instanceof MidpointAuthentication) {
            MidpointAuthentication mpAuthentication = (MidpointAuthentication) actualAuthentication;
            ModuleAuthentication moduleAuthentication = mpAuthentication.getProcessingModuleAuthentication();
            if (required && moduleAuthentication == null) {
                LOGGER.error("Couldn't find processing module authentication {}", mpAuthentication);
                throw new AuthenticationServiceException("web.security.flexAuth.module.null");
            }
            return moduleAuthentication;
        } else if (required) {
            LOGGER.error("Type of actual authentication in security context isn't MidpointAuthentication");
            throw new AuthenticationServiceException("web.security.flexAuth.auth.wrong.type");
        }
        return null;
    }

    public static String stripEndingSlashes(String s) {
        if (StringUtils.isNotEmpty(s) && s.endsWith("/")) {
            if (s.equals("/")) {
                return "";
            }
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
    public static String stripStartingSlashes(String s) {
        if (StringUtils.isNotEmpty(s) && s.startsWith("/")) {
            if (s.equals("/")) {
                return "";
            }
            s = s.substring(1);
        }
        return s;
    }

    public static String stripSlashes(String s) {
        s = stripStartingSlashes(s);
        s = stripEndingSlashes(s);
        return s;
    }
}
