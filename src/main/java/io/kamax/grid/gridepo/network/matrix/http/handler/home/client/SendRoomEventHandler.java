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
import io.kamax.grid.gridepo.core.UserSession;
import io.kamax.grid.gridepo.http.handler.Exchange;
import io.kamax.grid.gridepo.network.matrix.http.handler.ClientApiHandler;
import io.kamax.grid.gridepo.util.GsonUtil;

public class SendRoomEventHandler extends ClientApiHandler {

    private final Gridepo g;

    public SendRoomEventHandler(Gridepo g) {
        this.g = g;
    }

    @Override
    protected void handle(Exchange exchange) {
        UserSession session = g.withToken(exchange.getAccessToken());

        String rId = exchange.getPathVariable("roomId");
        String evType = exchange.getPathVariable("type");
        String txnId = exchange.getPathVariable("txnId"); // TODO support

        JsonObject content = exchange.parseJsonObject();
        JsonObject ev = new JsonObject();
        ev.addProperty("type", evType);
        ev.add("content", content);

        String evId = session.send(rId, ev);
        exchange.respondJson(GsonUtil.makeObj("event_id", evId));
    }

}
