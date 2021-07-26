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

import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.core.event.EventStreamer;
import io.kamax.grid.gridepo.exception.NotImplementedException;
import io.kamax.grid.gridepo.network.matrix.core.MatrixCore;
import io.kamax.grid.gridepo.network.matrix.core.MatrixServer;
import io.kamax.grid.gridepo.network.matrix.core.federation.HomeServerManager;
import io.kamax.grid.gridepo.network.matrix.core.room.RoomDirectory;
import io.kamax.grid.gridepo.network.matrix.core.room.RoomManager;

// FIXME do we need this?
public class BaseMatrixCore implements MatrixCore {

    private final Gridepo g;
    private final RoomManager rMgr;

    public BaseMatrixCore(Gridepo g) {
        this.g = g;
        rMgr = new RoomManager(g);
    }

    @Override
    public RoomManager roomMgr() {
        return rMgr;
    }

    @Override
    public RoomDirectory roomDir() {
        throw new NotImplementedException();
    }

    @Override
    public HomeServerManager hsMgr() {
        throw new NotImplementedException();
    }

    @Override
    public boolean isLocal(String host) {
        throw new NotImplementedException();
    }

    @Override
    public MatrixServer vHost(String host) {
        return new BaseMatrixServer(g, host);
    }

    @Override
    public EventStreamer getStreamer() {
        return new EventStreamer(g.getStore());
    }

}
