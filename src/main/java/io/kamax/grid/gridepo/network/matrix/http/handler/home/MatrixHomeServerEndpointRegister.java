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

package io.kamax.grid.gridepo.network.matrix.http.handler.home;

import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.network.matrix.http.handler.UnrecognizedEndpointHandler;
import io.kamax.grid.gridepo.network.matrix.http.handler.home.server.RoomDirectoryLookupHandler;
import io.kamax.grid.gridepo.network.matrix.http.handler.home.server.RoomMakeJoinHandler;
import io.kamax.grid.gridepo.network.matrix.http.handler.home.server.RoomSendJoinHandler;
import io.undertow.server.RoutingHandler;

public class MatrixHomeServerEndpointRegister {

    public static void apply(Gridepo g, RoutingHandler handler) {
        handler
                .setFallbackHandler(new UnrecognizedEndpointHandler())
                .setInvalidMethodHandler(new UnrecognizedEndpointHandler())

                .get("/_matrix/federation/v1/query/directory", new RoomDirectoryLookupHandler(g))
                .get("/_matrix/federation/v1/make_join/{roomId}/{userId}", new RoomMakeJoinHandler(g))
                .put("/_matrix/federation/v2/send_join/{roomId}/{userId}", new RoomSendJoinHandler(g))
        ;
    }

}