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

import java.net.InetAddress;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.opennms.integration.api.v1.collectors.CollectionSet;
import org.opennms.integration.api.v1.collectors.CollectionSetPersistenceService;
import org.opennms.integration.api.v1.dao.NodeDao;
import org.opennms.integration.api.v1.model.Node;
import org.opennms.plugins.odl.OdlMetadata;
import org.opennms.plugins.odl.OpendaylightRequisitionProvider;
import org.opennms.plugins.odl.OpendaylightRestconfClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetricPusher {
    private static final Logger LOG = LoggerFactory.getLogger(MetricPusher.class);

    private final OpendaylightRestconfClient client;
    private final NodeDao nodeDao;
    private final CollectionSetPersistenceService collectionSetPersistenceService;
    private final MetricGenerator metricGenerator = new MetricGenerator();

    private Timer timer;


    public MetricPusher(OpendaylightRestconfClient client, NodeDao nodeDao, CollectionSetPersistenceService collectionSetPersistenceService) {
        this.client = Objects.requireNonNull(client);
        this.nodeDao = Objects.requireNonNull(nodeDao);
        this.collectionSetPersistenceService = Objects.requireNonNull(collectionSetPersistenceService);
    }

    public void init() {
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Thread.currentThread().setName("ODL-MetricPusher");
                try {
                    gatherAndPersistMetrics();
                } catch (Exception e) {
                    LOG.error("Oops", e);
                }
            }
        }, 0, TimeUnit.SECONDS.toMillis(15));
    }

    public void destroy() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    public void gatherAndPersistMetrics() {
        LOG.debug("Gathering and persisting metrics for all nodes in the {} requsition...", OpendaylightRequisitionProvider.DEFAULT_FOREIGN_SOURCE);
        final List<Node> onmsNodes = nodeDao.getNodesInForeignSource(OpendaylightRequisitionProvider.DEFAULT_FOREIGN_SOURCE);
        if (onmsNodes.isEmpty()) {
            LOG.debug("No nodes. Nothing to collect.");
            return;
        }

        LOG.debug("Found {} nodes.", onmsNodes.size());
        for (Node onmsNode : onmsNodes) {
            final OdlMetadata odlMetadata = new OdlMetadata(onmsNode);
            try {
                LOG.debug("Collecting metrics for: {}", onmsNode.getLabel());
                org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node node = client.getNodeFromOperationalInventory(odlMetadata.getNodeId());
                if (node == null) {
                    LOG.debug("Node with label: {} is not in the operational inventory. Skipping.", onmsNode.getLabel());
                    continue;
                }
                final CollectionSet collectionSet = metricGenerator.toCollectionSet(onmsNode, node);
                collectionSetPersistenceService.persist(onmsNode.getId(), getFirstInetAddress(onmsNode), collectionSet);
                LOG.debug("Successfully pushed collection set with {} resouces for node: {}", collectionSet.getCollectionSetResources().size(), onmsNode.getLabel());
            } catch (Exception e ){
                LOG.error("Failed to gather/persist metrics for: {}", onmsNode.getLabel(), e);
            }
        }
    }

    private InetAddress getFirstInetAddress(Node onmsNode) {
        return onmsNode.getIpInterfaces().get(0).getIpAddress();
    }

}
