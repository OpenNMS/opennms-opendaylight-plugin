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
import org.opennms.integration.api.v1.collectors.resource.AttributeBuilder;
import org.opennms.integration.api.v1.collectors.resource.CollectionSetBuilder;
import org.opennms.integration.api.v1.collectors.resource.CollectionSetResourceBuilder;
import org.opennms.integration.api.v1.collectors.resource.IpInterfaceResource;
import org.opennms.integration.api.v1.collectors.resource.NodeResource;
import org.opennms.integration.api.v1.collectors.resource.NumericAttribute;
import org.opennms.integration.api.v1.collectors.resource.ResourceBuilder;
import org.opennms.integration.api.v1.collectors.resource.StringAttribute;

/**
 * Used to generate/extract metrics for a ODL operational inventory node
 * into an OpenNMS collection set.
 */
public class MetricGenerator {

    public CollectionSet toCollectionSet(org.opennms.integration.api.v1.model.Node onmsNode, Node odlNode) {
        final CollectionSetBuilder csetBuilder = new CollectionSetBuilder()
                .withStatus(CollectionSet.Status.SUCCEEDED);

        final NodeResource nodeResource = new ResourceBuilder()
                .withNodeId(onmsNode.getId())
                .buildNodeResource();

        // A node has many connectors, which operate like "interfaces" in OpenNMS
        for (NodeConnector nodeConnector : odlNode.getNodeConnector()) {
            // Unique ID
            final String nodeConnectorId = nodeConnector.getId().getValue();
            // Replace all non alpha-numeric characters
            final String sanitizedNodeConnectorId = nodeConnectorId.replaceAll("[^A-Za-z0-9]", "_");

            // Build an IP interface resource, using the nodeConnectorId as the instance id
            final IpInterfaceResource ipInterfaceResource = new ResourceBuilder()
                    .withInstance(sanitizedNodeConnectorId)
                    .buildIpInterfaceResource(nodeResource);
            final CollectionSetResourceBuilder<?> resourceBuilder = new CollectionSetResourceBuilder<>()
                    .withResource(ipInterfaceResource);

            // Store all attributes in the same group
            final String groupName = "opendaylight-port-statistics";

            final FlowCapableNodeConnector flowCapableNodeConnector = nodeConnector.getAugmentation(FlowCapableNodeConnector.class);
            // ifName
            resourceBuilder.withStringAttribute(string("ifName", groupName, flowCapableNodeConnector::getName));

            // ifHighSpeed - currentSpeed is in kbps
            resourceBuilder.withStringAttribute(string("ifHighSpeed", groupName, () -> Long.toString(flowCapableNodeConnector.getCurrentSpeed() / 1000)));

            final FlowCapableNodeConnectorStatisticsData flow = nodeConnector.getAugmentation(FlowCapableNodeConnectorStatisticsData.class);
            final FlowCapableNodeConnectorStatistics flowStats = flow.getFlowCapableNodeConnectorStatistics();

            // ifHCInOctets, ifHCOutOctets
            resourceBuilder.withNumericAttribute(counter("ifHCInOctets", groupName, () -> flowStats.getBytes().getReceived()));
            resourceBuilder.withNumericAttribute(counter("ifHCOutOctets", groupName, () -> flowStats.getBytes().getTransmitted()));

            // ifHCInUcastPkts, ifHCOutUcastPkts
            resourceBuilder.withNumericAttribute(counter("ifHCInUcastPkts", groupName, () -> flowStats.getPackets().getReceived()));
            resourceBuilder.withNumericAttribute(counter("ifHCOutUcastPkts", groupName, () -> flowStats.getPackets().getTransmitted()));

            // transmitDrops
            resourceBuilder.withNumericAttribute(counter("transmitDrops", groupName, flowStats::getTransmitDrops));

            // Add the resource to the collection set
            csetBuilder.withCollectionSetResource(resourceBuilder.build());
        }
        return csetBuilder.build();
    }

    private StringAttribute string(String name, String group, Supplier<String> value) {
        return new AttributeBuilder()
                .withName(name)
                .withGroup(group)
                .withStringValue(value.get())
                .buildString();
    }

    private NumericAttribute counter(String name, String group, Supplier<BigInteger> value) {
        return new AttributeBuilder()
                .withName(name)
                .withGroup(group)
                .withType(NumericAttribute.Type.COUNTER)
                .withNumericValue(value.get().doubleValue())
                .buildNumeric();
    }
}
