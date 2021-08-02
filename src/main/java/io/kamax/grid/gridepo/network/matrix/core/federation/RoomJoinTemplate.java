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

package io.kamax.grid.gridepo.network.matrix.core.federation;

import com.google.gson.JsonObject;

public class RoomJoinTemplate {

    public RoomJoinTemplate() {
        // noop
    }

    public RoomJoinTemplate(String roomVersion, JsonObject event) {
        setRoomVersion(roomVersion);
        setEvent(event);
    }

    private transient String origin;
    private transient String roomId;
    private transient String userId;
    private String roomVersion;
    private JsonObject event;

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRoomVersion() {
        return roomVersion;
    }

    public void setRoomVersion(String roomVersion) {
        this.roomVersion = roomVersion;
    }

    public JsonObject getEvent() {
        return event;
    }

    public void setEvent(JsonObject event) {
        this.event = event;
    }

}
