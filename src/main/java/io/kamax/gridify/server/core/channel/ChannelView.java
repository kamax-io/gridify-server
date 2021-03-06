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

package io.kamax.gridify.server.core.channel;

import io.kamax.gridify.server.core.channel.event.BareMemberEvent;
import io.kamax.gridify.server.core.channel.event.ChannelEventType;
import io.kamax.gridify.server.core.channel.state.ChannelState;
import io.kamax.gridify.server.network.grid.core.ServerID;
import io.kamax.gridify.server.util.GsonUtil;

import java.util.Set;
import java.util.stream.Collectors;

public class ChannelView {

    private ServerID origin;
    private String head;
    private ChannelState state;

    public ChannelView(ServerID origin) {
        this(origin, null, ChannelState.empty());
    }

    public ChannelView(ServerID origin, String head, ChannelState state) {
        this.origin = origin;
        this.head = head;
        this.state = state;
    }

    // FIXME this does not make sense and HEAD should be moved to timeline
    // Only used as a fast hack until v0.1
    public String getHead() {
        return head;
    }

    public ChannelState getState() {
        return state;
    }

    public Set<ServerID> getAllServers() {
        return getServers(true);
    }

    public Set<ServerID> getOtherServers() {
        return getServers(false);
    }

    public Set<ServerID> getServers(boolean includeSelf) {
        return getState().getEvents().stream()
                .filter(ev -> ChannelEventType.Member.match(ev.getBare().getType()))
                .filter(bEv -> {
                    BareMemberEvent ev = GsonUtil.fromJson(bEv.getData(), BareMemberEvent.class);
                    return ChannelMembership.Join.match(ev.getContent().getAction());
                })
                .map(ev -> ServerID.parse(ev.getOrigin()))
                .filter(id -> !origin.equals(id) || includeSelf)
                .collect(Collectors.toSet());
    }

    public boolean isJoined(ServerID id) {
        // TODO fix, not restricted to joined servers
        return getAllServers().contains(id);
    }

}
