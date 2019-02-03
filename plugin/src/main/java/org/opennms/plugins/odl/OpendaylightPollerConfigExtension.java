/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2019 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2019 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.plugins.odl;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.opennms.integration.api.v1.config.poller.Monitor;
import org.opennms.integration.api.v1.config.poller.Package;
import org.opennms.integration.api.v1.config.poller.PollerConfigExtension;
import org.opennms.integration.api.v1.config.poller.Service;

public class OpendaylightPollerConfigExtension implements PollerConfigExtension {

    // TODO: Make configurable
    public static String SERVICE_NAME = "IsAttachedToODLController";
    private static final long DEFAULT_SERVICE_INTERVAL_MS = 300000;

    @Override
    public List<Monitor> getMonitors() {
        return Collections.singletonList(new Monitor() {
            @Override
            public String getServiceName() {
                return SERVICE_NAME;
            }

            @Override
            public String getClassName() {
                return OpendaylightServicePoller.class.getCanonicalName();
            }
        });
    }

    @Override
    public List<Package> getPackages() {
        return Collections.singletonList(new Package() {

            @Override
            public String getName() {
                // TODO: Make configurable
                return "example1";
            }

            @Override
            public List<Service> getServices() {
                return Collections.singletonList(new Service() {

                    @Override
                    public String getName() {
                        return SERVICE_NAME;
                    }

                    @Override
                    public long getInterval() {
                        return DEFAULT_SERVICE_INTERVAL_MS;
                    }

                    @Override
                    public Map<String, String> getParameters() {
                        return Collections.emptyMap();
                    }
                });
            }
        });
    }
}
