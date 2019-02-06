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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opennms.integration.api.v1.graph.Edge;
import org.opennms.integration.api.v1.graph.GraphContainer;
import org.opennms.integration.api.v1.graph.GraphContainerInfo;
import org.opennms.integration.api.v1.graph.GraphContainerProvider;
import org.opennms.integration.api.v1.graph.GraphRepository;
import org.opennms.integration.api.v1.graph.Vertex;
import org.opennms.integration.api.v1.graph.beans.ImmutableEdge;
import org.opennms.integration.api.v1.graph.beans.ImmutableGraph;
import org.opennms.integration.api.v1.graph.beans.ImmutableGraphContainer;
import org.opennms.integration.api.v1.graph.beans.ImmutableGraphInfo;
import org.opennms.integration.api.v1.graph.beans.ImmutableVertex;

public class OpendaylightGraphContainerProvider implements GraphContainerProvider {
    public static final String GRAPH_CONTAINER_ID = "ODL";

    private final OpendaylightRestconfClient client;
    private final GraphRepository graphRepository;

    public OpendaylightGraphContainerProvider(OpendaylightRestconfClient client, GraphRepository graphRepository) {
        this.client = Objects.requireNonNull(client);
        this.graphRepository = Objects.requireNonNull(graphRepository);
    }

    @Override
    public GraphContainer loadGraphContainer() {
        // Load the topology
        final Topology operationalTopology;
        try {
            operationalTopology = client.getOperationalTopology("flow:1");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        final Map<String,Vertex> verticesById = new LinkedHashMap<>();
        for (Node node : operationalTopology.getNode()) {
            final String nodeId = node.getNodeId().getValue();
            final Vertex vertex = ImmutableVertex.builder()
                    .id(nodeId)
                    .namespace("odl")
                    .build();
            verticesById.put(vertex.getId(), vertex);
        }

        final List<Edge> edges = new LinkedList<>();
        for (Link link : operationalTopology.getLink()) {
            final String sourceId = link.getSource().getSourceNode().getValue();
            final String targetId = link.getDestination().getDestNode().getValue();
            final Edge edge = ImmutableEdge.builder()
                    .id(sourceId + "->" + targetId)
                    .namespace("odl")
                    .source(verticesById.get(sourceId))
                    .target(verticesById.get(targetId))
                    .build();
            edges.add(edge);
        }

        ImmutableGraph graph = ImmutableGraph.builder()
                .namespace("odl")
                .vertices(new ArrayList<>(verticesById.values()))
                .edges(edges)
                .build();

        return ImmutableGraphContainer.builder()
                .id("odl")
                .label("Opendaylight")
                .graph(graph)
                .graphInfo(ImmutableGraphInfo.builder()
                        .namespace("odl")
                        .description("Opendaylight")
                        .label("Opendaylight")
                        .build())
                .build();
    }

    @Override
    public GraphContainerInfo getContainerInfo() {
        return loadGraphContainer();
    }
}
