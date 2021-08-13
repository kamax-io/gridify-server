/*
 * Copyright (C) 2019 Kamax Sarl
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

package io.kamax.gridify.server.network.grid.http.handler.grid.identity;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.core.UserSession;
import io.kamax.gridify.server.core.auth.UIAuthSession;
import io.kamax.gridify.server.http.handler.Exchange;
import io.kamax.gridify.server.network.grid.http.handler.grid.GridApiHandler;
import io.kamax.gridify.server.util.GsonUtil;

public class LoginPostHandler extends GridApiHandler {

    private GridifyServer g;

    public LoginPostHandler(GridifyServer g) {
        this.g = g;
    }

    @Override
    protected void handle(Exchange exchange) {
        JsonObject reqBody = exchange.parseJsonObject();
        String sId = GsonUtil.getStringOrThrow(reqBody, "session");
        UIAuthSession authSession = g.getAuth().getSession(sId);
        UserSession userSession = g.overGrid().forData().asClient().login(authSession);
        exchange.respond(GsonUtil.makeObj("token", userSession.getAccessToken()));
    }

}
