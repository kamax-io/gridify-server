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
import io.kamax.gridify.server.core.GridType;
import io.kamax.gridify.server.core.ServerSession;
import io.kamax.gridify.server.core.UserSession;
import io.kamax.gridify.server.core.auth.Credentials;
import io.kamax.gridify.server.core.auth.UIAuthSession;
import io.kamax.gridify.server.core.auth.UIAuthStage;
import io.kamax.gridify.server.core.channel.ChannelDirectory;
import io.kamax.gridify.server.core.channel.ChannelManager;
import io.kamax.gridify.server.core.federation.DataServerManager;
import io.kamax.gridify.server.core.identity.GenericThreePid;
import io.kamax.gridify.server.core.identity.ThreePid;
import io.kamax.gridify.server.core.identity.User;
import io.kamax.gridify.server.exception.InternalServerError;
import io.kamax.gridify.server.exception.UnauthenticatedException;
import io.kamax.gridify.server.util.GsonUtil;
import io.kamax.gridify.server.util.KxLog;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class SimpleGridDataServer implements GridDataServer {

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    private final GridServer g;
    private final ChannelManager chMgr;
    private final ChannelDirectory chDir;

    public SimpleGridDataServer(GridServer g) {
        this.g = g;
        chMgr = new ChannelManager(this);
        chDir = new ChannelDirectory(this);
    }

    @Override
    public GridServer server() {
        return g;
    }

    @Override
    public DataServerManager dataServerMgr() {
        return g.dataSrvMgr();
    }

    @Override
    public ChannelManager getChannelManager() {
        return chMgr;
    }

    @Override
    public ChannelDirectory getChannelDirectory() {
        return chDir;
    }

    @Override
    public ServerSession asServer(String srvId) {
        return new ServerSession(g);
    }

    @Override
    public GridDataServerClient asClient() {
        return new GridDataServerClient() {

            @Override
            public String getDomain() {
                return g.getDomain();
            }

            @Override
            public User register(String username, String password) {
                User user = server().gridify().getIdentity().createUserWithKey();
                user.addThreePid(new GenericThreePid(GridType.id().local().username(), username));
                user.addCredentials(new Credentials("g.auth.id.password", password));
                return user;
            }

            @Override
            public User login(UIAuthSession auth, String stageId) {
                if (!auth.isAuthenticated()) {
                    throw new UnauthenticatedException(auth);
                }

                Set<String> idStages = new HashSet<>(auth.getCompletedStages());
                if (idStages.isEmpty()) {
                    throw new InternalServerError("No ID-based authentication was completed, cannot identify the user");
                }

                UIAuthStage stage = auth.getStage(stageId);

                ThreePid uid = stage.getUid();
                return g.gridify().getIdentity().findUser(uid).orElseGet(() -> {
                    User newUsr = g.gridify().getIdentity().createUserWithKey();
                    newUsr.linkToStoreId(uid);
                    return newUsr;
                });
            }

            @Override
            public boolean isLocal(UserID uId) {
                return false;
            }

            @Override
            public UserSession withToken(String token) {
                User u = g.gridify().validateSessionToken(token);
                return new UserSession(SimpleGridDataServer.this, "grid", u);
            }

            @Override
            public UIAuthSession login() {
                return g.gridify().login("grid");
            }

            @Override
            public UserSession login(User user) {
                return withToken(g.gridify().createSessionToken("grid", user));
            }

            @Override
            public UserSession login(UIAuthSession session) {
                User u = g.gridify().login(session, GridType.of("auth.id.password"));
                return login(u);
            }

            @Override
            public UserSession login(JsonObject doc) {
                Optional<String> sessionId = GsonUtil.findString(doc, "session");
                UIAuthSession session;
                if (sessionId.isPresent()) {
                    session = g.gridify().getAuth().getSession(sessionId.get());
                } else {
                    session = login();
                }

                session.complete(doc);
                return login(session);
            }

            @Override
            public UserSession login(String username, String password) {
                JsonObject id = new JsonObject();
                id.addProperty("type", GridType.id().internal().getId());
                id.addProperty("value", username);
                JsonObject doc = new JsonObject();
                doc.addProperty("type", GridType.of("auth.id.password"));
                doc.addProperty("password", password);
                doc.add("identifier", id);
                return login(doc);
            }
        };
    }

}
