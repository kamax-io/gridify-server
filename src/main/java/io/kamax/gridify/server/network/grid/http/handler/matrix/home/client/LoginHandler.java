/*
 * Gridify Server
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

package io.kamax.gridify.server.network.grid.http.handler.matrix.home.client;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.core.UserSession;
import io.kamax.gridify.server.exception.UnauthenticatedException;
import io.kamax.gridify.server.http.handler.Exchange;
import io.kamax.gridify.server.network.grid.ProtocolMapper;
import io.kamax.gridify.server.network.matrix.http.handler.home.client.ClientApiHandler;
import io.kamax.gridify.server.network.matrix.http.json.UIAuthJson;
import io.kamax.gridify.server.util.GsonUtil;
import org.apache.commons.lang3.RandomStringUtils;

public class LoginHandler extends ClientApiHandler {

    private final GridifyServer g;

    public LoginHandler(GridifyServer g) {
        this.g = g;
    }

    @Override
    protected void handle(Exchange exchange) {
        try {
            JsonObject credentials = exchange.parseJsonObject();
            JsonObject gCreds = ProtocolMapper.m2gCredentials(credentials);
            UserSession session = g.overGrid().vHost(exchange.requireHost()).forData().asClient().login(gCreds);

            JsonObject reply = new JsonObject();
            reply.addProperty("user_id", "@" + session.getUser().getId() + ":" + exchange.requireHost());
            reply.addProperty("access_token", session.getAccessToken());
            reply.addProperty("device_id", RandomStringUtils.randomAlphanumeric(8));

            // Required for some clients who fail if not present, even if not mandatory and deprecated.
            // https://github.com/Nheko-Reborn/mtxclient/issues/7
            reply.addProperty("home_server", exchange.requireHost());

            exchange.respondJson(reply);
        } catch (UnauthenticatedException e) {
            UIAuthJson session = ProtocolMapper.g2m(e.getSession());
            JsonObject body = GsonUtil.makeObj(session);
            body.addProperty("errcode", "M_UNAUTHORIZED");
            body.addProperty("error", e.getMessage());
            exchange.respond(401, body);
        }
    }

}
