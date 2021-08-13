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
import io.kamax.gridify.server.core.channel.ChannelLookup;
import io.kamax.gridify.server.exception.ObjectNotFoundException;
import io.kamax.gridify.server.http.handler.Exchange;
import io.kamax.gridify.server.network.grid.core.ChannelAlias;
import io.kamax.gridify.server.network.grid.http.handler.grid.GridApiHandler;
import io.kamax.gridify.server.network.grid.http.handler.grid.data.io.ChannelLookupResponse;
import io.kamax.gridify.server.util.GsonUtil;

import java.util.HashSet;

public class ChannelDirectoryLookupHandler extends GridApiHandler {

    private final GridifyServer g;

    public ChannelDirectoryLookupHandler(GridifyServer g) {
        this.g = g;
    }

    @Override
    protected void handle(Exchange exchange) {
        ServerSession s = g.overGrid().forData().asServer(exchange.authenticate());

        JsonObject body = exchange.parseJsonObject();
        String aliasRaw = GsonUtil.getStringOrThrow(body, "alias");
        ChannelAlias cAlias = ChannelAlias.parse(aliasRaw);
        ChannelLookup lookup = s.lookup(cAlias).orElseThrow(() -> new ObjectNotFoundException("Channel alias", cAlias.full()));

        ChannelLookupResponse response = new ChannelLookupResponse();
        response.setId(lookup.getId().full());
        response.setServers(new HashSet<>());
        lookup.getServers().forEach(id -> response.getServers().add(id.full()));

        exchange.respondJson(response);
    }

}
