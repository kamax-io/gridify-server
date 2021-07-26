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

import java.util.ArrayList;
import java.util.List;

public class RoomAliasLookup {

    private final String alias;
    private final String id;
    private final List<String> residentServers;

    public RoomAliasLookup(String alias, String id, List<String> residentServers) {
        this.alias = alias;
        this.id = id;
        this.residentServers = new ArrayList<>(residentServers);
    }

    public String getAlias() {
        return alias;
    }

    public String getId() {
        return id;
    }

    public List<String> getResidentServers() {
        return residentServers;
    }

}
