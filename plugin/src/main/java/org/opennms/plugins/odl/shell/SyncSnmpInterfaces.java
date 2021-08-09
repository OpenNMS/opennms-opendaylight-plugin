package org.opennms.plugins.odl.shell;

import static org.opennms.plugins.odl.OdlMetadata.NODE_ID_INDEX_KEY;
import static org.opennms.plugins.odl.OpendaylightRequisitionProvider.METADATA_CONTEXT_ID;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.opennms.integration.api.v1.dao.NodeDao;
import org.opennms.integration.api.v1.model.Node;
import org.opennms.integration.api.v1.model.SnmpInterface;
import org.opennms.plugins.odl.OpendaylightRequisitionProvider;

@Command(scope = "opennms-odl", name = "sync-snmp-interfaces", description="Synchronize the SNMP interfaces for a given switch.")
@Service
public class SyncSnmpInterfaces implements Action {

    @Reference
    private NodeDao nodeDao;

    @Reference
    private DataSource dataSource;

    @Option(name = "-k", aliases = "--node-id-index", description = "Node ID Index", required = true)
    private Integer nodeIdIndex;

    @Option(name = "-i", aliases = "--ifIndex", description = "Interface Index", required = true, multiValued = true)
    private List<Integer> ifIndexes;

    @Option(name = "-n", aliases = "--ifName", description = "Interface Name", required = true, multiValued = true)
    private List<String> ifNames;

    @Override
    public Object execute() throws SQLException {
        if (ifIndexes.size() < 1) {
            throw new RuntimeException("Must provide at least one ifIndex");
        }
        if (ifIndexes.size() != ifNames.size()) {
            throw new RuntimeException("Must have an equal number of ifIndexes and ifNames");
        }

        final Optional<Node> maybeNode = findNodeWithIndex(nodeIdIndex);
        if (!maybeNode.isPresent()) {
            throw new RuntimeException("No node found with nodeIdIndex: " + nodeIdIndex);
        }

        // Convert list to a map
        Map<Integer, String> ifNamesByIndex = new LinkedHashMap<>();
        for (int i = 0; i < ifIndexes.size(); i++) {
            ifNamesByIndex.put(ifIndexes.get(i), ifNames.get(i).trim());
        }

        final Node node = maybeNode.get();
        // Build the list of existing ifIndexes and compare to the given set while we're iterating
        final Set<Integer> existingIfIndexes = new HashSet<>();
        for (SnmpInterface snmpInterface : node.getSnmpInterfaces()) {
            existingIfIndexes.add(snmpInterface.getIfIndex());

            final String expectedIfName = ifNamesByIndex.get(snmpInterface.getIfIndex());
            if (expectedIfName == null) {
                System.out.printf("Node with label:%s and dbid:%d has SNMP interface with ifIndex:%d, but it was not present in the list. Ignoring.\n",
                        node.getLabel(), node.getId(), snmpInterface.getIfIndex());
            } else if (!Objects.equals(snmpInterface.getIfName().trim(), expectedIfName)) {
                System.out.printf("Node with label:%s and dbid:%d has SNMP interface with ifIndex:%d, but it has name:%s instead of '%s'. Ignoring.\n",
                        node.getLabel(), node.getId(), snmpInterface.getIfIndex(), snmpInterface.getIfName(), expectedIfName);
            }
        }

        for (Integer ifIndex : ifIndexes) {
            if (!existingIfIndexes.contains(ifIndex)) {
                try(Connection conn = dataSource.getConnection()) {
                    String query = "insert into snmpinterface (nodeid, snmpifindex, snmpifdescr, snmpifname) values (?, ?, ?, ?)";
                    try(PreparedStatement stmt = conn.prepareStatement(query)) {
                        stmt.setInt(1, node.getId());
                        stmt.setInt(2, ifIndex);
                        stmt.setString(3, ifNamesByIndex.get(ifIndex));
                        stmt.setString(4, ifNamesByIndex.get(ifIndex));
                        System.out.printf("Inserting SNMP interface for node with label:%s, dbid:%d and ifIndex:%d: %s.\n",
                                node.getLabel(), node.getId(), ifIndex, stmt);
                        stmt.execute();
                    }
                }
            }
        }

        return null;
    }

    private Optional<Node> findNodeWithIndex(int nodeIdIndex) {
        return nodeDao.getNodesInForeignSource(OpendaylightRequisitionProvider.DEFAULT_FOREIGN_SOURCE).stream()
                .filter(n -> n.getMetaData().stream()
                        .filter(m -> METADATA_CONTEXT_ID.equals(m.getContext()))
                        .filter(m -> NODE_ID_INDEX_KEY.equals(m.getKey()))
                        .anyMatch(m -> Integer.toString(nodeIdIndex).equals(m.getValue())))
                .findFirst();
    }

}
