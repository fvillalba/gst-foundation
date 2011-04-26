/*
 * Copyright 2011 FatWire Corporation. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fatwire.gst.foundation.controller.action;

import javax.servlet.ServletContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

/**
 * @author Dolf Dijkstra
 * @since Apr 11, 2011
 */
public class ActionLocatorUtils {
    private static final Log LOG = LogFactory.getLog(ActionLocatorUtils.class.getPackage().getName());
    public static final String ACTION_LOCATOR_BEAN = "gsfActionLocator";

    /**
     * 
     */
    private ActionLocatorUtils() {
    }

    /**
     * Returns the ActionLocator as configured by spring framework on the
     * WebApplicationContext bean by the name of gsfActionLocator.
     * 
     * @param servletContext
     * @return the ActionLocator is confifured via the servletContext
     */
    public static ActionLocator getActionLocator(final ServletContext servletContext) {

        // get the spring web application context
        final WebApplicationContext wac = WebApplicationContextUtils.getRequiredWebApplicationContext(servletContext);

        // get the bean. Note for lazy administrators, a default locator is
        // provided

        final ActionLocator locator;
        if (wac.containsBean(ACTION_LOCATOR_BEAN)) {
            locator = (ActionLocator) wac.getBean(ACTION_LOCATOR_BEAN);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Using actionLocatorBean as configured: " + locator.getClass().getName());
            }
        } else {
            locator = new CommandActionLocator();
            if (LOG.isTraceEnabled()) {
                LOG.trace("Using default actionLocatorBean " + locator.getClass().getName());
            }
        }
        return locator;
    }

}