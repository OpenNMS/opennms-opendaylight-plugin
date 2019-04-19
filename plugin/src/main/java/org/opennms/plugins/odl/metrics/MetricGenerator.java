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

package org.opennms.plugins.odl.metrics;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.FlowCapableNodeConnectorStatisticsData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.flow.capable.node.connector.statistics.FlowCapableNodeConnectorStatistics;
import org.opennms.integration.api.v1.collectors.CollectionSet;
import org.opennms.integration.api.v1.collectors.immutables.ImmutableNumericAttribute;
import org.opennms.integration.api.v1.collectors.immutables.ImmutableStringAttribute;
import org.opennms.integration.api.v1.collectors.resource.IpInterfaceResource;
import org.opennms.integration.api.v1.collectors.resource.NodeResource;
import org.opennms.integration.api.v1.collectors.resource.NumericAttribute;
import org.opennms.integration.api.v1.collectors.resource.StringAttribute;
import org.opennms.integration.api.v1.collectors.resource.immutables.ImmutableCollectionSet;
import org.opennms.integration.api.v1.collectors.resource.immutables.ImmutableCollectionSetResource;
import org.opennms.integration.api.v1.collectors.resource.immutables.ImmutableIpInterfaceResource;
import org.opennms.integration.api.v1.collectors.resource.immutables.ImmutableNodeResource;

/**
 * Used to generate/extract metrics for a ODL operational inventory node
 * into an OpenNMS collection set.
 */
public class MetricGenerator {

    public CollectionSet toCollectionSet(org.opennms.integration.api.v1.model.Node onmsNode, Node odlNode) {
        final ImmutableCollectionSet.Builder csetBuilder = ImmutableCollectionSet.newBuilder()
                .setStatus(CollectionSet.Status.SUCCEEDED);

        final NodeResource nodeResource = ImmutableNodeResource.newBuilder()
                .setNodeId(onmsNode.getId())
                .build();

        // A node has many connectors, which operate like "interfaces" in OpenNMS
        for (NodeConnector nodeConnector : odlNode.getNodeConnector()) {
            // Unique ID
            final String nodeConnectorId = nodeConnector.getId().getValue();
            // Replace all non alpha-numeric characters
            final String sanitizedNodeConnectorId = nodeConnectorId.replaceAll("[^A-Za-z0-9]", "_");

            // Build an IP interface resource, using the nodeConnectorId as the instance id
            final IpInterfaceResource ipInterfaceResource = ImmutableIpInterfaceResource.newInstance(nodeResource, sanitizedNodeConnectorId);
            final ImmutableCollectionSetResource.Builder<?> resourceBuilder = ImmutableCollectionSetResource.newBuilder(IpInterfaceResource.class)
                    .setResource(ipInterfaceResource);

            // Store all attributes in the same group
            final String groupName = "opendaylight-port-statistics";

            final FlowCapableNodeConnector flowCapableNodeConnector = nodeConnector.getAugmentation(FlowCapableNodeConnector.class);
            // ifName
            resourceBuilder.addStringAttribute(string("ifName", groupName, flowCapableNodeConnector::getName));

            // ifHighSpeed - currentSpeed is in kbps
            resourceBuilder.addStringAttribute(string("ifHighSpeed", groupName, () -> Long.toString(flowCapableNodeConnector.getCurrentSpeed() / 1000)));

            final FlowCapableNodeConnectorStatisticsData flow = nodeConnector.getAugmentation(FlowCapableNodeConnectorStatisticsData.class);
            if (flow != null) {
                final FlowCapableNodeConnectorStatistics flowStats = flow.getFlowCapableNodeConnectorStatistics();

                // ifHCInOctets, ifHCOutOctets
                resourceBuilder.addNumericAttribute(counter("ifHCInOctets", groupName, () -> flowStats.getBytes().getReceived()));
                resourceBuilder.addNumericAttribute(counter("ifHCOutOctets", groupName, () -> flowStats.getBytes().getTransmitted()));

                // ifHCInUcastPkts, ifHCOutUcastPkts
                resourceBuilder.addNumericAttribute(counter("ifHCInUcastPkts", groupName, () -> flowStats.getPackets().getReceived()));
                resourceBuilder.addNumericAttribute(counter("ifHCOutUcastPkts", groupName, () -> flowStats.getPackets().getTransmitted()));

                // transmitDrops
                resourceBuilder.addNumericAttribute(counter("transmitDrops", groupName, flowStats::getTransmitDrops));

                // Add the resource to the collection set
                csetBuilder.addCollectionSetResource(resourceBuilder.build());
            }
        }
        return csetBuilder.build();
    }

    private StringAttribute string(String name, String group, Supplier<String> value) {
        return ImmutableStringAttribute.newBuilder()
                .setName(name)
                .setGroup(group)
                .setValue(value.get())
                .build();
    }

    private NumericAttribute counter(String name, String group, Supplier<BigInteger> value) {
        return ImmutableNumericAttribute.newBuilder()
                .setName(name)
                .setGroup(group)
                .setType(NumericAttribute.Type.COUNTER)
                .setValue(value.get().doubleValue())
                .build();
    }
}
