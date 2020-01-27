/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2020-2020 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2020 The OpenNMS Group, Inc.
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

import java.util.Objects;

import org.opennms.integration.api.v1.graph.Configuration;
import org.opennms.integration.api.v1.graph.Graph;
import org.opennms.integration.api.v1.graph.GraphContainer;
import org.opennms.integration.api.v1.graph.GraphContainerInfo;
import org.opennms.integration.api.v1.graph.GraphContainerProvider;
import org.opennms.integration.api.v1.graph.immutables.ImmutableGraphContainer;
import org.opennms.integration.api.v1.graph.immutables.ImmutableGraphContainerInfo;
import org.opennms.integration.api.v1.graph.immutables.ImmutableGraphInfo;

public class OpendaylightGraphContainerProvider implements GraphContainerProvider {

    private static final String CONTAINER_ID = "opendaylight";
    private static final String NAMESPACE = "flow:1";
    private static final String LABEL = "OpenDaylight";
    private static final String DESCRIPTION = "The Opendaylight Topology displays information provided by the opennms-opendaylight-plugin";
    private final OpendaylightGraphProvider delegate;

    public OpendaylightGraphContainerProvider(final OpendaylightGraphProvider delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public GraphContainer loadGraphContainer() {
        final Graph graph = delegate.loadGraph();
        final ImmutableGraphContainer graphContainer = ImmutableGraphContainer.builder(getGraphContainerInfo())
                .addGraph(graph)
                .build();
        return graphContainer;
    }

    @Override
    public GraphContainerInfo getGraphContainerInfo() {
        return new ImmutableGraphContainerInfo(CONTAINER_ID, LABEL, DESCRIPTION,
                new ImmutableGraphInfo(NAMESPACE, LABEL + " Topology", DESCRIPTION));
    }

    @Override
    public Configuration getConfiguration() {
        return delegate.getConfiguration();
    }
}
