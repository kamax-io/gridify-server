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

package io.kamax.gridify.server.network.grid.http.handler.grid.identity;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.core.identity.GenericThreePid;
import io.kamax.gridify.server.core.identity.ThreePid;
import io.kamax.gridify.server.core.identity.User;
import io.kamax.gridify.server.exception.ObjectNotFoundException;
import io.kamax.gridify.server.http.Exchange;
import io.kamax.gridify.server.network.grid.http.handler.grid.GridApiHandler;
import io.kamax.gridify.server.util.GsonUtil;

public class UserLookupHandler extends GridApiHandler {

    private final GridifyServer g;

    public UserLookupHandler(GridifyServer g) {
        this.g = g;
    }

    @Override
    protected void handle(Exchange exchange) {
        JsonObject id = exchange.parseJsonObject("identifier");
        String type = GsonUtil.getStringOrThrow(id, "type"); // TODO use shared structure with client
        String value = GsonUtil.getStringOrThrow(id, "value"); // TODO use shared structure with client

        ThreePid tpid = new GenericThreePid(type, value);
        User u = g.getIdentity().findUser(tpid).orElseThrow(() -> new ObjectNotFoundException("User with 3PID " + tpid));

        exchange.respond(GsonUtil.makeObj("id", u.getNetworkId("grid")));
    }

}
