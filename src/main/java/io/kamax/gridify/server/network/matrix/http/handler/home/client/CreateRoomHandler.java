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

package io.kamax.gridify.server.network.matrix.http.handler.home.client;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.http.Exchange;
import io.kamax.gridify.server.network.matrix.core.base.UserSession;
import io.kamax.gridify.server.network.matrix.core.room.Room;
import io.kamax.gridify.server.util.GsonUtil;

public class CreateRoomHandler extends AuthenticatedClientApiHandler {

    public CreateRoomHandler(GridifyServer g) {
        super(g);
    }

    @Override
    protected void handle(UserSession session, Exchange ex) {
        JsonObject body = ex.parseJsonObject();
        Room r = session.createRoom(body);
        ex.respondJson(GsonUtil.makeObj("room_id", r.getId()));
    }

}
