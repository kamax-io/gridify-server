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

package io.kamax.gridify.server.network.grid.core;

import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.core.event.EventService;
import io.kamax.gridify.server.core.event.EventStreamer;
import io.kamax.gridify.server.core.federation.DataServerManager;
import io.kamax.gridify.server.core.store.DataStore;

public interface GridServer {

    GridifyServer gridify();

    ServerID getOrigin();

    String getDomain();

    DataServerManager dataSrvMgr();

    EventService evSvc();

    DataStore store();

    EventStreamer streamer();

    boolean isLocal(ServerID id);

    boolean isLocal(UserID uId);

    GridDataServer forData();


}
