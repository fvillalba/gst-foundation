/*
 * Copyright 2009 FatWire Corporation. All Rights Reserved.
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

package com.fatwire.gst.foundation.core.service;

import COM.FutureTense.CS.Factory;
import COM.FutureTense.Interfaces.ICS;

/**
 * Base implementation of {@link ICSLocator}. Just one single ics is stored.
 * <p/>
 * <b>Not thread safe!!</b>
 *
 * @author Dolf Dijkstra
 */

public class ICSLocatorSupport implements ICSLocator {
    private final ICS ics;

    public ICSLocatorSupport() {
        try {
            this.ics = Factory.newCS();
        } catch (Exception e) {
            throw new IllegalStateException("Failure instantiating new CS. Check Content Server configuration", e);
        }
    }

    public ICSLocatorSupport(ICS ics) {
        super();
        this.ics = ics;
    }

    public ICS getICS() {
        return ics;
    }

}
