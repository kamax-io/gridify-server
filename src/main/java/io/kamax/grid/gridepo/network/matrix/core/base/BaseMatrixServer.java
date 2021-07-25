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

package io.kamax.grid.gridepo.network.matrix.core.base;

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.core.GridType;
import io.kamax.grid.gridepo.core.UserSession;
import io.kamax.grid.gridepo.core.auth.UIAuthSession;
import io.kamax.grid.gridepo.core.identity.GenericThreePid;
import io.kamax.grid.gridepo.core.identity.User;
import io.kamax.grid.gridepo.network.matrix.core.MatrixDataClient;
import io.kamax.grid.gridepo.network.matrix.core.MatrixDataServer;
import io.kamax.grid.gridepo.network.matrix.core.MatrixIdentityServer;
import io.kamax.grid.gridepo.network.matrix.core.MatrixServer;
import io.kamax.grid.gridepo.util.GsonUtil;

import java.util.Optional;

public class BaseMatrixServer implements MatrixServer, MatrixDataClient, MatrixDataServer {

    private Gridepo g;
    private MatrixIdentityServer is;

    private String domain;

    public BaseMatrixServer(Gridepo g, String domain) {
        this.g = g;
        this.domain = domain;

        is = new BaseMatrixIdentityServer(g);
    }

    @Override
    public MatrixIdentityServer forIdentity() {
        return is;
    }

    @Override
    public MatrixDataClient asClient() {
        return this;
    }

    @Override
    public MatrixDataServer asServer() {
        return this;
    }

    @Override
    public UserSession withToken(String token) {
        return g.withToken(token);
    }

    @Override
    public User register(String username, String password) {
        User u = g.register(username, password);
        u.addThreePid(new GenericThreePid(GridType.id().make("net.matrix"), "@" + username + ":" + domain));
        return u;
    }

    @Override
    public UserSession login(User u) {
        return g.login("matrix", u);
    }

    @Override
    public UserSession login(String username, String password) {
        UIAuthSession session = g.login("matrix");

        JsonObject id = new JsonObject();
        id.addProperty("type", "m.id.user");
        id.addProperty("user", username);
        JsonObject doc = new JsonObject();
        doc.addProperty("type", "m.login.password");
        doc.addProperty("password", password);
        doc.add("identifier", id);

        session.complete(doc);
        return g.login(session);
    }

    @Override
    public UserSession login(JsonObject credentials) {
        Optional<String> sessionId = GsonUtil.findString(credentials, "session");
        UIAuthSession session;
        if (sessionId.isPresent()) {
            session = g.getAuth().getSession(sessionId.get());
        } else {
            session = g.login("matrix");
        }

        session.complete(credentials);
        return g.login(session);
    }

}
