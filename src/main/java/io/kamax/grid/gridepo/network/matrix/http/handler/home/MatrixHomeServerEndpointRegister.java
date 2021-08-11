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
import io.kamax.grid.gridepo.network.matrix.http.handler.home.server.*;
import io.undertow.server.RoutingHandler;

public class MatrixHomeServerEndpointRegister {

    public static void apply(Gridepo g, RoutingHandler handler) {
        KeyServerHandler keySrvHandler = new KeyServerHandler(g);

        handler
                .get("/.well-known/matrix/server", new WellKnownHandler(g))

                .get("/_matrix/key/v2/server", keySrvHandler)
                .get("/_matrix/key/v2/server/{keyId}", keySrvHandler)

                .put("/_matrix/federation/v1/send/{txnId}", new TransactionSendHandler(g))

                .get("/_matrix/federation/v1/make_join/{roomId}/{userId}", new RoomMakeJoinHandler(g))
                .put("/_matrix/federation/v2/send_join/{roomId}/{userId}", new RoomSendJoinHandler(g))
                .post("/_matrix/federation/v1/get_missing_events/{roomId}", new RoomMissingEventsHandler(g))

                .get("/_matrix/federation/v1/query/directory", new RoomDirectoryLookupHandler(g))
        ;
    }

}
