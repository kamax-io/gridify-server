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

import com.google.gson.JsonArray;
import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.network.grid.http.handler.matrix.home.client.SendRoomStateHandler;
import io.kamax.gridify.server.network.matrix.http.HomeClientAPI;
import io.kamax.gridify.server.network.matrix.http.HomeClientAPIr0;
import io.kamax.gridify.server.network.matrix.http.handler.EmptyJsonObjectHandler;
import io.kamax.gridify.server.network.matrix.http.handler.JsonObjectHandler;
import io.kamax.gridify.server.network.matrix.http.handler.OptionsHandler;
import io.kamax.gridify.server.network.matrix.http.handler.home.client.*;
import io.kamax.gridify.server.network.matrix.http.handler.home.client.dummy.UserSearchPublicisedGroups;
import io.undertow.server.RoutingHandler;

public class MatrixHomeClientEndpointRegister {

    public static void apply(GridifyServer g, RoutingHandler handler) {
        EmptyJsonObjectHandler ejuHandler = new EmptyJsonObjectHandler(g, false);
        EmptyJsonObjectHandler ejaHandler = new EmptyJsonObjectHandler(g, true);
        SendRoomStateHandler srsHandler = new SendRoomStateHandler(g);

        handler
                // CORS support
                .add("OPTIONS", HomeClientAPI.Base + "/**", new OptionsHandler())

                // Fundamental endpoints
                .get(HomeClientAPI.Base + "/versions", new VersionsHandler())
                .get(HomeClientAPIr0.Base + "/capabilities", ejaHandler)
                .get(HomeClientAPIr0.Base + "/login", new LoginGetHandler(g))
                .post(HomeClientAPIr0.Base + "/login", new LoginHandler(g))
                .get(HomeClientAPIr0.Base + "/sync", new SyncHandler(g))
                .post(HomeClientAPIr0.Base + "/logout", new LogoutHandler(g))

                // Account endpoints
                .get(HomeClientAPIr0.Base + "/register/available", new RegisterAvailableHandler(g))
                .post(HomeClientAPIr0.Base + "/register", new RegisterPostHandler(g))
                .get(HomeClientAPIr0.Base + "/account/3pid",
                        new JsonObjectHandler(g, true, "threepids", new JsonArray()))

                // User-related endpoints
                .get(HomeClientAPIr0.Base + "/profile/**", ejuHandler)
                .post(HomeClientAPIr0.Base + "/user_directory/search", new UserDirectorySearchHandler(g))

                // Room management endpoints
                .post(HomeClientAPIr0.Base + "/createRoom", new CreateRoomHandler(g))
                .post(HomeClientAPIr0.Room + "/invite", new RoomInviteHandler(g))
                .post(HomeClientAPIr0.Base + "/join/{roomId}", new RoomJoinHandler(g))
                .post(HomeClientAPIr0.Room + "/leave", new RoomLeaveHandler(g))
                .post(HomeClientAPIr0.Room + "/forget", ejaHandler)
                .get(HomeClientAPIr0.Room + "/initialSync", new RoomInitialSyncHandler(g))

                // Room event endpoints
                .put(HomeClientAPIr0.Room + "/send/{type}/{txnId}", new SendRoomEventHandler(g))
                .put(HomeClientAPIr0.Room + "/state/{type}", srsHandler)
                .put(HomeClientAPIr0.Room + "/state/{type}/{stateKey}", srsHandler)
                .get(HomeClientAPIr0.Room + "/messages", new RoomMessagesHandler(g))

                // Room state endpoints
                .get(HomeClientAPIr0.Room + "/members", new RoomMembersHandler(g))

                // Room Directory endpoints
                .get(HomeClientAPIr0.Directory + "/room/{roomAlias}", new RoomAliasLookupHandler(g))
                .put(HomeClientAPIr0.Directory + "/room/{roomAlias}", new RoomDirectoryAddHandler(g))
                .delete(HomeClientAPIr0.Directory + "/room/{roomAlias}", new RoomDirectoryRemoveHandler(g))
                .post(HomeClientAPIr0.Base + "/publicRooms", new PublicChannelListingHandler(g))

                // TODO implement below
                .post(HomeClientAPIr0.Room + "/read_markers", ejaHandler)
                .put(HomeClientAPIr0.Room + "/typing/{userId}", ejaHandler)
                .put(HomeClientAPIr0.UserID + "/account_data/{type}", ejaHandler)
                .put(HomeClientAPIr0.UserID + "/rooms/{roomId}/account_data/{type}", ejaHandler)
                .get(HomeClientAPIr0.UserID + "/filter/{filterId}", ejaHandler)
                .post(HomeClientAPIr0.UserID + "/filter", new FiltersPostHandler(g))

                .get(HomeClientAPIr0.Base + "/pushrules/", new PushRulesHandler())
                .put(HomeClientAPIr0.Base + "/presence/**", ejaHandler)
                .get(HomeClientAPIr0.Base + "/voip/turnServer", ejaHandler)
                .get(HomeClientAPIr0.Base + "/joined_groups", ejaHandler)
                .get(HomeClientAPIr0.Base + "/thirdparty/protocols", ejaHandler)

                .post(HomeClientAPIr0.Base + "/keys/query", ejaHandler)
                .post(HomeClientAPIr0.Base + "/keys/upload", ejaHandler)

                // Not in the spec, but requested by some clients
                .post(HomeClientAPIr0.Base + "/publicised_groups", new UserSearchPublicisedGroups(g))
        ;
    }

}
