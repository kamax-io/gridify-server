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

package io.kamax.gridify.server.core.channel;

import io.kamax.gridify.server.network.grid.core.ChannelAlias;
import io.kamax.gridify.server.network.grid.core.ChannelID;
import io.kamax.gridify.server.network.grid.core.ServerID;

import java.util.Collections;
import java.util.Set;

public class ChannelLookup {

    private ChannelAlias alias;
    private ChannelID id;
    private Set<ServerID> servers;

    public ChannelLookup(ChannelAlias alias, ChannelID id, Set<ServerID> servers) {
        this.alias = alias;
        this.id = id;
        this.servers = Collections.unmodifiableSet(servers);
    }

    public ChannelAlias getAlias() {
        return alias;
    }

    public ChannelID getId() {
        return id;
    }

    public Set<ServerID> getServers() {
        return servers;
    }

}
