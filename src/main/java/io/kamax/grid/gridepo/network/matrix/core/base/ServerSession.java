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
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelEventAuthorization;
import io.kamax.grid.gridepo.exception.ForbiddenException;
import io.kamax.grid.gridepo.network.matrix.core.IncompatibleRoomVersionException;
import io.kamax.grid.gridepo.network.matrix.core.MatrixCore;
import io.kamax.grid.gridepo.network.matrix.core.federation.RoomJoinTemplate;
import io.kamax.grid.gridepo.network.matrix.core.room.*;
import io.kamax.grid.gridepo.util.GsonUtil;
import io.kamax.grid.gridepo.util.KxLog;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ServerSession {

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    private final MatrixCore core;

    private final String domain;
    private final String remote;

    public ServerSession(MatrixCore core, String domain, String remote) {
        this.core = core;
        this.domain = domain;
        this.remote = remote;
    }

    public Optional<RoomLookup> lookupRoomAlias(String roomAlias) {
        RoomAlias alias = RoomAlias.parse(roomAlias);
        return core.roomDir().lookup(remote, alias, false);
    }

    public RoomJoinTemplate makeJoin(String roomId, String userId, Set<String> roomVersions) {
        Room r = core.roomMgr().get(roomId);
        String roomVersion = r.getVersion();
        if (!roomVersions.contains(roomVersion)) {
            throw new IncompatibleRoomVersionException();
        }
        JsonObject template = r.makeJoinTemplate(userId);
        log.debug("Built join template for User {} in Room {} : {}", userId, roomId, GsonUtil.getPrettyForLog(template));
        return new RoomJoinTemplate(roomVersion, template);
    }

    public RoomJoinSeed sendJoin(String roomId, String userId, JsonObject eventDoc) {
        Room r = core.roomMgr().get(roomId);
        ChannelEventAuthorization auth = r.offer(remote, eventDoc);
        if (!auth.isAuthorized()) {
            throw new ForbiddenException(auth.getReason());
        }

        RoomState state = r.getFullState(auth.getEventId());
        List<ChannelEvent> authChain = r.getAuthChain(state);

        RoomJoinSeed seed = new RoomJoinSeed();
        seed.setDomain(domain);
        seed.setState(state);
        seed.makeAuthChain(authChain);

        return seed;
    }

}
