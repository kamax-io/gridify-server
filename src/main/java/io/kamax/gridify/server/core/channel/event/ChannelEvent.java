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

package io.kamax.gridify.server.core.channel.event;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.core.channel.state.ChannelEventAuthorization;
import io.kamax.gridify.server.core.store.postgres.ChannelEventMeta;
import io.kamax.gridify.server.network.matrix.core.event.BareGenericEvent;
import io.kamax.gridify.server.util.GsonUtil;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class ChannelEvent {

    public static ChannelEvent forNotFound(long cSid, String evId) {
        return from(cSid, evId, null);
    }

    public static ChannelEvent from(long cSid, JsonObject raw) {
        return from(cSid, null, raw);
    }

    public static ChannelEvent from(long cSid, String id, JsonObject raw) {
        ChannelEvent ev = new ChannelEvent(cSid);
        ev.id = id;
        ev.setData(raw);
        ev.getMeta().setPresent(Objects.nonNull(raw));
        return ev;
    }

    private long cSid;
    private Long lid;
    private Long sid;
    private String id;
    private JsonObject data;
    private ChannelEventMeta meta;
    private JsonObject extra;

    private transient BareGenericEvent asMatrix;
    private transient io.kamax.gridify.server.core.channel.event.BareGenericEvent bare;
    private transient List<String> prevEvents;

    public ChannelEvent() {
        meta = new ChannelEventMeta();
        extra = new JsonObject();
    }

    public ChannelEvent(long cSid) {
        this();

        this.cSid = cSid;
    }

    public ChannelEvent(long cSid, long lid) {
        this(cSid);

        this.lid = lid;
    }

    public ChannelEvent(long cSid, long lid, String id) {
        this(cSid);
        this.lid = lid;
        this.id = id;
    }

    public ChannelEvent(long cSid, long lid, ChannelEventMeta meta) {
        this(cSid, lid);
        this.meta = meta;
    }

    public ChannelEvent(long cSid, long lid, String id, ChannelEventMeta meta) {
        this(cSid, lid, id);
        this.meta = meta;
    }

    public long getChannelSid() {
        return cSid;
    }

    public boolean hasLid() {
        return Objects.nonNull(lid);
    }

    public Long getLid() {
        if (!hasLid()) {
            throw new IllegalStateException();
        }

        return lid;
    }

    public void setLid(long lid) {
        if (hasLid()) {
            throw new IllegalStateException();
        }

        this.lid = lid;
    }

    public boolean hasSid() {
        return Objects.nonNull(sid);
    }

    public Long getSid() {
        return sid;
    }

    public void setSid(Long sid) {
        this.sid = sid;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrigin() {
        return getBare().getOrigin();
    }

    public String getChannelId() {
        return getBare().getChannelId();
    }

    public JsonObject getData() {
        return data;
    }

    public void setData(JsonObject data) {
        this.data = data;
        bare = null;
        asMatrix = null;

        getMeta().setPresent(!Objects.isNull(data));
    }

    public List<String> getPreviousEvents() {
        if (Objects.isNull(prevEvents)) {
            prevEvents = getBare().getPreviousEvents();
        }

        return prevEvents;
    }

    @Deprecated
    public io.kamax.gridify.server.core.channel.event.BareGenericEvent getBare() {
        if (Objects.isNull(bare)) {
            bare = GsonUtil.fromJson(getData(), io.kamax.gridify.server.core.channel.event.BareGenericEvent.class);
        }

        return bare;
    }

    public BareGenericEvent asMatrix() {
        if (Objects.isNull(asMatrix)) {
            asMatrix = GsonUtil.fromJson(getData(), BareGenericEvent.class);
        }

        return asMatrix;
    }

    public ChannelEventMeta getMeta() {
        return meta;
    }

    public JsonObject getExtra() {
        if (Objects.isNull(extra)) {
            extra = new JsonObject();
        }

        return extra;
    }

    public void processed(ChannelEventAuthorization auth) {
        getMeta().setValid(auth.isValid());
        getMeta().setAllowed(auth.isAuthorized());
        getMeta().setValidReason(auth.getReason());
        getMeta().setProcessedOn(Instant.now());
    }

}
