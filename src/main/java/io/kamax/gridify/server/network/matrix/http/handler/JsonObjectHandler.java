/*
 * Gridify Server
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

package io.kamax.gridify.server.network.matrix.http.handler;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.http.handler.Exchange;
import io.kamax.gridify.server.network.matrix.http.handler.home.client.ClientApiHandler;
import io.kamax.gridify.server.util.GsonUtil;

public class JsonObjectHandler extends ClientApiHandler {

    private final GridifyServer g;
    private final boolean withAuth;
    private final String body;

    public JsonObjectHandler(GridifyServer g, boolean withAuth, JsonObject body) {
        this.g = g;
        this.withAuth = withAuth;
        this.body = GsonUtil.toJson(body);
    }

    public JsonObjectHandler(GridifyServer g, boolean withAuth, String objKey, Object objValue) {
        this(g, withAuth, GsonUtil.makeObj(objKey, objValue));
    }

    @Override
    protected void handle(Exchange exchange) {
        if (withAuth) {
            getSession(g, exchange);
        }

        exchange.respondJson(body);
    }

}
