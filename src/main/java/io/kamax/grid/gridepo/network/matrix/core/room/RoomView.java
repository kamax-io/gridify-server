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

package io.kamax.grid.gridepo.network.matrix.core.room;

import io.kamax.grid.gridepo.core.channel.state.ChannelState;
import io.kamax.grid.gridepo.exception.NotImplementedException;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public class RoomView {

    private final String eventId;
    private final RoomState state;

    public RoomView(String eventId, ChannelState state) {
        this(eventId, new RoomState(state));
    }

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

    public List<String> getAllServers() {
        throw new NotImplementedException();
    }

    public List<String> getJoinedServers() {
        throw new NotImplementedException();
    }

    public boolean isServerJoined(String domain) {
        return getJoinedServers().stream().anyMatch(v -> StringUtils.equals(v, domain));
    }

}
