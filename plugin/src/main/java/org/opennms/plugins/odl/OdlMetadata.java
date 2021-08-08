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

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.opennms.integration.api.v1.model.MetaData;
import org.opennms.integration.api.v1.model.Node;

public class OdlMetadata {
    public static final String NODE_ID_KEY = "nodeId";
    public static final String NODE_ID_INDEX_KEY = "nodeIdIndex";
    public static final String TOPOLOGY_ID_KEY = "topologyId";

    private final Map<String, String> metadata;

    public OdlMetadata(Node node) {
        Objects.requireNonNull(node);
        metadata = getOdlMetadata(node);
    }

    public String getNodeId() {
        return metadata.get(NODE_ID_KEY);
    }

    public String getTopologyId() {
        return metadata.get(TOPOLOGY_ID_KEY);
    }

    private static Map<String, String> getOdlMetadata(Node node) {
        return node.getMetaData().stream()
                .filter(m -> Objects.equals(OpendaylightRequisitionProvider.METADATA_CONTEXT_ID, m.getContext()))
                .collect(Collectors.toMap(MetaData::getKey, MetaData::getValue));
    }

}
