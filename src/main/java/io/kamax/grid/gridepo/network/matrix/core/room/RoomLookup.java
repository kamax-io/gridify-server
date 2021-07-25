/*
 * Gridepo - Grid Data Server
 * Copyright (C) 2021 Kamax Sarl
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

package io.kamax.grid.gridepo.network.matrix.core.room;

import java.util.Collections;
import java.util.Set;

public class RoomLookup {

    private final String alias;
    private final String id;
    private final Set<String> servers;

    public RoomLookup(String alias, String id, Set<String> servers) {
        this.alias = alias;
        this.id = id;
        this.servers = Collections.unmodifiableSet(servers);
    }

    public String getAlias() {
        return alias;
    }

    public String getId() {
        return id;
    }

    public Set<String> getServers() {
        return servers;
    }

}
