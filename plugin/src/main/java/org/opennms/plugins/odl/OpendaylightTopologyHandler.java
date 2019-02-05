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

import java.util.Objects;

import org.opennms.integration.api.v1.config.requisition.Requisition;
import org.opennms.integration.api.v1.events.EventHandler;
import org.opennms.integration.api.v1.events.EventListener;
import org.opennms.integration.api.v1.graph.GraphContainer;
import org.opennms.integration.api.v1.graph.GraphRepository;
import org.opennms.integration.api.v1.model.InMemoryEvent;
import org.opennms.integration.api.v1.requisition.RequisitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpendaylightTopologyHandler implements EventListener {
    private static final Logger LOG = LoggerFactory.getLogger(OpendaylightTopologyHandler.class);

    public static final String IMPORT_SUCCESSFUL_UEI = "uei.opennms.org/internal/importer/importSuccessful";
    public static final String PARM_FOREIGN_SOURCE = "foreignSource";

    private final OpendaylightRestconfClient client;
    private final RequisitionRepository requisitionRepository;
    private final GraphRepository graphRepository;

    public OpendaylightTopologyHandler(OpendaylightRestconfClient client,
                                       RequisitionRepository requisitionRepository,
                                       GraphRepository graphRepository) {
        this.client = Objects.requireNonNull(client);
        this.requisitionRepository = Objects.requireNonNull(requisitionRepository);
        this.graphRepository = Objects.requireNonNull(graphRepository);
    }

    @Override
    public String getName() {
        return OpendaylightTopologyHandler.class.getName();
    }

    @Override
    public int getNumThreads() {
        return 1;
    }

    @EventHandler(uei = IMPORT_SUCCESSFUL_UEI)
    public void onImportSuccessful(InMemoryEvent event) {
        // Extract the name of the referenced FS
        final String foreignSource = event.getParameterValue(PARM_FOREIGN_SOURCE).orElse(null);
        if (foreignSource == null) {
            LOG.warn("No foreign source parameter found on import successful event. Ignoring.");
            return;
        }

        // TODO: Quickly determine whether or not we need to load the requisition - it may be large and unrelated

        // Retrieve a copy of the requisition in question
        final Requisition requisition = requisitionRepository.getDeployedRequisition(foreignSource);
        requisition.getNodes();

        // Figure out which controller

        // Load the topology
        try {
            client.getOperationalTopology("flow:1");
        } catch (Exception e) {
            e.printStackTrace();
        }


        // Build the graph
        GraphContainer graphContainer = null; ///

        // Save it
        graphRepository.save(graphContainer);

        // Done.
    }

}
