/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.notifications.impl.helpers;

import org.springframework.stereotype.Component;

import com.evolveum.midpoint.notifications.api.NotificationManager;
import com.evolveum.midpoint.notifications.api.events.Event;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.BaseEventHandlerType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.EventHandlerType;

@Component
public class ChainHelper extends BaseNotificationHelper {

    private static final Trace LOGGER = TraceManager.getTrace(ChainHelper.class);

    public boolean processEvent(Event event, BaseEventHandlerType eventHandlerConfig,
            NotificationManager notificationManager, Task task, OperationResult result) {

        if (!(eventHandlerConfig instanceof EventHandlerType)) {
            return true;
        }

        EventHandlerType eventHandlerType = (EventHandlerType) eventHandlerConfig;
        if (eventHandlerType.getChained().isEmpty()) {
            return true;
        }

        logStart(LOGGER, event, eventHandlerType);

        boolean shouldContinue = true;
        for (EventHandlerType branchHandlerType : eventHandlerType.getChained()) {
            shouldContinue = notificationManager.processEvent(event, branchHandlerType, task, result);
            if (!shouldContinue) {
                break;
            }
        }

        logEnd(LOGGER, event, eventHandlerType, shouldContinue);
        return shouldContinue;
    }
}
