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

import java.util.Objects;

import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

public class LinkProvider {

    private final OpendaylightRestconfClient client;

    public LinkProvider(OpendaylightRestconfClient client) {
        this.client = Objects.requireNonNull(client);
    }

    public void getLinks() {

        final Topology operationalTopology;
        try {
            operationalTopology = client.getOperationalTopology("flow:1");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        for (Node node : operationalTopology.getNode()) {
            final String nodeId = node.getNodeId().getValue();
        }

        for (Link link : operationalTopology.getLink()) {
            final String sourceId = link.getSource().getSourceNode().getValue();
            final String targetId = link.getDestination().getDestNode().getValue();
            final String label = sourceId + "->" + targetId;
        }
    }
}
