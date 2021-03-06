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

package io.kamax.gridify.server.network.grid.core;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.core.UserSession;
import io.kamax.gridify.server.core.auth.UIAuthSession;
import io.kamax.gridify.server.core.identity.User;

public interface GridDataServerClient {

    String getDomain();

    User register(String username, String password);

    User login(UIAuthSession auth, String stage);

    boolean isLocal(UserID uId);

    UserSession withToken(String token);

    UIAuthSession login();

    UserSession login(User user);

    UserSession login(UIAuthSession session);

    UserSession login(JsonObject doc);

    UserSession login(String username, String password);

}
