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

import java.util.Map;

import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpendaylightRequisitionProvider {
    private static final Logger LOG = LoggerFactory.getLogger(OpendaylightRestconfClient.class);

    public OpendaylightRequisitionRequest getRequest(Map<String, String> parameters) {
        String foreignSource =  null; //ParameterMap.getKeyedString(parameters, "foreign-source", "odl");
        String host = null; // ParameterMap.getKeyedString(parameters, "host", "127.0.0.1");
        int port = 8181; // ParameterMap.getKeyedInteger(parameters, "port", OpendaylightRestconfClient.DEFAULT_PORT);

        OpendaylightRequisitionRequest request = new OpendaylightRequisitionRequest();
        request.setForeignSource(foreignSource);
        request.setHost(host);
        request.setPort(port);
        return request;
    }

    /*
    private Requisition getExistingRequisition(OpendaylightRequisitionRequest request) {
        try {
            if (m_foreignSourceRepository == null) {
                m_foreignSourceRepository = BeanUtils.getBean("daoContext", "deployedForeignSourceRepository", ForeignSourceRepository.class);
            }
            return m_foreignSourceRepository.getRequisition(request.getForeignSource());
        } catch (Exception e) {
            LOG.warn("Can't retrieve requisition. {}. Assuming there was no previous requisition.", request.getForeignSource());
            return null;
        }
    }
    */

    public void getRequisitionFor(OpendaylightRequisitionRequest request) {
        LOG.debug("Retrieving existing requisition.");
       /* Requisition requisition = getExistingRequisition(request);
        if (requisition == null) {
            LOG.info("No existing requisition was found. Creating a new requisition.");
            requisition = new Requisition();
            requisition.setForeignSource(request.getForeignSource());
        }*/

        final OpendaylightRestconfClient odlClient = new OpendaylightRestconfClient(request.getHost());
        NetworkTopology networkTopology;
        try {
            networkTopology = odlClient.getOperationalNetworkTopology();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (Topology topology : networkTopology.getTopology()) {
            final String topologyId = topology.getTopologyId().getValue();
            for (Node node : topology.getNode()) {
                final String nodeId = node.getNodeId().getValue();

                // TODO: What if the string already contains an underscore or colon dash
                // Colons are typically used a separators, so we replace them to be safe
                final String foreignId = nodeId.replaceAll(":", "_");

                // Parse the topology info we need to persist
      //          Links links = getLinksSourceFrom(topology, node);

                /*
                RequisitionAsset requisitionAssetTopologyInfo = new RequisitionAsset("vmwareTopologyInfo", JaxbUtils.marshal(links));
                RequisitionNode requisitionNode = requisition.getNode(foreignId);
                if (requisitionNode != null) {
                    // There already a node in the requisition for this fid
                    // Update the topology info
                    requisitionNode.putAsset(requisitionAssetTopologyInfo);
                    continue;
                }

                requisitionNode = new RequisitionNode();
                requisitionNode.putAsset(requisitionAssetTopologyInfo);
                requisitionNode.putAsset(new RequisitionAsset("building", topologyId));

                requisitionNode.setForeignId(foreignId);
                requisitionNode.setNodeLabel(nodeId);

                RequisitionInterface requisitionIf = new RequisitionInterface();
                try {
                    requisitionIf.setIpAddr(NamingUtils.generateIpAddressForForeignId(foreignId));
                } catch (Throwable t) {
                    requisitionIf.setIpAddr("127.0.0.1");
                }

                RequisitionMonitoredService requisitionSvc = new RequisitionMonitoredService();
                requisitionSvc.setServiceName("IsAttachedToODLController");

                requisitionIf.getMonitoredServices().add(requisitionSvc);
                requisitionNode.getInterfaces().add(requisitionIf);
                requisition.getNodes().add(requisitionNode);
                */
            }
        }

       // return requisition;
    }

    /*
    public static Links getLinksSourceFrom(Topology topology, Node node) {
        return new Links(topology.getLink().stream()
                .filter(l -> node.getNodeId().equals(l.getSource().getSourceNode()))
                .map(l -> new Link(l))
                .collect(Collectors.toList()));
    }
    */
}
