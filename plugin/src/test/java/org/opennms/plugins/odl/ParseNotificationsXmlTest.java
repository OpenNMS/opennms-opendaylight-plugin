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

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.io.ByteArrayInputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.Ignore;
import org.junit.Test;
import org.opendaylight.mdsal.binding.dom.codec.api.BindingCodecTreeNode;
import org.opendaylight.mdsal.binding.dom.codec.gen.impl.StreamWriterGenerator;
import org.opendaylight.mdsal.binding.dom.codec.impl.BindingNormalizedNodeCodecRegistry;
import org.opendaylight.mdsal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.mdsal.binding.generator.util.BindingRuntimeContext;
import org.opendaylight.mdsal.binding.generator.util.JavassistUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.DataChangedNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.data.changed.notification.DataChangeEvent;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.google.common.io.Resources;

import javassist.ClassPool;

@Ignore("Broken due to NPE in XML parsing?")
public class ParseNotificationsXmlTest {
    private static final Logger LOG = LoggerFactory.getLogger(ParseNotificationsXmlTest.class);

    @Test
    public void parseIt() throws Exception {
        String notificationsXml = Resources.toString(Resources.getResource("example-notifications.xml"), StandardCharsets.UTF_8);
        NetconfNotification notification = parseNotificationXml(notificationsXml);

        assertThat(notification, notNullValue());
        assertThat(notification.getEventTime().toInstant().getEpochSecond(), equalTo(1552579737L));

        assertThat(notification.getDataChangedNotifications(), hasSize(1));

        DataChangedNotification dataChangedNotification = notification.getDataChangedNotifications().get(0);
        assertThat(dataChangedNotification.getDataChangeEvent(), hasSize(10));

        DataChangeEvent dataChangeEvent = dataChangedNotification.getDataChangeEvent().get(0);
        assertThat(dataChangeEvent.getPath(), equalTo("/network-topology:network-topology/network-topology:topology[network-topology:topology-id='flow:1']"));
        assertThat(dataChangeEvent.getOperation(), equalTo("updated"));
    }


    private static final SchemaContext s_schemaContext;
    private static final BindingCodecTreeNode<NetworkTopology> s_networkTopologyCodec;
    private static final BindingCodecTreeNode<Topology> s_topologyCodec;
    private static final BindingCodecTreeNode<Node> s_nodeCodec;
    private static final BindingCodecTreeNode<DataChangedNotification> s_notificationCodec;

    private static final DataSchemaNode s_networkTopologySchemaNode;
    private static final DataSchemaNode s_topologySchemaNode;
    private static final DataSchemaNode s_notificationSchemaNode;

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
        ctx.registerModuleInfo(org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.$YangModuleInfoImpl.getInstance());
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

        // Data change event handling
        s_notificationCodec = registry.getCodecContext().getSubtreeCodec(InstanceIdentifier.create(DataChangedNotification.class));
        Module salRemoteModule = s_schemaContext.findModule("sal-remote", Revision.of("2014-01-14")).get();
        s_notificationSchemaNode = salRemoteModule.getNotifications().iterator().next().getDataChildByName(DataChangeEvent.QNAME);
        Objects.requireNonNull(s_notificationSchemaNode);
    }


    protected NetconfNotification parseNotificationXml(String xml) throws Exception {
        Document doc = loadXMLFromString(xml);

        XPath xPath = XPathFactory.newInstance().newXPath();
        String dateStr = (String)xPath.evaluate("/*[local-name()='notification']/*[local-name()='eventTime']/text()", doc, XPathConstants.STRING);
        ZonedDateTime eventTime = ZonedDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME);

        List<DataChangedNotification> dataChangedNotifications = new LinkedList<>();
        NodeList nodes = (NodeList)xPath.evaluate("/*[local-name()='notification']/*[local-name()='data-changed-notification']/*[local-name()='data-change-event']", doc, XPathConstants.NODESET);
        for (int i = 0; i < nodes.getLength(); ++i) {
            org.w3c.dom.Node n = nodes.item(i);
            String xmlNode = writeToXml(n);
            NormalizedNode<?, ?> transformedInput = streamXmlToNode(xmlNode, s_notificationSchemaNode);
            dataChangedNotifications.add(s_notificationCodec.deserialize(transformedInput));
        }

        NetconfNotification notification = new NetconfNotification();
        notification.setEventTime(eventTime);
        notification.setDataChangedNotifications(dataChangedNotifications);
        return notification;
    }

    private static Document loadXMLFromString(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes()));
    }

    private static String writeToXml(org.w3c.dom.Node node) throws TransformerException {
        StringWriter writer = new StringWriter();
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform(new DOMSource(node), new StreamResult(writer));
        return writer.toString();
    }

    private NormalizedNode<?, ?> streamXmlToNode(String xml, DataSchemaNode parentNode) throws Exception {
        Reader reader = new StringReader(xml);
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader xmlReader = factory.createXMLStreamReader(reader);
        return streamXmlToNode(xmlReader, parentNode);
    }

    private NormalizedNode<?, ?> streamXmlToNode(XMLStreamReader reader, DataSchemaNode parentNode) throws Exception {
        final NormalizedNodeResult result = new NormalizedNodeResult();
        final NormalizedNodeStreamWriter streamWriter = ImmutableNormalizedNodeStreamWriter.from(result);
        final XmlParserStream xmlParser = XmlParserStream.create(streamWriter, s_schemaContext, parentNode);
        xmlParser.parse(reader);
        return result.getResult();
    }

    private static class NetconfNotification {
        private ZonedDateTime eventTime;
        private List<DataChangedNotification> dataChangedNotifications;

        public ZonedDateTime getEventTime() {
            return eventTime;
        }

        public void setEventTime(ZonedDateTime eventTime) {
            this.eventTime = eventTime;
        }

        public List<DataChangedNotification> getDataChangedNotifications() {
            return dataChangedNotifications;
        }

        public void setDataChangedNotifications(List<DataChangedNotification> dataChangedNotifications) {
            this.dataChangedNotifications = dataChangedNotifications;
        }
    }

}
