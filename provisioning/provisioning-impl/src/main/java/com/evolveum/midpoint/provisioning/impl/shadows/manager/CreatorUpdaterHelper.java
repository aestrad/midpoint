/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.provisioning.impl.shadows.manager;

import java.util.Collection;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.common.Clock;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ItemDeltaCollectionsUtil;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.provisioning.impl.ProvisioningContext;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.annotation.Experimental;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.MetadataType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceConsistencyType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;

/**
 * Auxiliary code used for both shadow creator or updater.
 *
 * TODO better name
 */
@Component
@Experimental
public class CreatorUpdaterHelper {

    private static final Trace LOGGER = TraceManager.getTrace(ShadowUpdater.class);

    @Autowired private Clock clock;

    // Just minimal metadata for now, maybe we need to expand that later
    // those are needed to properly manage dead shadows
    void addCreateMetadata(PrismObject<ShadowType> repoShadow) {
        ShadowType repoShadowType = repoShadow.asObjectable();
        MetadataType metadata = repoShadowType.getMetadata();
        if (metadata != null) {
            return;
        }
        metadata = new MetadataType();
        repoShadowType.setMetadata(metadata);
        metadata.setCreateTimestamp(clock.currentTimeXMLGregorianCalendar());
    }

    // Just minimal metadata for now, maybe we need to expand that later
    // those are needed to properly manage dead shadows
    void addModifyMetadataDeltas(PrismObject<ShadowType> repoShadow, Collection<ItemDelta<?, ?>> shadowChanges) {
        PropertyDelta<XMLGregorianCalendar> modifyTimestampDelta = ItemDeltaCollectionsUtil
                .findPropertyDelta(shadowChanges, SchemaConstants.PATH_METADATA_MODIFY_TIMESTAMP);
        if (modifyTimestampDelta != null) {
            return;
        }
        LOGGER.debug("Metadata not found, adding minimal metadata. Modifications:\n{}", DebugUtil.debugDumpLazily(shadowChanges, 1));
        PrismPropertyDefinition<XMLGregorianCalendar> def = repoShadow.getDefinition().findPropertyDefinition(SchemaConstants.PATH_METADATA_MODIFY_TIMESTAMP);
        modifyTimestampDelta = def.createEmptyDelta(SchemaConstants.PATH_METADATA_MODIFY_TIMESTAMP);
        modifyTimestampDelta.setRealValuesToReplace(clock.currentTimeXMLGregorianCalendar());
        shadowChanges.add(modifyTimestampDelta);
    }

    boolean isUseProposedShadows(ProvisioningContext ctx) throws ObjectNotFoundException, SchemaException, CommunicationException,
            ConfigurationException, ExpressionEvaluationException {
        ResourceConsistencyType consistency = ctx.getResource().getConsistency();
        return consistency != null && BooleanUtils.isTrue(consistency.isUseProposedShadows());
    }
}
