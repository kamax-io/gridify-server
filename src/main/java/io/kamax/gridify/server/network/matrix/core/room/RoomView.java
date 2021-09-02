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

import io.kamax.gridify.server.network.matrix.core.event.BareEvent;
import io.kamax.gridify.server.network.matrix.core.event.BareGenericEvent;
import io.kamax.gridify.server.network.matrix.core.event.BareMemberEvent;
import io.kamax.gridify.server.network.matrix.core.event.RoomEventType;
import io.kamax.gridify.server.util.GsonUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.Set;
import java.util.stream.Collectors;

public class RoomView {

    private final String eventId;
    private final RoomState state;

    public RoomView(String eventId, RoomState state) {
        this.eventId = eventId;
        this.state = state;
    }

    public String getEventId() {
        return eventId;
    }

    public RoomState getState() {
        return state;
    }

    public Set<String> getAllServers() {
        return getState().getEvents().stream()
                .map(o -> BareGenericEvent.fromJson(o.getData()).getSender())
                .map(sender -> StringUtils.substringAfter(sender, ":"))
                .collect(Collectors.toSet());
    }

    public Set<String> getJoinedServers() {
        return getState().getEvents().stream()
                .filter(o -> RoomEventType.Member.match(o.getData()))
                .map(o -> GsonUtil.fromJson(o.getData(), BareMemberEvent.class))
                .filter(o -> RoomMembership.Join.match(o.getContent().getMembership()))
                .map(BareEvent::getOrigin)
                .collect(Collectors.toSet());
    }

    public boolean isServerJoined(String domain) {
        return getJoinedServers().stream().anyMatch(v -> StringUtils.equals(v, domain));
    }

}
