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
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.annotation.Nullable;

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
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import javassist.ClassPool;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * Java client for Opendaylight's RESTConf API.
 *
 * Uses Opendaylight's YANG tools for serializing/deserializing POJOs
 * to and from JSON.
 *
 * @author jwhite
 */
public class OpendaylightRestconfClient {
    private static final Logger LOG = LoggerFactory.getLogger(OpendaylightRestconfClient.class);

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

    private final String controllerUrl;
    private final String username;
    private final String password;
    private final boolean httpRequestLoggingEnabled = false;

    private final HttpUrl baseUrl;
    private final OkHttpClient okHttpClient;

    public OpendaylightRestconfClient(String controllerUrl) {
        this(controllerUrl, DEFAULT_USERNAME, DEFAULT_PASSWORD);
    }

    public OpendaylightRestconfClient(String controllerUrl, String username, String password) {
        this.controllerUrl = Objects.requireNonNull(controllerUrl);
        this.username = username;
        this.password = password;

        baseUrl = HttpUrl.parse(controllerUrl);
        if (baseUrl == null) {
            throw new IllegalArgumentException("Invalid controller URL: " + controllerUrl);
        }
        final OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
        if (username != null && password != null) {
            clientBuilder.authenticator((route, response) -> response.request().newBuilder()
                    .header("Authorization", Credentials.basic(username, password))
                    .build());
        }
        clientBuilder.connectTimeout(15, TimeUnit.SECONDS);
        clientBuilder.writeTimeout(15, TimeUnit.SECONDS);
        clientBuilder.readTimeout(60, TimeUnit.SECONDS);
        if (httpRequestLoggingEnabled) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            clientBuilder.addInterceptor(logging);
        }
        okHttpClient = clientBuilder.build();
    }

    public void destroy() {
        // Trigger shutdown of the dispatcher's executor so this process can exit cleanly.
        okHttpClient.dispatcher().executorService().shutdown();
    }

    private String doGet(HttpUrl httpUrl) throws IOException {
        final Request request = new Request.Builder()
                .url(httpUrl)
                .get()
                .build();
        final Response response = okHttpClient.newCall(request).execute();

        final ResponseBody responseBody = response.body();
        String responseBodyAsStr = null;
        if (responseBody != null) {
            responseBodyAsStr = responseBody.string();
        }

        if (!response.isSuccessful()) {
            throw new IOException(String.format("GET for URL: %s failed. Response: %s Body: %s",
                    httpUrl, response, responseBodyAsStr));
        }

        return responseBodyAsStr;
    }

    private Response doGetWithResponse(HttpUrl httpUrl) throws IOException {
        final Request request = new Request.Builder()
                .url(httpUrl)
                .get()
                .build();
        return okHttpClient.newCall(request).execute();
    }

    public NetworkTopology getOperationalNetworkTopology() throws Exception {
        final HttpUrl httpUrl = baseUrl.newBuilder()
                .addPathSegment("restconf")
                .addPathSegment("operational")
                .addPathSegment("network-topology:network-topology")
                .addPathSegment("") // add an empty segment, since it must end with trailing slash
                .build();
        final String json = doGet(httpUrl);
        final NormalizedNode<?,?> node = streamJsonToNode(json, s_schemaContext);
        return s_networkTopologyCodec.deserialize(node);
    }

    public Topology getOperationalTopology(TopologyId topologyId) throws Exception {
        return getOperationalTopology(topologyId.getValue());
    }

    public Topology getOperationalTopology(String topologyId) throws Exception {
        final HttpUrl httpUrl = baseUrl.newBuilder()
                .addPathSegment("restconf")
                .addPathSegment("operational")
                .addPathSegment("network-topology:network-topology")
                .addPathSegment("topology")
                .addPathSegment(topologyId)
                .build();
        final String json = doGet(httpUrl);
        final MapNode node = (MapNode)streamJsonToNode(json, s_networkTopologySchemaNode);
        return s_topologyCodec.deserialize(node.getValue().iterator().next());
    }

    public Node getNodeFromOperationalTopology(TopologyId topologyId, NodeId nodeId) throws Exception {
        return getNodeFromOperationalTopology(topologyId.getValue(), nodeId.getValue());
    }

    public Node getNodeFromOperationalTopology(String topologyId, String nodeId) throws Exception {
        final HttpUrl httpUrl = baseUrl.newBuilder()
                .addPathSegment("restconf")
                .addPathSegment("operational")
                .addPathSegment("network-topology:network-topology")
                .addPathSegment("topology")
                .addPathSegment(topologyId)
                .addPathSegment("node")
                .addPathSegment(nodeId)
                .build();
        final Response response = doGetWithResponse(httpUrl);
        if (response.code() == 404) {
            return null;
        } else if (response.isSuccessful()) {
            final String json = doGet(httpUrl);
            final MapNode node = (MapNode)streamJsonToNode(json, s_topologySchemaNode);
            return s_nodeCodec.deserialize(node.getValue().iterator().next());
        } else {
            throw new IOException(String.format("GET for URL: %s failed. Response: %s",
                    httpUrl, response));
        }
    }

    private NormalizedNode<?, ?> streamJsonToNode(String json, DataSchemaNode parentNode) {
        final NormalizedNodeResult result = new NormalizedNodeResult();
        final NormalizedNodeStreamWriter streamWriter = ImmutableNormalizedNodeStreamWriter.from(result);

        final JsonParserStream jsonParser = JsonParserStream.create(streamWriter, s_schemaContext, parentNode);
        jsonParser.parse(new JsonReader(new StringReader(json)));

        return result.getResult();
    }

    /**
     * @param path the path
     * @param datastore CONFIGURATION or OPERATIONAL
     * @param scope BASE, ONE, or SUBTREE
     * @return
     * @throws IOException
     */
    protected String getStreamName(String path, String datastore, String scope) throws IOException {
        final HttpUrl httpUrl = baseUrl.newBuilder()
                .addPathSegment("restconf")
                .addPathSegment("operations")
                .addPathSegment("sal-remote:create-data-change-event-subscription")
                .build();
        final RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"),
                "{\n" +
                        "    \"input\": {\n" +
                        "        \"path\": \"" + path + "\",\n" +
                        "        \"sal-remote-augment:datastore\": \"" + datastore + "\",\n" +
                        "        \"sal-remote-augment:scope\": \""+ scope + "\"\n" +
                        "    }\n" +
                        "}");
        final String jsonResponse = doPost(httpUrl, requestBody);
        // We expect the response to look like:
        //  {"output":{"stream-name":"data-change-event-subscription/opendaylight-inventory:nodes/datastore=CONFIGURATION/scope=BASE"}}
        LOG.debug("Got response: {}", jsonResponse);

        final JsonParser jp = new JsonParser();
        final JsonElement root = jp.parse(jsonResponse);
        final String streamName = root.getAsJsonObject()
                .getAsJsonObject("output")
                .getAsJsonPrimitive("stream-name")
                .getAsString();
        LOG.debug("Found stream name: {}", streamName);

        return streamName;
    }

    protected String subscribeToStream(String streamName) throws IOException {
        final HttpUrl httpUrl = baseUrl.newBuilder()
                .addPathSegment("restconf")
                .addPathSegment("streams")
                .addPathSegment("stream")
                // The stream name is already URL encoded, ensure we don't re-encode it
                .addEncodedPathSegments(streamName)
                .build();
        final String jsonResponse = doGet(httpUrl);
        // We expect the response to look like:
        // {"location":"ws://localhost:8185/data-change-event-subscription/opendaylight-inventory:nodes/datastore=CONFIGURATION/scope=BASE"}
        LOG.debug("Got response: {}", jsonResponse);

        final JsonParser jp = new JsonParser();
        final JsonElement root = jp.parse(jsonResponse);
        final String wsUrl = root.getAsJsonObject()
                .getAsJsonPrimitive("location")
                .getAsString();
        LOG.debug("Found URL: {}", wsUrl);

        return wsUrl;
    }

    public WebSocket streamChangesForTopology(String topologyId, Consumer<String> consumer) throws IOException {
        return streamChanges(String.format("/network-topology:network-topology/network-topology:topology[network-topology:topology-id='%s']", topologyId),
                "OPERATIONAL","SUBTREE", consumer);
    }

    public WebSocket streamChanges(String path, String datastore, String scope, Consumer<String> consumer) throws IOException {
        // See https://wiki.opendaylight.org/view/OpenDaylight_Controller:MD-SAL:Restconf:Change_event_notification_subscription
        final String streamName = getStreamName(path, datastore, scope);
        final String wsUrl = subscribeToStream(streamName);

        final ChangeEventWSListener listener = new ChangeEventWSListener(consumer);
        Request request = new Request.Builder()
                .url(wsUrl)
                .build();
        return okHttpClient.newWebSocket(request, listener);
    }

    private String doPost(HttpUrl httpUrl, RequestBody requestBody) throws IOException {
        final Request request = new Request.Builder()
                .url(httpUrl)
                .post(requestBody)
                .addHeader("Accept", "application/json")
                .build();
        final Response response = okHttpClient.newCall(request).execute();

        final ResponseBody responseBody = response.body();
        String responseBodyAsStr = null;
        if (responseBody != null) {
            responseBodyAsStr = responseBody.string();
        }

        if (!response.isSuccessful()) {
            throw new IOException(String.format("GET for URL: %s failed. Response: %s Body: %s",
                    httpUrl, response, responseBodyAsStr));
        }
        return responseBodyAsStr;
    }

    private static class ChangeEventWSListener extends WebSocketListener {
        private static final Logger LOG = LoggerFactory.getLogger(ChangeEventWSListener.class);
        private final Consumer<String> consumer;

        public ChangeEventWSListener(Consumer<String> consumer) {
            this.consumer = Objects.requireNonNull(consumer);
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            LOG.debug("Websocket is open.");
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            // Notifications are always in XML format
            LOG.debug("Received message.");
            consumer.accept(text);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            LOG.debug("Websocket is closed.");
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, @Nullable Response response) {
            LOG.warn("Websocket failure.", t);
        }
    }

}
