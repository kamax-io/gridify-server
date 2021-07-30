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

package io.kamax.grid.gridepo.network.matrix.core.base;

import io.kamax.grid.gridepo.network.matrix.core.MatrixCore;
import io.kamax.grid.gridepo.network.matrix.core.room.RoomAlias;
import io.kamax.grid.gridepo.network.matrix.core.room.RoomLookup;

import java.util.Optional;

public class ServerSession {

    private MatrixCore core;

    private String domain;
    private String remote;

    public ServerSession(MatrixCore core, String domain, String remote) {
        this.core = core;
        this.domain = domain;
        this.remote = remote;
    }

    public Optional<RoomLookup> lookupRoomAlias(String roomAlias) {
        RoomAlias alias = RoomAlias.parse(roomAlias);
        return core.roomDir().lookup(remote, alias, false);
    }

}
