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

import java.io.StringReader;
import java.util.Arrays;

import org.opendaylight.mdsal.binding.dom.codec.gen.impl.StreamWriterGenerator;
import org.opendaylight.mdsal.binding.dom.codec.impl.BindingNormalizedNodeCodecRegistry;
import org.opendaylight.mdsal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.mdsal.binding.generator.util.BindingRuntimeContext;
import org.opendaylight.mdsal.binding.generator.util.JavassistUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.YangModuleInfo;
import org.opendaylight.yangtools.yang.binding.util.BindingReflections;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

import com.google.gson.stream.JsonReader;

import javassist.ClassPool;

public class YangDecoder {

    private final SchemaContext context;
    private final BindingRuntimeContext bindingContext;
    private final BindingNormalizedNodeCodecRegistry codecRegistry;

    public YangDecoder() {
        this(Arrays.asList(org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.$YangModuleInfoImpl.getInstance(),
                org.opendaylight.yang.gen.v1.urn.opendaylight.host.tracker.rev140624.$YangModuleInfoImpl.getInstance(),
                org.opendaylight.yang.gen.v1.urn.opendaylight.model.topology.inventory.rev131030.$YangModuleInfoImpl.getInstance(),
                org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.$YangModuleInfoImpl.getInstance()));
    }

    public YangDecoder(final Iterable<? extends YangModuleInfo> moduleInfos) {
        System.out.println("Building context");
        final ModuleInfoBackedContext moduleContext = ModuleInfoBackedContext.create();
        //moduleContext.addModuleInfos(moduleInfos);

        moduleContext.registerModuleInfo(org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.$YangModuleInfoImpl.getInstance());
        moduleContext.registerModuleInfo(org.opendaylight.yang.gen.v1.urn.opendaylight.host.tracker.rev140624.$YangModuleInfoImpl.getInstance());
        moduleContext.registerModuleInfo(org.opendaylight.yang.gen.v1.urn.opendaylight.model.topology.inventory.rev131030.$YangModuleInfoImpl.getInstance());
        moduleContext.registerModuleInfo(org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.$YangModuleInfoImpl.getInstance());
        moduleContext.registerModuleInfo(org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.$YangModuleInfoImpl.getInstance());
        moduleContext.registerModuleInfo(org.opendaylight.yang.gen.v1.urn.opendaylight.flow.statistics.rev130819.$YangModuleInfoImpl.getInstance());
        moduleContext.registerModuleInfo(org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.$YangModuleInfoImpl.getInstance());
        moduleContext.registerModuleInfo(org.opendaylight.yang.gen.v1.urn.opendaylight.group.statistics.rev131111.$YangModuleInfoImpl.getInstance());
        moduleContext.registerModuleInfo(org.opendaylight.yang.gen.v1.urn.opendaylight.flow.table.statistics.rev131215.$YangModuleInfoImpl.getInstance());
        moduleContext.registerModuleInfo(org.opendaylight.yang.gen.v1.urn.opendaylight.l2switch.loopremover.rev140714.$YangModuleInfoImpl.getInstance());

        this.context = moduleContext.tryToCreateSchemaContext().get();
        System.out.println("Context built");

        System.out.println("Building Binding Context");
        this.bindingContext = BindingRuntimeContext.create(moduleContext, this.context);

        System.out.println("Building Binding Codec Factory");
        final BindingNormalizedNodeCodecRegistry bindingStreamCodecs = new BindingNormalizedNodeCodecRegistry(StreamWriterGenerator.create(JavassistUtils.forClassPool(ClassPool.getDefault())));
        bindingStreamCodecs.onBindingRuntimeContextUpdated(this.bindingContext);
        this.codecRegistry = bindingStreamCodecs;
        System.out.println("Mapping service built");
    }

    public NormalizedNode<?,?> normalizedNodeFromJsonString(final String inputJson) {
        final NormalizedNodeResult result = new NormalizedNodeResult();
        final NormalizedNodeStreamWriter streamWriter = ImmutableNormalizedNodeStreamWriter.from(result);
        // note: context used to be generated by using loadModules from TestUtils in
        //       org.opendaylight.yangtools.yang.data.codec.gson
        final JsonParserStream jsonParser = JsonParserStream.create(streamWriter, this.context);
        jsonParser.parse(new JsonReader(new StringReader(inputJson)));
        final NormalizedNode<?, ?> transformedInput = result.getResult();
        return transformedInput;
    }

    public Node getNode(String inputJson) {
        System.out.println(BindingReflections.findQName(Node.class));
        NormalizedNode<?,?> node = normalizedNodeFromJsonString(inputJson);
        //DataObject obj = codecRegistry.fromNormalizedNode(YangInstanceIdentifier.of(BindingReflections.findQName(NetworkTopology.class)), node);

        Object obj = codecRegistry.fromNormalizedNode(YangInstanceIdentifier.of(BindingReflections.findQName(Node.class)), node);
        return (Node)obj;
    }

}
