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

package io.kamax.grid.gridepo.network.matrix.http.handler.home.client;

import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.core.SyncOptions;
import io.kamax.grid.gridepo.http.handler.Exchange;
import io.kamax.grid.gridepo.network.matrix.core.base.UserSession;
import io.kamax.grid.gridepo.network.matrix.http.handler.AuthenticatedClientApiHandler;
import io.kamax.grid.gridepo.network.matrix.http.json.SyncResponse;
import org.apache.commons.lang3.StringUtils;

public class SyncHandler extends AuthenticatedClientApiHandler {

    public SyncHandler(Gridepo g) {
        super(g);
    }

    @Override
    protected void handle(UserSession session, Exchange ex) {
        String since = StringUtils.defaultIfBlank(ex.getQueryParameter("since"), "");

        SyncOptions options = new SyncOptions();
        options.setToken(since);

        String timeout = ex.getQueryParameter("timeout");
        if (StringUtils.isNotEmpty(timeout)) {
            try {
                options.setTimeout(Long.parseLong(timeout));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Timeout is not a valid integer: " + timeout);
            }
        }

        SyncResponse data = session.sync(options);
        ex.respondJson(data);
    }

}
