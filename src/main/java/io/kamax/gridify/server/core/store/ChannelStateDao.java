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

package io.kamax.gridify.server.core.store;

import io.kamax.gridify.server.core.channel.event.ChannelEvent;

import java.util.List;

public class ChannelStateDao {

    private Long sid;
    private boolean trusted = false;
    private boolean complete = false;
    private boolean finall = false; // because final is a reserved keyword
    private List<ChannelEvent> events;

    public ChannelStateDao(Long sid, List<ChannelEvent> events) {
        this.sid = sid;
        this.events = events;
    }

    public Long getSid() {
        return sid;
    }

    public void setSid(Long sid) {
        this.sid = sid;
    }

    public boolean isTrusted() {
        return trusted;
    }

    public void setTrusted(boolean trusted) {
        this.trusted = trusted;
    }

    public boolean isComplete() {
        return complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    public boolean isFinal() {
        return finall;
    }

    public void setFinal(boolean finall) {
        this.finall = finall;
    }

    public List<ChannelEvent> getEvents() {
        return events;
    }

    public void setEventSids(List<ChannelEvent> events) {
        this.events = events;
    }

}
