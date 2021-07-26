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
import io.kamax.grid.gridepo.core.SyncData;
import io.kamax.grid.gridepo.core.SyncOptions;
import io.kamax.grid.gridepo.core.channel.TimelineChunk;
import io.kamax.grid.gridepo.core.channel.TimelineDirection;
import io.kamax.grid.gridepo.exception.NotImplementedException;
import io.kamax.grid.gridepo.network.matrix.core.room.Room;
import io.kamax.grid.gridepo.network.matrix.core.room.RoomAliasLookup;
import io.kamax.grid.gridepo.util.KxLog;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.Optional;

public class UserSession {

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    private final Gridepo g;
    private final String vHost;
    private final String userId;
    private final String accessToken;

    public UserSession(Gridepo g, String vHost, String userId, String accessToken) {
        this.g = g;
        this.vHost = vHost;
        this.userId = userId;
        this.accessToken = accessToken;
    }

    public String getUser() {
        return userId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public SyncData sync(SyncOptions options) {
        throw new NotImplementedException();
    }

    public Room createRoom(JsonObject options) {
        return g.overMatrix().roomMgr().createRoom(vHost, userId, options);
    }

    public Room joinRoom(String roomIdOrAlias) {
        throw new NotImplementedException();
    }

    public Room getRoom(String roomId) {
        return g.overMatrix().roomMgr().get(roomId);
    }

    public void leaveRoom(String roomId) {
        throw new NotImplementedException();
    }

    public void inviteToRoom(String roomId, String userId) {
        throw new NotImplementedException();
    }

    public String send(String roomId, JsonObject doc) {
        throw new NotImplementedException();
    }

    public TimelineChunk paginateTimeline(String roomId, String anchor, TimelineDirection direction, long maxEvents) {
        throw new NotImplementedException();
    }

    public void addRoomAlias(String roomAlias, String roomId) {
        throw new NotImplementedException();
    }

    public void removeRoomAlias(String roomAlias) {
        throw new NotImplementedException();
    }

    public Optional<RoomAliasLookup> lookupRoomAlias(String roomAlias) {
        throw new NotImplementedException();
    }

}
