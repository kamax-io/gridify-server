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

package io.kamax.gridify.server.network.matrix.core.room;

import com.google.gson.JsonObject;

import java.util.List;

public class RoomInviteRequest {

    String roomId;
    String roomVersion;
    String eventId;
    List<JsonObject> strippedState;
    JsonObject doc;

    public String getRoomId() {
        return roomId;
    }

    public RoomInviteRequest setRoomId(String roomId) {
        this.roomId = roomId;

        return this;
    }

    public String getRoomVersion() {
        return roomVersion;
    }

    public RoomInviteRequest setRoomVersion(String roomVersion) {
        this.roomVersion = roomVersion;

        return this;
    }

    public String getEventId() {
        return eventId;
    }

    public RoomInviteRequest setEventId(String eventId) {
        this.eventId = eventId;

        return this;
    }

    public List<JsonObject> getStrippedState() {
        return strippedState;
    }

    public RoomInviteRequest setStrippedState(List<JsonObject> strippedState) {
        this.strippedState = strippedState;

        return this;
    }

    public JsonObject getDoc() {
        return doc;
    }

    public RoomInviteRequest setDoc(JsonObject doc) {
        this.doc = doc;

        return this;
    }

}
