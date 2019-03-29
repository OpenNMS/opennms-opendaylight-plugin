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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.nio.file.Paths;

import org.junit.Rule;
import org.junit.Test;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opennms.integration.api.v1.collectors.CollectionSet;
import org.opennms.integration.api.v1.model.Node;
import org.opennms.plugins.odl.OpendaylightRestconfClient;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

public class MetricGeneratorIT {

    private MetricGenerator metricGenerator = new MetricGenerator();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.wireMockConfig()
            .withRootDirectory(Paths.get("src", "test", "resources").toString())
            .dynamicPort());

    @Test
    public void canRetrieveNodeFromInventoryAndGenerateMetrics() throws Exception {
        stubFor(get(urlEqualTo("/restconf/operational/opendaylight-inventory:nodes/node/openflow:4"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "Content-Type: application/yang.data+json; charset=utf-8")
                        .withBodyFile("operational-inventory-node.json")));

        // Make the call
        OpendaylightRestconfClient client = new OpendaylightRestconfClient(String.format("http://localhost:%s", wireMockRule.port()));
        org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node node = client.getNodeFromOperationalInventory("openflow:4");

        // Ensure we loaded the dataset
        assertEquals("openflow:4", node.getId().getValue());

        FlowCapableNode flowCapableNode = node.getAugmentation(FlowCapableNode.class);
        assertEquals("Open vSwitch", flowCapableNode.getHardware());

        Node onmsNode = mock(Node.class);
        CollectionSet collectionSet = metricGenerator.toCollectionSet(onmsNode, node);
        assertThat(collectionSet.getCollectionSetResources(), hasSize(5));
    }
}
