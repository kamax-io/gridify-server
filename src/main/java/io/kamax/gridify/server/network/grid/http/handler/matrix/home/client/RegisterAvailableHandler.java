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
import io.kamax.gridify.server.core.GridType;
import io.kamax.gridify.server.core.identity.GenericThreePid;
import io.kamax.gridify.server.core.identity.User;
import io.kamax.gridify.server.exception.ForbiddenException;
import io.kamax.gridify.server.http.Exchange;
import io.kamax.gridify.server.network.matrix.http.handler.home.client.ClientApiHandler;
import io.kamax.gridify.server.util.GsonUtil;

import java.util.Optional;

public class RegisterAvailableHandler extends ClientApiHandler {

    private final GridifyServer g;

    public RegisterAvailableHandler(GridifyServer g) {
        this.g = g;
    }

    @Override
    protected void handle(Exchange exchange) {
        if (!g.getIdentity().canRegister()) {
            throw new ForbiddenException("Registrations are not allowed");
        }

        String username = exchange.getQueryParameter("username");
        Optional<User> u = g.getIdentity().findUser(new GenericThreePid(GridType.id().local().username(), username));
        if (u.isPresent()) {
            throw new IllegalArgumentException("Not available, not allowed, who knows?");
        }

        exchange.respond(GsonUtil.makeObj("available", true));
    }

}
