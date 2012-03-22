/*
 * Copyright (c) 2012 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 *
 * Portions Copyrighted 2012 [name of copyright owner]
 */

package com.evolveum.midpoint.repo.sql.query;

import org.apache.commons.lang.StringUtils;

import javax.xml.namespace.QName;

/**
 * @author lazyman
 */
public abstract class Definition {

    private QName name;
    private QName type;

    private String realName;

    public QName getName() {
        return name;
    }

    public void setName(QName name) {
        this.name = name;
    }

    public QName getType() {
        return type;
    }

    public void setType(QName type) {
        this.type = type;
    }

    public String getRealName() {
        if (StringUtils.isEmpty(realName)) {
            return name.getLocalPart();
        }
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public abstract Definition findDefinition(QName qname);

    public abstract <T extends Definition> T findDefinition(QName qname, Class<T> type);

    public abstract boolean isEntity();
}
