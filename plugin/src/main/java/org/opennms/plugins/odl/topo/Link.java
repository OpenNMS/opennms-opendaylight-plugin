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

package org.opennms.plugins.odl.topo;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="links")
@XmlAccessorType(XmlAccessType.NONE)
public class Link {

    @XmlAttribute(name="id")
    private String m_id;

    @XmlAttribute(name="destination-node")
    private String m_destinationNode;

    @XmlAttribute(name="destination-tp")
    private String m_destinationTerminationPoint;

    @XmlAttribute(name="source-node")
    private String m_sourceNode;

    @XmlAttribute(name="source-tp")
    private String m_sourceTerminationPoint;

    public Link() {
        // pass
    }

    public Link(org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link link) {
        m_id = link.getLinkId().getValue();
        m_destinationNode = link.getDestination().getDestNode().getValue();
        m_destinationTerminationPoint = link.getDestination().getDestTp().getValue();
        m_sourceNode = link.getSource().getSourceNode().getValue();
        m_sourceTerminationPoint = link.getSource().getSourceTp().getValue();
    }

    public String getId() {
        return m_id;
    }

    public void setId(String id) {
        m_id = id;
    }

    public String getDestinationNode() {
        return m_destinationNode;
    }

    public void setDestinationNode(String destinationNode) {
        m_destinationNode = destinationNode;
    }

    public String getDestinationTerminationPoint() {
        return m_destinationTerminationPoint;
    }

    public void setDestinationTerminationPoint(String destinationTerminationPoint) {
        m_destinationTerminationPoint = destinationTerminationPoint;
    }

    public String getSourceNode() {
        return m_sourceNode;
    }

    public void setSourceNode(String sourceNode) {
        m_sourceNode = sourceNode;
    }

    public String getSourceTerminationPoint() {
        return m_sourceTerminationPoint;
    }

    public void setSourceTerminationPoint(String sourceTerminationPoint) {
        m_sourceTerminationPoint = sourceTerminationPoint;
    }
}
