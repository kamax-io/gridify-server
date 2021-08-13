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

import io.kamax.gridify.server.core.channel.event.ChannelEvent;

import java.util.List;
import java.util.stream.Collectors;

public class EventStateIds {

    public static EventStateIds getIds(EventState state) {
        EventStateIds obj = new EventStateIds();
        obj.authChainIds = state.getAuthChain().stream().map(ChannelEvent::getId).collect(Collectors.toList());
        obj.stateIds = state.getState().stream().map(ChannelEvent::getId).collect(Collectors.toList());
        return obj;
    }

    private List<String> authChainIds;
    private List<String> stateIds;

    public List<String> getAuthChainIds() {
        return authChainIds;
    }

    public void setAuthChainIds(List<String> authChainIds) {
        this.authChainIds = authChainIds;
    }

    public List<String> getStateIds() {
        return stateIds;
    }

    public void setStateIds(List<String> stateIds) {
        this.stateIds = stateIds;
    }

}
