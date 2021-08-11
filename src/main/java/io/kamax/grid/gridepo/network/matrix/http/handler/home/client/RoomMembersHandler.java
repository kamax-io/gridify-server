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

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.http.handler.Exchange;
import io.kamax.grid.gridepo.network.matrix.core.base.UserSession;
import io.kamax.grid.gridepo.network.matrix.http.json.RoomEvent;
import io.kamax.grid.gridepo.util.GsonUtil;

import java.util.List;
import java.util.stream.Collectors;

public class RoomMembersHandler extends AuthenticatedClientApiHandler {

    public RoomMembersHandler(Gridepo g) {
        super(g);
    }

    @Override
    protected void handle(UserSession session, Exchange ex) {
        String roomId = ex.getPathVariable("roomId");
        List<JsonObject> roomEvents = session.getRoom(roomId).getView().getState().getMembers().stream()
                .map(RoomEvent::make)
                .map(RoomEvent::toJson)
                .collect(Collectors.toList());

        ex.respond(GsonUtil.makeObj("chunck", GsonUtil.asArray(roomEvents)));
    }

}
