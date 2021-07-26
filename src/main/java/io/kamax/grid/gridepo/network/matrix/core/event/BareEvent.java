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

package io.kamax.grid.gridepo.network.matrix.core.event;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import io.kamax.grid.gridepo.core.event.EventKey;
import io.kamax.grid.gridepo.util.GsonUtil;

import java.util.ArrayList;
import java.util.List;

public abstract class BareEvent<T> {

    @SerializedName(EventKey.Type)
    private String type;
    @SerializedName(EventKey.Id)
    private String id;
    @SerializedName(EventKey.Timestamp)
    private Long timestamp;
    @SerializedName(EventKey.Origin)
    private String origin;
    @SerializedName(EventKey.Sender)
    private String sender;
    @SerializedName(EventKey.ChannelId)
    private String roomId;
    @SerializedName(EventKey.Scope)
    private String stateKey;
    @SerializedName(EventKey.PrevEvents)
    private List<String> previousEvents;
    @SerializedName(EventKey.Depth)
    private Long depth;
    @SerializedName(EventKey.Content)
    private T content;

    public JsonObject getJson() {
        return GsonUtil.makeObj(this);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setType(RoomEventType type) {
        setType(type.getId());
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getStateKey() {
        return stateKey;
    }

    public void setStateKey(String stateKey) {
        this.stateKey = stateKey;
    }

    public List<String> getPreviousEvents() {
        return previousEvents;
    }

    public void setPreviousEvents(List<String> previousEvents) {
        this.previousEvents = new ArrayList<>(previousEvents);
    }

    public Long getDepth() {
        return depth;
    }

    public void setDepth(long depth) {
        this.depth = depth;
    }

    public T getContent() {
        return content;
    }

    public void setContent(T content) {
        this.content = content;
    }

}