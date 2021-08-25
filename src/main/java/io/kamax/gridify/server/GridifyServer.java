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

package io.kamax.gridify.server;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.config.GridifyConfig;
import io.kamax.gridify.server.core.UserSession;
import io.kamax.gridify.server.core.admin.AdminCore;
import io.kamax.gridify.server.core.auth.AuthService;
import io.kamax.gridify.server.core.auth.UIAuthSession;
import io.kamax.gridify.server.core.crypto.Cryptopher;
import io.kamax.gridify.server.core.crypto.PublicKey;
import io.kamax.gridify.server.core.event.EventStreamer;
import io.kamax.gridify.server.core.identity.IdentityManager;
import io.kamax.gridify.server.core.identity.User;
import io.kamax.gridify.server.core.signal.SignalBus;
import io.kamax.gridify.server.core.store.DataStore;
import io.kamax.gridify.server.network.grid.core.GridCore;
import io.kamax.gridify.server.network.matrix.core.MatrixCore;

public interface GridifyServer {

    String getServerId();

    PublicKey getPublicKey();

    boolean isSetup();

    void setup(JsonObject setupDoc);

    void start();

    void stop();

    boolean isStopping();

    GridifyConfig getConfig();

    SignalBus getBus();

    DataStore getStore();

    Cryptopher getCrypto();

    IdentityManager getIdentity();

    EventStreamer getStreamer();

    AuthService getAuth();

    String createSessionToken(String network, User usr);

    User validateSessionToken(String token);

    void destroySessionToken(String token);

    User register(String username, String password);

    UIAuthSession login(String network);

    User login(UIAuthSession auth, String stage);

    @Deprecated
    default UserSession withToken(String token) {
        return overGrid().vHost("").forData().asClient().withToken(token);
    }

    GridCore overGrid();

    MatrixCore overMatrix();

    AdminCore overAdmin();

}
