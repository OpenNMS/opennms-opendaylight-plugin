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
import java.io.StringReader;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.opendaylight.mdsal.binding.dom.codec.api.BindingCodecTreeNode;
import org.opendaylight.mdsal.binding.dom.codec.gen.impl.StreamWriterGenerator;
import org.opendaylight.mdsal.binding.dom.codec.impl.BindingNormalizedNodeCodecRegistry;
import org.opendaylight.mdsal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.mdsal.binding.generator.util.BindingRuntimeContext;
import org.opendaylight.mdsal.binding.generator.util.JavassistUtils;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.util.BindingReflections;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.util.FilteringSchemaContextProxy;

import com.google.common.base.Charsets;
import com.google.gson.stream.JsonReader;

import javassist.ClassPool;

/**
 * Java client for Opendaylight's RESTConf API.
 *
 * Uses Opendaylight's YANG tools for serializing/deserializing POJOs
 * to and from JSON.
 *
 * @author jwhite
 */
public class OpendaylightRestconfClient {
    public static final int DEFAULT_PORT = 8181;
    public static final String DEFAULT_USERNAME = "admin";
    public static final String DEFAULT_PASSWORD = "admin";

    private static final SchemaContext s_schemaContext;
    private static final BindingCodecTreeNode<NetworkTopology> s_networkTopologyCodec;
    private static final BindingCodecTreeNode<Topology> s_topologyCodec;
    private static final BindingCodecTreeNode<Node> s_nodeCodec;

    private static final DataSchemaNode s_networkTopologySchemaNode;
    private static final DataSchemaNode s_topologySchemaNode;

    static {
        /*
         * This next block of code scans the class-path for .yang files in order to
         * generate the SchemaContext and CodecRegistry.
         */
        final ModuleInfoBackedContext ctx = ModuleInfoBackedContext.create();
        // Specify the YANG modules we want to load
        ctx.registerModuleInfo(org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.$YangModuleInfoImpl.getInstance());
        ctx.registerModuleInfo(org.opendaylight.yang.gen.v1.urn.opendaylight.host.tracker.rev140624.$YangModuleInfoImpl.getInstance());
        ctx.registerModuleInfo(org.opendaylight.yang.gen.v1.urn.opendaylight.model.topology.inventory.rev131030.$YangModuleInfoImpl.getInstance());
        // Alternatively, we could load everything on the classpath
        // ctx.addModuleInfos(BindingReflections.loadModuleInfos());
        s_schemaContext = ctx.tryToCreateSchemaContext().get();
        BindingRuntimeContext runtimeContext = BindingRuntimeContext.create(ctx, s_schemaContext);
        final JavassistUtils utils = JavassistUtils.forClassPool(ClassPool.getDefault());
        final BindingNormalizedNodeCodecRegistry registry = new BindingNormalizedNodeCodecRegistry(StreamWriterGenerator.create(utils));
        registry.onBindingRuntimeContextUpdated(runtimeContext);

        // Create codecs for the object we'll need to serialize/de-serialize
        s_networkTopologyCodec = registry.getCodecContext().getSubtreeCodec(InstanceIdentifier.create(NetworkTopology.class));
        s_topologyCodec = s_networkTopologyCodec.streamChild(Topology.class);
        s_nodeCodec = s_topologyCodec.streamChild(Node.class);

        s_networkTopologySchemaNode = s_schemaContext.getDataChildByName(NetworkTopology.QNAME);
        s_topologySchemaNode = ((DataNodeContainer)s_networkTopologySchemaNode).getDataChildByName(Topology.QNAME);
    }

    private final String m_host;
    private final int m_port;
    private final String m_username;
    private final String m_password;

    public OpendaylightRestconfClient(String host) {
        this(host, DEFAULT_PORT);
    }

    public OpendaylightRestconfClient(String host, int port) {
        this(host, port, DEFAULT_USERNAME, DEFAULT_PASSWORD);
    }

    public OpendaylightRestconfClient(String host, int port, String username, String password) {
        m_host = Objects.requireNonNull(host);
        m_port = port;
        m_username = username;
        m_password = password;
    }

    private String doGet(HttpGet httpGet) throws ParseException, IOException {
        HttpHost target = new HttpHost(m_host, m_port, "http");
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope(target.getHostName(), target.getPort()),
                new UsernamePasswordCredentials(m_username, m_password));
        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider).build()) {
            HttpResponse httpResponse = httpClient.execute(target, httpGet);
            if (httpResponse.getStatusLine().getStatusCode() != 200) {
                throw new IOException("Get did not return a 200: " + httpResponse);
            }
            HttpEntity entity = httpResponse.getEntity();
            return EntityUtils.toString(entity, Charsets.UTF_8);
        }
    }

    public NetworkTopology getOperationalNetworkTopology() throws Exception {
        final String json = doGet(new HttpGet("/restconf/operational/network-topology:network-topology/"));
        final NormalizedNode<?,?> node = streamJsonToNode(json, s_schemaContext);
        return s_networkTopologyCodec.deserialize(node);
    }

    public Topology getOperationalTopology(TopologyId topologyId) throws Exception {
        return getOperationalTopology(topologyId.getValue());
    }

    public Topology getOperationalTopology(String topologyId) throws Exception {
        final String json = doGet(new HttpGet(String.format("/restconf/operational/network-topology:network-topology/"
                + "topology/%s", topologyId)));
        final MapNode node = (MapNode)streamJsonToNode(json, s_networkTopologySchemaNode);
        return s_topologyCodec.deserialize(node.getValue().iterator().next());
    }

    public Node getNodeFromOperationalTopology(TopologyId topologyId, NodeId nodeId) throws Exception {
        return getNodeFromOperationalTopology(topologyId.getValue(), nodeId.getValue());
    }

    public Node getNodeFromOperationalTopology(String topologyId, String nodeId) throws Exception {
        final String json = doGet(new HttpGet(String.format("/restconf/operational/network-topology:network-topology/"
                + "topology/%s/node/%s", topologyId, nodeId)));
        final MapNode node = (MapNode)streamJsonToNode(json, s_topologySchemaNode);
        return s_nodeCodec.deserialize(node.getValue().iterator().next());
    }

    private NormalizedNode<?, ?> streamJsonToNode(String json, DataSchemaNode parentNode) {
        final NormalizedNodeResult result = new NormalizedNodeResult();
        final NormalizedNodeStreamWriter streamWriter = ImmutableNormalizedNodeStreamWriter.from(result);

        final JsonParserStream jsonParser = JsonParserStream.create(streamWriter, s_schemaContext, parentNode);
        jsonParser.parse(new JsonReader(new StringReader(json)));

        return result.getResult();
    }
}
