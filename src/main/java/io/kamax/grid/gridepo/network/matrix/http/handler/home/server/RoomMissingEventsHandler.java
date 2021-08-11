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

import java.util.List;
import java.util.stream.Collectors;

public class RoomMissingEventsHandler extends AuthenticatedServerApiHandler {

    public RoomMissingEventsHandler(Gridepo g) {
        super(g);
    }

    @Override
    protected void handle(ServerSession session, Exchange ex) {
        String roomId = ex.getPathVariable("roomId");
        JsonObject body = ex.parseJsonObject();

        List<String> earliestEvents = GsonUtil.asList(body, "earliest_events", String.class);
        List<String> latestEvents = GsonUtil.asList(body, "latest_events", String.class);

        long limit = 10L;
        if (body.has("limit")) {
            limit = GsonUtil.getLong(body, "limit");
        }

        long minDepth = 0L;
        if (body.has("min_depth")) {
            minDepth = GsonUtil.getLong(body, "min_depth");
        }

        List<ChannelEvent> events = session.getEventsTree(roomId, latestEvents, earliestEvents, limit, minDepth);
        List<JsonObject> docs = events.stream().map(ChannelEvent::getData).collect(Collectors.toList());
        ex.respondJson(GsonUtil.makeObj("events", GsonUtil.asArray(docs)));
    }

}