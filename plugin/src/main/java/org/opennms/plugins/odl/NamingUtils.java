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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NamingUtils {

    public static String getNodeIdFromForeignId(String foreignId) {
        return foreignId.replace("_", ":");
    }

    public static String generateIpAddressForForeignId(String foreignId) {
        // TODO Generate IPv6 addresses in the link-local prefix fe80::/10
        Pattern switches = Pattern.compile(".*openflow_(\\d+)$");
        Matcher m = switches.matcher(foreignId);
        if (m.find()) {
            return "127.0.10." + m.group(1);
        }

        Pattern hosts = Pattern.compile(".*host_00_00_00_00_00_0(\\d+)$");
        m = hosts.matcher(foreignId);
        if (m.find()) {
            return "127.0.20." + m.group(1);
        }

        throw new RuntimeException("Unsupported fid: " + foreignId);
    }
}
