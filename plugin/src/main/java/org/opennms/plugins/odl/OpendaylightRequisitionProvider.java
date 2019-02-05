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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Objects;

import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opennms.integration.api.v1.config.requisition.Requisition;
import org.opennms.integration.api.v1.config.requisition.beans.RequisitionBean;
import org.opennms.integration.api.v1.config.requisition.beans.RequisitionInterfaceBean;
import org.opennms.integration.api.v1.config.requisition.beans.RequisitionNodeBean;
import org.opennms.integration.api.v1.requisition.RequisitionProvider;
import org.opennms.integration.api.v1.requisition.RequisitionRepository;
import org.opennms.integration.api.v1.requisition.RequisitionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpendaylightRequisitionProvider implements RequisitionProvider {
    private static final Logger LOG = LoggerFactory.getLogger(OpendaylightRestconfClient.class);
    private static final String TYPE = "opendaylight";

    // TODO: Make this configurable
    public static final InetAddress NON_RESPONSIVE_IP_ADDRESS;
    static {
        try {
            // Addresses in the 192.0.2.0/24 block are reserved for documentation and should not respond to anything
            NON_RESPONSIVE_IP_ADDRESS = InetAddress.getByName("192.0.2.0");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    private final OpendaylightRestconfClient client;
    private final RequisitionRepository requisitionRepository;

    public OpendaylightRequisitionProvider(OpendaylightRestconfClient client, RequisitionRepository requisitionRepository) {
        this.client = Objects.requireNonNull(client);
        this.requisitionRepository = Objects.requireNonNull(requisitionRepository);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public OpendaylightRequisitionRequest getRequest(Map<String, String> parameters) {
        return new OpendaylightRequisitionRequest();
    }

    @Override
    public Requisition getRequisition(RequisitionRequest request) {
        final OpendaylightRequisitionRequest odlRequest = (OpendaylightRequisitionRequest)request;
        final NetworkTopology networkTopology;
        try {
            networkTopology = client.getOperationalNetworkTopology();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // TODO: Load existing requisition instead of starting from scracth?
        requisitionRepository.getDeployedRequisition("ODL");

        final RequisitionBean.Builder requisitionBuilder = RequisitionBean.builder()
                .foreignSource("ODL");

        for (Topology topology : networkTopology.getTopology()) {
            if (topology.getNode() == null) {
                // no nodes in this topology
                continue;
            }
            final String topologyId = topology.getTopologyId().getValue();
            for (Node node : topology.getNode()) {
                final String nodeId = node.getNodeId().getValue();

                // Colons are typically used a separators, so we replace them to be safe
                final String foreignId = nodeId.replaceAll(":", "_");

                requisitionBuilder.node(RequisitionNodeBean.builder()
                        .foreignId(foreignId)
                        .nodeLabel(nodeId)
                        .asset("building", topologyId)
                        .iface(RequisitionInterfaceBean.builder()
                                .ipAddress(NON_RESPONSIVE_IP_ADDRESS)
                                .monitoredService(OpendaylightPollerConfigExtension.SERVICE_NAME)
                                .build())
                        .build());
            }
        }

        return requisitionBuilder.build();
    }

    @Override
    public byte[] marshalRequest(RequisitionRequest request) {
        // TODO: Minion support
        return new byte[0];
    }

    @Override
    public RequisitionRequest unmarshalRequest(byte[] bytes) {
        // TODO: Minion support
        return new OpendaylightRequisitionRequest();
    }

}
