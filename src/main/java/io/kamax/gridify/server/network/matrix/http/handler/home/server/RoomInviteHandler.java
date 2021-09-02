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

package io.kamax.gridify.server.network.matrix.http.handler.home.server;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.http.Exchange;
import io.kamax.gridify.server.network.matrix.core.base.ServerSession;
import io.kamax.gridify.server.network.matrix.core.room.RoomInviteRequest;
import io.kamax.gridify.server.util.GsonUtil;

public class RoomInviteHandler extends AuthenticatedServerApiHandler {

    public RoomInviteHandler(GridifyServer g) {
        super(g);
    }

    @Override
    protected void handle(ServerSession session, Exchange ex) {
        JsonObject body = ex.parseJsonObject();

        RoomInviteRequest req = new RoomInviteRequest();
        req.setRoomId(ex.getPathVariable("roomId"));
        req.setEventId(ex.getPathVariable("eventId"));
        req.setRoomVersion(GsonUtil.getStringOrThrow(body, "room_version"));
        req.setDoc(GsonUtil.getObj(body, "event"));
        req.setStrippedState(GsonUtil.tryArrayAsList(body, "invite_room_state"));

        JsonObject signedEvent = session.inviteUser(req);

        ex.respond(GsonUtil.makeObj("event", signedEvent));
    }

}
