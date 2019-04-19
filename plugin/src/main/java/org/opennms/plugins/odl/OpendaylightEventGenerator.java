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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opennms.integration.api.v1.dao.NodeDao;
import org.opennms.integration.api.v1.events.EventForwarder;
import org.opennms.integration.api.v1.model.InMemoryEvent;
import org.opennms.integration.api.v1.model.immutables.ImmutableInMemoryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.WebSocket;

public class OpendaylightEventGenerator implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(OpendaylightEventGenerator.class);

    private static final int REFRESH_PERIOD_MS = 30000;
    private static final int MIN_REFRESH_DELAY_MS = 5000;

    // TODO: Use cache instead - entries should eventually expire
    private final Map<Integer,Boolean> lastStatusByNodeId = new HashMap<>();

    private final OpendaylightRestconfClient client;
    private final EventForwarder eventForwarder;
    private final NodeDao nodeDao;
    private final OpendaylightServicePollerFactory opendaylightServicePollerFactory;

    private final AtomicBoolean exiting = new AtomicBoolean(false);
    private final Object triggerNextPoll = new Object();
    private Thread thread;

    private WebSocket webSocket;
    private Long lastRefreshMs;

    public OpendaylightEventGenerator(OpendaylightRestconfClient client, EventForwarder eventForwarder, NodeDao nodeDao, OpendaylightServicePollerFactory opendaylightServicePollerFactory) {
        this.client = Objects.requireNonNull(client);
        this.eventForwarder = Objects.requireNonNull(eventForwarder);
        this.nodeDao = Objects.requireNonNull(nodeDao);
        this.opendaylightServicePollerFactory = Objects.requireNonNull(opendaylightServicePollerFactory);
    }

    public void init() {
        try {
            this.webSocket = client.streamChangesForTopology("flow:1", notificationXml -> {
                synchronized (triggerNextPoll) {
                    triggerNextPoll.notifyAll();
                }
            });
        } catch (IOException e) {
            LOG.warn("Failed to stream changes for topology.", e);
        }

        thread = new Thread(this);
        thread.setName("ODL-EventGenerator");
        thread.start();
    }

    public void destroy() throws InterruptedException {
        if (webSocket != null) {
            webSocket.close(1001, "going away");
        }
        if (thread != null) {
            exiting.set(true);
            thread.interrupt();
            thread.join();
        }
    }

    @Override
    public void run() {
        while (!exiting.get()) {
            try {
                synchronized (triggerNextPoll) {
                    triggerNextPoll.wait(REFRESH_PERIOD_MS);
                }

                LOG.debug("Refreshing the operational topology.");
                for (org.opennms.integration.api.v1.model.Node node : nodeDao.getNodesInForeignSource("ODL")) {
                    try {
                        LOG.debug("Refreshing node state for: {}", node.getLabel());
                        refreshNodeState(node);
                    } catch (Exception e) {
                        LOG.error("Failed to refresh state for node: {}", node.getLabel(), e);
                    }
                }
            } catch (InterruptedException e) {
                LOG.info("Interrupted. Exiting.");
                return;
            }
        }
    }

    private void refreshNodeState(org.opennms.integration.api.v1.model.Node node) throws Exception {
        final OpendaylightServicePoller poller = opendaylightServicePollerFactory.createPoller();
        final Node topologyNode = poller.getNodeFromOperationalTopology(node);

        final boolean isOnline = topologyNode != null;
        final Boolean lastStatus = lastStatusByNodeId.get(node.getId());
        if (lastStatus == null || isOnline != lastStatus) {
            if (isOnline) {
                LOG.info("Sending online event for: {} with id: {}", node.getForeignId(), node.getId());
                sendOnlineEvent(node);
            } else {
                LOG.info("Sending offline event for: {} with id: {}", node.getForeignId(), node.getId());
                sendOfflineEvent(node);
            }
            lastStatusByNodeId.put(node.getId(), isOnline);
        } else {
            LOG.debug("Suppressing event for: {} status is the same: {}", node.getForeignId(), isOnline);
        }
    }

    private void sendOfflineEvent(org.opennms.integration.api.v1.model.Node node) {
        final InMemoryEvent event = ImmutableInMemoryEvent.newBuilder()
                .setUei(EventConstants.NODE_OFFLINE_UEI)
                .setSource(EventConstants.SOURCE)
                .setNodeId(node.getId())
                .build();
        eventForwarder.sendAsync(event);
    }

    private void sendOnlineEvent(org.opennms.integration.api.v1.model.Node node) {
        final InMemoryEvent event = ImmutableInMemoryEvent.newBuilder()
                .setUei(EventConstants.NODE_ONLINE_UEI)
                .setSource(EventConstants.SOURCE)
                .setNodeId(node.getId())
                .build();
        eventForwarder.sendAsync(event);
    }

}
