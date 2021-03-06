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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.core.UserSession;
import io.kamax.gridify.server.core.identity.User;
import io.kamax.gridify.server.exception.ForbiddenException;
import io.kamax.gridify.server.exception.NotImplementedException;
import io.kamax.gridify.server.http.Exchange;
import io.kamax.gridify.server.network.grid.ProtocolEventMapper;
import io.kamax.gridify.server.network.grid.core.GridDataServerClient;
import io.kamax.gridify.server.network.matrix.http.handler.home.client.ClientApiHandler;
import io.kamax.gridify.server.util.GsonUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;

import java.util.UUID;

public class RegisterPostHandler extends ClientApiHandler {

    private final GridifyServer g;

    public RegisterPostHandler(GridifyServer g) {
        this.g = g;
    }

    private JsonObject makeFlows() {
        JsonArray stages = new JsonArray();
        stages.add("m.login.password");

        JsonArray flows = new JsonArray();
        flows.add(GsonUtil.makeObj("stages", stages));

        JsonObject body = new JsonObject();
        body.addProperty("session", UUID.randomUUID().toString());
        body.add("flows", flows);

        return body;
    }

    @Override
    protected void handle(Exchange exchange) {
        GridDataServerClient gClient = g.overGrid().vHost(exchange.requireHost()).forData().asClient();

        if (!g.getIdentity().canRegister()) {
            throw new ForbiddenException("Registrations are not allowed");
        }

        String kind = StringUtils.defaultIfEmpty(exchange.getQueryParameter("kind"), "user");
        if (!StringUtils.equals("user", kind)) {
            throw new NotImplementedException("Registration with a kind other than user");
        }

        JsonObject req = exchange.parseJsonObject();
        JsonObject auth = GsonUtil.findObj(req, "auth").orElseGet(JsonObject::new);

        // TODO implement UI Auth session
        /*
        Optional<String> sessionOpt = GsonUtil.findString(auth, "session");
        if (!authOpt.isPresent() || !sessionOpt.isPresent()) {
            exchange.respond(HttpStatus.SC_UNAUTHORIZED, makeFlows());
            return;
        }

        String sessionId = sessionOpt.get();
         */

        String type = GsonUtil.findString(auth, "type").orElse("m.login.password");

        if (!StringUtils.equals("m.login.password", type)) {
            throw new IllegalArgumentException("Type " + type + " is not valid");
        }

        String username = GsonUtil.getStringOrNull(req, "username");
        if (StringUtils.isEmpty(username)) {
            exchange.respond(HttpStatus.SC_UNAUTHORIZED, makeFlows());
            return;
        }

        String password = GsonUtil.getStringOrNull(req, "password");


        User user = g.overGrid().vHost(exchange.requireHost()).forData().asClient().register(username, password);
        UserSession session = gClient.login(user);

        JsonObject reply = new JsonObject();
        reply.addProperty("user_id", ProtocolEventMapper.forUserIdFromGridToMatrix(session.getUser().getGridId().full()));
        reply.addProperty("access_token", session.getAccessToken());
        reply.addProperty("device_id", RandomStringUtils.randomAlphanumeric(8));

        // Required for some clients who fail if not present, even if not mandatory and deprecated.
        // https://github.com/Nheko-Reborn/mtxclient/issues/7
        reply.addProperty("home_server", gClient.getDomain());

        exchange.respondJson(reply);
    }

}
