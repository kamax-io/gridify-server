/*
 * Gridify Server
 * Copyright (C) 2019 Kamax Sarl
 *
 * https://www.kamax.io/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.kamax.gridify.server.network.grid.core;

import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

public class ServerID extends GridEntityID {

    public static final String Sigill = ":";

    public static ServerID parse(String id) {
        if (!StringUtils.startsWith(id, Sigill)) {
            throw new IllegalArgumentException("Does not start with " + Sigill);
        }

        return new ServerID(id.substring(1));
    }

    public static ServerID from(String namespace) {
        return new ServerID(encode(namespace));
    }

    public static ServerID fromDns(String domain) {
        return new ServerID("dns:" + encode(domain));
    }

    public Optional<String> tryDecodeDns() {
        if (!base().startsWith("dns:")) {
            return Optional.empty();
        }

        return tryDecode(base().substring(4));
    }

    public ServerID(String id) {
        super(Sigill, id);
    }

}
