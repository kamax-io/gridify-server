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

package io.kamax.gridify.server.network.matrix.http.handler.home.server;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.http.Exchange;
import io.kamax.gridify.server.network.matrix.core.MatrixServerImplementation;
import io.kamax.gridify.server.util.GsonUtil;

public class VersionHandler extends ServerApiHandler {

    private final GridifyServer g;

    public VersionHandler(GridifyServer g) {
        this.g = g;
    }

    @Override
    protected void handle(Exchange ex) {
        MatrixServerImplementation impl = g.overMatrix().vHost(ex.requireHost()).getImplementation();
        JsonObject body = new JsonObject();
        body.addProperty("name", impl.getName());
        body.addProperty("version", impl.getVersion());
        ex.respond(GsonUtil.makeObj("server", body));
    }

}
