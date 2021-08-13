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

import io.kamax.gridify.server.config.GridifyConfig;
import io.kamax.gridify.server.core.UserSession;
import io.kamax.gridify.server.core.auth.AuthService;
import io.kamax.gridify.server.core.auth.UIAuthSession;
import io.kamax.gridify.server.core.channel.ChannelDirectory;
import io.kamax.gridify.server.core.channel.ChannelManager;
import io.kamax.gridify.server.core.crypto.Cryptopher;
import io.kamax.gridify.server.core.event.EventService;
import io.kamax.gridify.server.core.event.EventStreamer;
import io.kamax.gridify.server.core.federation.DataServerManager;
import io.kamax.gridify.server.core.federation.FederationPusher;
import io.kamax.gridify.server.core.identity.IdentityManager;
import io.kamax.gridify.server.core.identity.User;
import io.kamax.gridify.server.core.signal.SignalBus;
import io.kamax.gridify.server.core.store.DataStore;
import io.kamax.gridify.server.network.grid.core.GridServer;
import io.kamax.gridify.server.network.grid.core.ServerID;
import io.kamax.gridify.server.network.grid.core.UserID;
import io.kamax.gridify.server.network.matrix.core.MatrixCore;
import org.apache.commons.lang3.StringUtils;

public interface GridifyServer {

    void start();

    void stop();

    boolean isStopping();

    GridifyConfig getConfig();

    String getDomain();

    ServerID getOrigin();

    default boolean isOrigin(String sId) {
        return StringUtils.equals(sId, getOrigin().full());
    }

    boolean isLocal(ServerID sId);

    SignalBus getBus();

    DataStore getStore();

    Cryptopher getCrypto();

    IdentityManager getIdentity();

    ChannelManager getChannelManager();

    ChannelDirectory getChannelDirectory();

    EventService getEventService();

    EventStreamer getStreamer();

    DataServerManager getServers();

    FederationPusher getFedPusher();

    AuthService getAuth();

    UserSession withToken(String token);

    String createSessionToken(String network, User usr);

    User validateSessionToken(String token);

    void destroySessionToken(String token);

    User register(String username, String password);

    UIAuthSession login(String network);

    User login(UIAuthSession auth, String stage);

    boolean isLocal(UserID uId);

    GridServer overGrid();

    MatrixCore overMatrix();

}
