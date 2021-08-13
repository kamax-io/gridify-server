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

package io.kamax.gridify.server.core.channel.state;

import io.kamax.gridify.server.core.channel.event.ChannelEvent;

public class ChannelEventAuthorization {

    public static class Builder {

        private ChannelEventAuthorization o;

        public Builder(String eventId) {
            o = new ChannelEventAuthorization();
            o.eventId = eventId;
        }

        public Builder(ChannelEvent ev) {
            this(ev.getId());
            o.event = ev;
        }

        public ChannelEventAuthorization authorize(boolean a, String r) {
            o.valid = true;
            o.authorized = a;
            o.reason = r;
            return o;
        }

        public ChannelEventAuthorization invalid(String reason) {
            o.valid = false;
            o.authorized = false;
            o.reason = reason;
            return o;
        }

        public ChannelEventAuthorization allow() {
            return authorize(true, "");
        }

        public ChannelEventAuthorization deny(String reason) {
            return authorize(false, reason);
        }

        public ChannelEventAuthorization get() {
            return o;
        }

    }

    public static ChannelEventAuthorization from(ChannelEvent ev) {
        return new ChannelEventAuthorization.Builder(ev.getId()).authorize(ev.getMeta().isAllowed(), "Previous processing");
    }

    private String eventId;
    private transient ChannelEvent event;
    private boolean valid;
    private boolean authorized;
    private String reason;

    private ChannelEventAuthorization() {
        // only for builder
    }

    public void setEvent(ChannelEvent event) {
        this.event = event;
    }

    public String getEventId() {
        return eventId;
    }

    public ChannelEvent getEvent() {
        return event;
    }

    public boolean isValid() {
        return valid;
    }

    public boolean isAuthorized() {
        return authorized;
    }

    public String getReason() {
        return reason;
    }

}
