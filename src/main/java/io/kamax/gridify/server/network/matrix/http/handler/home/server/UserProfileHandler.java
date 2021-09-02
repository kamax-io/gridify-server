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

public class UserProfileHandler extends AuthenticatedServerApiHandler {

    public UserProfileHandler(GridifyServer g) {
        super(g);
    }

    @Override
    protected void handle(ServerSession session, Exchange ex) {
        String userId = ex.getQueryParameter("user_id");
        JsonObject profile = session.getUserProfile(userId);
        ex.respond(profile);
    }

}
