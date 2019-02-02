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
import java.util.concurrent.CompletableFuture;

import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opennms.integration.api.v1.dao.NodeDao;
import org.opennms.integration.api.v1.pollers.PollerRequest;
import org.opennms.integration.api.v1.pollers.PollerResult;
import org.opennms.integration.api.v1.pollers.ServicePoller;
import org.opennms.integration.api.v1.pollers.Status;
import org.opennms.integration.api.v1.pollers.beans.PollerResultBean;

public class OpendaylightServicePoller implements ServicePoller {

    private final OpendaylightRestconfClient client;
    private final NodeDao nodeDao;

    public OpendaylightServicePoller(OpendaylightRestconfClient client, NodeDao nodeDao) {
        this.client = Objects.requireNonNull(client);
        this.nodeDao = Objects.requireNonNull(nodeDao);
    }

    @Override
    public CompletableFuture<PollerResult> poll(PollerRequest pollerRequest) {
        // Assume the poll is for a node on the current controller

        // Retrieve the node
        final org.opennms.integration.api.v1.model.Node node = nodeDao.getNodeById(pollerRequest.getNodeId());
        if (node == null) {
            return CompletableFuture.completedFuture(new PollerResultBean(Status.Down, "No matching node found!"));
        }

        // Determine the topology and (odl) node id
        // TODO: Where do we store these!?!?!?

        String foreignId = node.getForeignId();
        String odlTopologyId = "0"; //node.getAssetRecord().getBuilding();
        String odlNodeId = NamingUtils.getNodeIdFromForeignId(foreignId);

        try {
            Node odlNode = client.getNodeFromOperationalTopology(odlTopologyId, odlNodeId);
            if (odlNode == null) {
                return CompletableFuture.completedFuture(new PollerResultBean(Status.Down, "Node was not found in operational topology."));
            }
            return CompletableFuture.completedFuture(new PollerResultBean(Status.Up));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(new PollerResultBean(e));
        }
    }

}
