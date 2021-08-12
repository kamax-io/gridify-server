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
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.http.handler.Exchange;
import io.kamax.grid.gridepo.network.matrix.core.base.ServerSession;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.apache.commons.lang.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;

public class RoomBackfillHandler extends AuthenticatedServerApiHandler {

    public RoomBackfillHandler(Gridepo g) {
        super(g);
    }

    @Override
    protected void handle(ServerSession session, Exchange ex) {
        String roomId = ex.getPathVariable("roomId");
        Queue<String> eventIds = ex.getQueryParameters("v");
        String limitRaw = ex.getQueryParameter("limit");

        if (StringUtils.isBlank(limitRaw)) {
            throw new IllegalArgumentException("limit parameter is not given");
        }

        long limit;
        try {
            limit = Long.parseLong(limitRaw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("limit parameter is not a number");
        }

        List<ChannelEvent> events = session.backfill(roomId, eventIds, limit);
        List<JsonObject> docs = events.stream().map(ChannelEvent::getData).collect(Collectors.toList());

        JsonObject body = new JsonObject();
        body.addProperty("origin", session.getVhost());
        body.addProperty("origin_server_ts", Instant.now().toEpochMilli());
        body.add("pdus", GsonUtil.asArray(docs));
        ex.respond(body);
    }

}
