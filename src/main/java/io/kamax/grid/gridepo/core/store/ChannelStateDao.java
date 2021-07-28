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

package io.kamax.grid.gridepo.core.store;

import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;

import java.util.ArrayList;
import java.util.List;

public class ChannelStateDao {

    private long sid;
    private List<ChannelEvent> events = new ArrayList<>();

    public ChannelStateDao(long sid, List<ChannelEvent> events) {
        this.sid = sid;
        this.events = events;
    }

    public long getSid() {
        return sid;
    }

    public void setSid(long sid) {
        this.sid = sid;
    }

    public List<ChannelEvent> getEvents() {
        return events;
    }

    public void setEventSids(List<ChannelEvent> events) {
        this.events = events;
    }

}
