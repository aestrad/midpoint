/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.authentication.impl.module.authentication;

import com.evolveum.midpoint.authentication.api.util.AuthenticationModuleNameConstants;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AuthenticationSequenceModuleNecessityType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AuthenticationSequenceModuleType;

/**
 * @author skublik
 */

public class SecurityQuestionFormModuleAuthentication extends CredentialModuleAuthenticationImpl {

    public SecurityQuestionFormModuleAuthentication(AuthenticationSequenceModuleType sequenceModule) {
        super(AuthenticationModuleNameConstants.SECURITY_QUESTIONS_FORM, sequenceModule);
    }

    public ModuleAuthenticationImpl clone() {
        SecurityQuestionFormModuleAuthentication module = new SecurityQuestionFormModuleAuthentication(this.getSequenceModule());
        module.setAuthentication(this.getAuthentication());
        super.clone(module);
        return module;
    }
}
