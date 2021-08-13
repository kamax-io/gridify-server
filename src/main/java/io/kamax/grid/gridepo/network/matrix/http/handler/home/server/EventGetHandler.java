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

package io.kamax.grid.gridepo.network.matrix.http.handler.home.server;

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.http.handler.Exchange;
import io.kamax.grid.gridepo.network.matrix.core.base.ServerSession;
import io.kamax.grid.gridepo.util.GsonUtil;

import java.time.Instant;

public class EventGetHandler extends AuthenticatedServerApiHandler {

    public EventGetHandler(Gridepo g) {
        super(g);
    }

    @Override
    protected void handle(ServerSession session, Exchange ex) {
        String eventId = ex.getPathVariable("eventId");
        JsonObject evDoc = session.getEvent(eventId);

        JsonObject body = new JsonObject();
        body.addProperty("origin", session.getVhost());
        body.addProperty("origin_server_ts", Instant.now().toEpochMilli());
        body.add("pdus", GsonUtil.asArray(evDoc));
        ex.respond(body);
    }

}
