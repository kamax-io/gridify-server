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

import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.core.UserSession;
import io.kamax.gridify.server.http.Exchange;
import io.kamax.gridify.server.network.matrix.http.handler.home.client.ClientApiHandler;
import org.apache.commons.lang3.StringUtils;

public class RoomDirectoryRemoveHandler extends ClientApiHandler {

    private final GridifyServer g;

    public RoomDirectoryRemoveHandler(GridifyServer g) {
        this.g = g;
    }

    @Override
    protected void handle(Exchange exchange) {
        UserSession s = g.withToken(exchange.getAccessToken());

        String rAlias = exchange.getPathVariable("roomAlias");
        if (StringUtils.isEmpty(rAlias)) {
            throw new IllegalArgumentException("Missing Room Alias in path");
        }

        if (StringUtils.isBlank(rAlias)) {
            throw new IllegalArgumentException("Room alias cannot be blank");
        }

        String cAlias = rAlias.replaceFirst(":", "@");
        s.removeChannelAlias(cAlias);

        exchange.respondJson("{}");
    }

}
