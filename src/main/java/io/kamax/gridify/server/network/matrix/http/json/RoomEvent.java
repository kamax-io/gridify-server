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

package io.kamax.gridify.server.network.matrix.http.json;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.core.channel.event.ChannelEvent;
import io.kamax.gridify.server.util.GsonUtil;

import java.util.Objects;

public class RoomEvent {

    public static RoomEvent make(ChannelEvent ev) {
        RoomEvent rEv = GsonUtil.fromJson(ev.getData(), RoomEvent.class);
        rEv.setEventId(ev.getId());
        return rEv;
    }

    private transient String channelId;

    private String eventId;
    private String type;
    private String roomId;
    private Long originServerTs;
    private String sender;
    private String stateKey;
    private JsonObject unsigned;
    private Object content;
    private JsonObject grid;

    public JsonObject toJson() {
        return GsonUtil.makeObj(this);
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public Long getOriginServerTs() {
        return originServerTs;
    }

    public void setOriginServerTs(Long originServerTs) {
        this.originServerTs = originServerTs;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getStateKey() {
        return stateKey;
    }

    public void setStateKey(String stateKey) {
        this.stateKey = stateKey;
    }

    public JsonObject getUnsigned() {
        if (Objects.isNull(unsigned)) {
            unsigned = new JsonObject();
        }

        return unsigned;
    }

    public void setUnsigned(JsonObject unsigned) {
        this.unsigned = unsigned;
    }

    public Object getContent() {
        return content;
    }

    public void setContent(Object content) {
        this.content = content;
    }

    public JsonObject getGrid() {
        return grid;
    }

    public void setGrid(JsonObject grid) {
        this.grid = grid;
    }

}
