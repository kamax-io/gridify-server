/*
 * Gridify Server
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

package io.kamax.gridify.server.network.matrix.core;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.core.crypto.Cryptopher;
import io.kamax.gridify.server.core.event.EventStreamer;
import io.kamax.gridify.server.core.signal.SignalBus;
import io.kamax.gridify.server.core.store.DataStore;
import io.kamax.gridify.server.network.matrix.core.domain.MatrixDomain;
import io.kamax.gridify.server.network.matrix.core.federation.FederationPusher;
import io.kamax.gridify.server.network.matrix.core.federation.HomeServerManager;
import io.kamax.gridify.server.network.matrix.core.room.RoomDirectory;
import io.kamax.gridify.server.network.matrix.core.room.RoomManager;

import java.util.Queue;

public interface MatrixCore {

    GridifyServer gridify();

    SignalBus bus();

    DataStore store();

    Cryptopher crypto();

    RoomManager roomMgr();

    RoomDirectory roomDir();

    HomeServerManager hsMgr();

    boolean isLocal(String host);

    MatrixDomain addDomain(String domain);

    MatrixServer forDomain(String domain);

    void removeDomain(String domain);

    MatrixServer vHost(String host);

    EventStreamer getStreamer();

    FederationPusher getFedPusher();

    Queue<JsonObject> getCommandResponseQueue(String userId);

}
