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

package io.kamax.grid.gridepo.network.matrix.http.handler.home.client;

import com.google.gson.JsonArray;
import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.network.grid.http.handler.matrix.home.client.SendRoomStateHandler;
import io.kamax.grid.gridepo.network.matrix.http.HomeClientAPI;
import io.kamax.grid.gridepo.network.matrix.http.HomeClientAPIr0;
import io.kamax.grid.gridepo.network.matrix.http.handler.EmptyJsonObjectHandler;
import io.kamax.grid.gridepo.network.matrix.http.handler.JsonObjectHandler;
import io.kamax.grid.gridepo.network.matrix.http.handler.NotFoundHandler;
import io.kamax.grid.gridepo.network.matrix.http.handler.OptionsHandler;
import io.kamax.grid.gridepo.util.GsonUtil;
import io.undertow.server.RoutingHandler;

public class MatrixHttpEndpointRegister {

    public static void apply(Gridepo g, RoutingHandler handler) {
        SendRoomStateHandler srsHandler = new SendRoomStateHandler(g);

        handler
                // CORS support
                .add("OPTIONS", HomeClientAPI.Base + "/**", new OptionsHandler())

                // Fundamental endpoints
                .get(HomeClientAPI.Base + "/versions", new VersionsHandler())
                .get(HomeClientAPIr0.Base + "/login", new LoginGetHandler(g))
                .post(HomeClientAPIr0.Base + "/login", new LoginHandler(g))
                .get(HomeClientAPIr0.Base + "/sync", new SyncHandler(g))
                .post(HomeClientAPIr0.Base + "/logout", new LogoutHandler(g))

                // Account endpoints
                .get(HomeClientAPIr0.Base + "/register/available", new RegisterAvailableHandler(g))
                .post(HomeClientAPIr0.Base + "/register", new RegisterPostHandler(g))
                .get(HomeClientAPIr0.Base + "/account/3pid", new JsonObjectHandler(
                        g,
                        true,
                        GsonUtil.makeObj("threepids", new JsonArray()))
                )

                // User-related endpoints
                .get(HomeClientAPIr0.Base + "/profile/**", new EmptyJsonObjectHandler(g, false))
                .post(HomeClientAPIr0.Base + "/user_directory/search", new UserDirectorySearchHandler(g))

                // Room management endpoints
                .post(HomeClientAPIr0.Base + "/createRoom", new CreateRoomHandler(g))
                .post(HomeClientAPIr0.Room + "/invite", new RoomInviteHandler(g))
                .post(HomeClientAPIr0.Base + "/join/{roomId}", new RoomJoinHandler(g))
                .post(HomeClientAPIr0.Room + "/leave", new RoomLeaveHandler(g))
                .post(HomeClientAPIr0.Room + "/forget", new EmptyJsonObjectHandler(g, true))
                .get(HomeClientAPIr0.Room + "/initialSync", new RoomInitialSyncHandler(g))

                // Room event endpoints
                .put(HomeClientAPIr0.Room + "/send/{type}/{txnId}", new SendRoomEventHandler(g))
                .put(HomeClientAPIr0.Room + "/state/{type}", srsHandler)
                .put(HomeClientAPIr0.Room + "/state/{type}/{stateKey}", srsHandler)
                .get(HomeClientAPIr0.Room + "/messages", new RoomMessagesHandler(g))

                // Room Directory endpoints
                .get(HomeClientAPIr0.Directory + "/room/{roomAlias}", new RoomAliasLookupHandler(g))
                .put(HomeClientAPIr0.Directory + "/room/{roomAlias}", new RoomDirectoryAddHandler(g))
                .delete(HomeClientAPIr0.Directory + "/room/{roomAlias}", new RoomDirectoryRemoveHandler(g))
                .post(HomeClientAPIr0.Base + "/publicRooms", new PublicChannelListingHandler(g))

                // So various Matrix clients (e.g. Riot) stops spamming us with requests
                // TODO implement
                .post(HomeClientAPIr0.Room + "/read_markers", new EmptyJsonObjectHandler(g, true))
                .put(HomeClientAPIr0.Room + "/typing/{userId}", new EmptyJsonObjectHandler(g, true))
                .put(HomeClientAPIr0.UserID + "/rooms/{roomId}/account_data/{type}", new EmptyJsonObjectHandler(g, true))
                .get(HomeClientAPIr0.UserID + "/filter/{filterId}", new EmptyJsonObjectHandler(g, true))
                .post(HomeClientAPIr0.UserID + "/filter", new FiltersPostHandler(g))

                .get(HomeClientAPIr0.Base + "/pushrules/", new PushRulesHandler())
                .put(HomeClientAPIr0.Base + "/presence/**", new EmptyJsonObjectHandler(g, true))
                .get(HomeClientAPIr0.Base + "/voip/turnServer", new EmptyJsonObjectHandler(g, true))
                .get(HomeClientAPIr0.Base + "/joined_groups", new EmptyJsonObjectHandler(g, true))
                .get(HomeClientAPIr0.Base + "/thirdparty/protocols", new EmptyJsonObjectHandler(g, true))

                .post(HomeClientAPIr0.Base + "/keys/query", new EmptyJsonObjectHandler(g, true))

                .setFallbackHandler(new NotFoundHandler())
                .setInvalidMethodHandler(new NotFoundHandler());
    }

}
