package org.opennms.plugins.odl;

import java.util.Objects;

import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opennms.integration.api.v1.graph.Edge;
import org.opennms.integration.api.v1.graph.Graph;
import org.opennms.integration.api.v1.graph.GraphInfo;
import org.opennms.integration.api.v1.graph.GraphProvider;
import org.opennms.integration.api.v1.graph.Vertex;
import org.opennms.integration.api.v1.graph.immutables.ImmutableEdge;
import org.opennms.integration.api.v1.graph.immutables.ImmutableGraph;
import org.opennms.integration.api.v1.graph.immutables.ImmutableGraphInfo;
import org.opennms.integration.api.v1.graph.immutables.ImmutableVertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class OpendaylightGraphProvider implements GraphProvider {

    private static final Logger LOG = LoggerFactory.getLogger(OpendaylightTopologyHandler.class);

    private static final String NAMESPACE = "flow:1";
    private static final String LABEL = "OpenDaylight Topology";
    private static final String DESCRIPTION = "The Opendaylight Topology displays information provided by the opennms-opendaylight-plugin";

    private final OpendaylightRestconfClient client;

    public OpendaylightGraphProvider(final OpendaylightRestconfClient client) {
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public Graph loadGraph() {
        final Topology topology = getOperationalTopology();
        final ImmutableGraph.ImmutableGraphBuilder graphBuilder = ImmutableGraph.builder(getGraphInfo());
        topology.getNode().forEach(n -> {
            final String nodeId = n.getNodeId().getValue();;
            final String foreignId = nodeId.replaceAll(":", "_"); // Should match foreignId from requisition to have it enrich later with NodeInfo data
            final ImmutableVertex.ImmutableVertexBuilder vertexBuilder = ImmutableVertex.builder();
            final Vertex vertex = vertexBuilder.namespace(graphBuilder.getNamespace())
                    .id(n.getNodeId().getValue())
                    .nodeRef(OpendaylightRequisitionProvider.DEFAULT_FOREIGN_SOURCE, foreignId)
                    .property("latitude", "45.340561") // Example custom property latitude
                    .property("longitude", "-75.910005") // Example custom property longitude
                    .build();
            graphBuilder.addVertex(vertex);
        });

        topology.getLink().forEach(link -> {
            final String linkId = link.getLinkId().getValue();
            final String sourceNodeId = link.getSource().getSourceNode().getValue();
            final String targetNodeId = link.getDestination().getDestNode().getValue();
            final Edge edge = ImmutableEdge.builder()
                    .namespace(graphBuilder.getNamespace())
                    .id(linkId)
                    .source(graphBuilder.getNamespace(), sourceNodeId)
                    .target(graphBuilder.getNamespace(), targetNodeId)
                    .build();
            graphBuilder.addEdge(edge);
        });

        final ImmutableGraph graph = graphBuilder.build();
        return graph;
    }

    @Override
    public GraphInfo getGraphInfo() {
        return new ImmutableGraphInfo(NAMESPACE, LABEL, DESCRIPTION);
    }

    public boolean isTopology() {
        return true;
    }

    private Topology getOperationalTopology() {
        try {
            return client.getOperationalTopology(NAMESPACE);
        } catch (Exception e) {
            LOG.error("Failed to load operational topology.", e);
            throw new RuntimeException(e);
        }
    }
}
