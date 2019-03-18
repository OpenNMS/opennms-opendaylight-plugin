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

import static org.opennms.plugins.odl.OpendaylightRequisitionProvider.DEFAULT_FOREIGN_SOURCE;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opennms.integration.api.v1.dao.NodeDao;
import org.opennms.integration.api.v1.events.EventListener;
import org.opennms.integration.api.v1.events.EventSubscriptionService;
import org.opennms.integration.api.v1.model.InMemoryEvent;
import org.opennms.integration.api.v1.model.Node;
import org.opennms.integration.api.v1.requisition.RequisitionRepository;
import org.opennms.integration.api.v1.topology.UserDefinedLink;
import org.opennms.integration.api.v1.topology.UserDefinedLinkDao;
import org.opennms.integration.api.v1.topology.beans.UserDefinedLinkBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

public class OpendaylightTopologyHandler implements EventListener {
    private static final Logger LOG = LoggerFactory.getLogger(OpendaylightTopologyHandler.class);

    public static final String IMPORT_SUCCESSFUL_UEI = "uei.opennms.org/internal/importer/importSuccessful";
    public static final String PARM_FOREIGN_SOURCE = "foreignSource";

    private final OpendaylightRestconfClient client;
    private final RequisitionRepository requisitionRepository;
    private final EventSubscriptionService eventSubscriptionService;
    private final NodeDao nodeDao;
    private final UserDefinedLinkDao userDefinedLinkDao;

    public OpendaylightTopologyHandler(OpendaylightRestconfClient client,
                                       RequisitionRepository requisitionRepository,
                                       EventSubscriptionService eventSubscriptionService,
                                       NodeDao nodeDao,
                                       UserDefinedLinkDao userDefinedLinkDao) {
        this.client = Objects.requireNonNull(client);
        this.requisitionRepository = Objects.requireNonNull(requisitionRepository);
        this.eventSubscriptionService = Objects.requireNonNull(eventSubscriptionService);
        this.nodeDao = Objects.requireNonNull(nodeDao);
        this.userDefinedLinkDao = Objects.requireNonNull(userDefinedLinkDao);
    }

    @Override
    public String getName() {
        return OpendaylightTopologyHandler.class.getName();
    }

    @Override
    public int getNumThreads() {
        return 1;
    }

    @Override
    public void onEvent(InMemoryEvent event) {
        if (event == null || !IMPORT_SUCCESSFUL_UEI.equals(event.getUei())) {
            return;
        }

        // Extract the name of the referenced FS
        final String foreignSource = event.getParameterValue(PARM_FOREIGN_SOURCE).orElse(null);
        if (foreignSource == null) {
            LOG.warn("No foreign source parameter found on import successful event. Ignoring.");
            return;
        }


        if (!DEFAULT_FOREIGN_SOURCE.equals(foreignSource)) {
            LOG.debug("Ignoring foreign source: {}", foreignSource);
            return;
        }

        /*
        // TODO: Quickly determine whether or not we need to load the requisition - it may be large and unrelated

        // Retrieve a copy of the requisition in question
        final Requisition requisition = requisitionRepository.getDeployedRequisition(foreignSource);
        requisition.getNodes();

        // TODO: Figure out which controller
        */

        // Load the topology
        Topology operationalTopology;
        try {
            operationalTopology = client.getOperationalTopology("flow:1");
        } catch (Exception e) {
            LOG.error("Failed to load operational topology.", e);
            return;
        }

        // Index the links by source id
        final Map<String, List<Link>> linksBySourceId = operationalTopology.getLink().stream()
                .collect(Collectors.groupingBy(l -> l.getSource().getSourceNode().getValue()));


        // Build a "ODL Node ID" to "OpenNMS Node" map
        final List<Node> nodes = nodeDao.getNodesInForeignSource(foreignSource);
        final Map<String,Node> nodesByLabel = nodes.stream()
                .collect(Collectors.toMap(Node::getLabel, n -> n));

        for (Node node : nodes) {
            List<Link> expectedLinks = linksBySourceId.get(node.getLabel());
            pushLinkUpdates(node, expectedLinks, nodesByLabel);
        }
    }

    private void pushLinkUpdates(Node node, List<Link> expectedLinks, Map<String,Node> nodesByLabel) {
        final Map<String, UserDefinedLink> existingLinksById = userDefinedLinkDao.getOutLinks(node.getId()).stream()
                .filter(udl -> Objects.equals(EventConstants.SOURCE, udl.getOwner()))
                .collect(Collectors.toMap(UserDefinedLink::getLinkId, l -> l));

        // Add any missing links
        for (Link expectedLink : expectedLinks) {
            final String linkId = expectedLink.getKey().getLinkId().getValue();
            UserDefinedLink udl = existingLinksById.get(linkId);
            if (udl != null) {
                // Link already exists
                continue;
            }

            final String targetId = expectedLink.getDestination().getDestNode().getValue();
            final Node targetNode = nodesByLabel.get(targetId);
            if (targetNode == null) {
                LOG.warn("Link refers to node which was not found!: {}", expectedLink);
                continue;
            }

            udl = UserDefinedLinkBean.builder()
                    .nodeIdA(node.getId())
                    .componentLabelA(expectedLink.getSource().getSourceTp().getValue())
                    .nodeIdZ(targetNode.getId())
                    .componentLabelZ(expectedLink.getDestination().getDestTp().getValue())
                    .linkId(linkId)
                    .linkLabel("Opendaylight Topology")
                    .owner(EventConstants.SOURCE)
                    .build();
            LOG.debug("Inserting link: {}", udl);
            userDefinedLinkDao.saveOrUpdate(udl);
        }

        // Delete any extraneous links
        final Set<String> expectedLinkIds = expectedLinks.stream()
                .map(l -> l.getKey().getLinkId().getValue())
                .collect(Collectors.toSet());

        // Links with these ids are present, but are not expected
        final Set<String> linkIdsToDelete = Sets.difference(existingLinksById.keySet(), expectedLinkIds);
        for (String linkIdToDelete : linkIdsToDelete) {
            final UserDefinedLink udl = existingLinksById.get(linkIdToDelete);
            LOG.debug("Deleting link: {}", udl);
            userDefinedLinkDao.delete(udl);
        }
    }

    public void init() {
        eventSubscriptionService.addEventListener(this, IMPORT_SUCCESSFUL_UEI);
    }

    public void destroy() {
        eventSubscriptionService.removeEventListener(this, IMPORT_SUCCESSFUL_UEI);
    }

}
