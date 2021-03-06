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

import io.kamax.gridify.server.http.Exchange;
import io.kamax.gridify.server.network.grid.http.handler.grid.GridApiHandler;
import io.kamax.gridify.server.util.GsonUtil;

public class VersionHandler extends GridApiHandler {

    private final String responseBody;

    public VersionHandler() {
        responseBody = GsonUtil.toJson(GsonUtil.makeObj("api", GsonUtil.asArray("v0.0")));
    }

    @Override
    protected void handle(Exchange exchange) {
        exchange.respondJson(responseBody);
    }

}
