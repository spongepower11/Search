/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.common.logging;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.PatternConverter;
import org.elasticsearch.action.support.user.ActionUser;
import org.elasticsearch.common.Strings;

@Plugin(category = PatternConverter.CATEGORY, name = "ActionUserRolesConverter")
@ConverterKeys({ "action_user_roles" })
public final class ActionUserRolesConverter extends ActionUserConverter {

    /**
     * Called by log4j2 to initialize this converter.
     */
    public static ActionUserRolesConverter newInstance(@SuppressWarnings("unused") final String[] options) {
        return new ActionUserRolesConverter();
    }

    public ActionUserRolesConverter() {
        super("action_user_roles");
    }

    @Override
    protected void doFormat(ActionUser user, LogEvent event, StringBuilder toAppendTo) {
        final String[] roles = (String[]) user.identifier().asMap().get("roles");
        if (roles != null) {
            toAppendTo.append(Strings.arrayToCommaDelimitedString(roles));
        }
    }
}
