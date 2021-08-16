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

package io.kamax.gridify.server.network.grid.http.handler.grid.data;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.core.ServerSession;
import io.kamax.gridify.server.core.channel.state.ChannelEventAuthorization;
import io.kamax.gridify.server.http.handler.Exchange;
import io.kamax.gridify.server.network.grid.http.handler.grid.GridApiHandler;
import io.kamax.gridify.server.util.GsonUtil;

import java.util.List;

public class DoPushHandler extends GridApiHandler {

    private final GridifyServer g;

    public DoPushHandler(GridifyServer g) {
        this.g = g;
    }

    @Override
    protected void handle(Exchange exchange) {
        ServerSession s = g.overGrid().vHost(exchange.requireHost()).forData().asServer(exchange.authenticate());

        JsonObject body = exchange.parseJsonObject();
        List<JsonObject> events = GsonUtil.asList(body, "events", JsonObject.class);

        List<ChannelEventAuthorization> response = s.push(events);

        JsonObject result = new JsonObject();
        response.forEach(evAuth -> {
            if (evAuth.isValid() && evAuth.isAuthorized()) {
                return;
            }

            JsonObject evAuthJson = new JsonObject();
            evAuthJson.addProperty("reason", evAuth.getReason());
            result.add(evAuth.getEventId(), evAuthJson);
        });
        exchange.respond(GsonUtil.makeObj("denied", result));
    }

}
