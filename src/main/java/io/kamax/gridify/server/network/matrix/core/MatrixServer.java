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

package io.kamax.gridify.server.network.matrix.core;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.core.event.EventStreamer;
import io.kamax.gridify.server.core.identity.User;
import io.kamax.gridify.server.core.signal.SignalBus;
import io.kamax.gridify.server.network.matrix.core.base.ServerSession;
import io.kamax.gridify.server.network.matrix.core.crypto.MatrixDomainCryptopher;
import io.kamax.gridify.server.network.matrix.core.domain.MatrixDomain;
import io.kamax.gridify.server.network.matrix.core.domain.MatrixDomainConfig;
import io.kamax.gridify.server.network.matrix.core.federation.FederationPusher;
import io.kamax.gridify.server.network.matrix.core.room.RoomDirectory;
import io.kamax.gridify.server.network.matrix.core.room.RoomManager;
import org.apache.commons.lang3.StringUtils;

import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;

public interface MatrixServer {

    MatrixDomain id();

    String getDomain();

    default boolean isLocal(UserID uId) {
        return StringUtils.equals(getDomain(), uId.network());
    }

    MatrixServerImplementation getImplementation();

    Set<String> getRoomVersions();

    MatrixDomainConfig getConfig();

    void setConfig(MatrixDomainConfig cfg);

    void updateConfig(Consumer<MatrixDomainConfig> c);

    MatrixCore core();

    MatrixDomainCryptopher crypto();

    RoomManager roomMgr();

    RoomDirectory roomDir();

    EventStreamer getStreamer();

    SignalBus getBus();

    FederationPusher getFedPusher();

    Queue<JsonObject> getCommandResponseQueue(String userId);

    MatrixDataClient asClient();

    MatrixDataServer asServer();

    ServerSession asServer(String remoteDomain);

    boolean isStopping();

    boolean isAdmin(User u);

}
