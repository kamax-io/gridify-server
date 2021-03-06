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

package io.kamax.gridify.server.network.matrix.http.handler.home;

import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.http.RootLogoHandler;
import io.kamax.gridify.server.network.matrix.http.handler.home.server.*;
import io.undertow.server.RoutingHandler;

public class MatrixHomeServerEndpointRegister {

    public static void apply(GridifyServer g, RoutingHandler handler) {
        KeyServerHandler keySrvHandler = new KeyServerHandler(g);

        handler
                .get("/", new RootLogoHandler(g))
                .get("/static/**", new RootLogoHandler(g))

                // TODO Caching may make server inaccessible for a long time if wrong values are returned
                // Research if really wanted and if yes, how to do it better
                //.get("/.well-known/matrix/server", new WellKnownHandler(g))

                .get("/_matrix/federation/v1/version", new VersionHandler(g))

                .get("/_matrix/key/v2/server", keySrvHandler)
                .get("/_matrix/key/v2/server/{keyId}", keySrvHandler)

                .put("/_matrix/federation/v1/send/{txnId}", new TransactionSendHandler(g))

                .get("/_matrix/federation/v1/make_join/{roomId}/{userId}", new RoomMakeJoinHandler(g))
                .put("/_matrix/federation/v2/send_join/{roomId}/{userId}", new RoomSendJoinHandler(g))
                .put("/_matrix/federation/v2/invite/{roomId}/{eventId}", new RoomInviteHandler(g))

                .get("/_matrix/federation/v1/event/{eventId}", new EventGetHandler(g))
                .post("/_matrix/federation/v1/get_missing_events/{roomId}", new RoomMissingEventsHandler(g))
                .get("/_matrix/federation/v1/backfill/{roomId}", new RoomBackfillHandler(g))
                .get("/_matrix/federation/v1/state_ids/{roomId}", new RoomEventStateIdsHandler(g))

                .get("/_matrix/federation/v1/query/directory", new RoomDirectoryLookupHandler(g))
                .get("/_matrix/federation/v1/query/profile", new UserProfileHandler(g))
        ;
    }

}
