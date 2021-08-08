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

package io.kamax.grid.gridepo.network.matrix.core;

import io.kamax.grid.gridepo.core.crypto.Cryptopher;
import io.kamax.grid.gridepo.core.event.EventStreamer;
import io.kamax.grid.gridepo.core.signal.SignalBus;
import io.kamax.grid.gridepo.core.store.DataStore;
import io.kamax.grid.gridepo.network.matrix.core.federation.FederationPusher;
import io.kamax.grid.gridepo.network.matrix.core.federation.HomeServerManager;
import io.kamax.grid.gridepo.network.matrix.core.room.RoomDirectory;
import io.kamax.grid.gridepo.network.matrix.core.room.RoomManager;

public interface MatrixCore {

    SignalBus bus();

    DataStore store();

    Cryptopher crypto();

    RoomManager roomMgr();

    RoomDirectory roomDir();

    HomeServerManager hsMgr();

    boolean isLocal(String host);

    MatrixServer vHost(String host);

    EventStreamer getStreamer();

    FederationPusher getFedPusher();

}
