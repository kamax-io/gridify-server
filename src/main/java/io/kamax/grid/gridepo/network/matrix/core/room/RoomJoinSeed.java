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

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;

import java.util.List;
import java.util.stream.Collectors;

public class RoomJoinSeed {

    private String domain;
    private List<JsonObject> authChain;
    private List<JsonObject> state;

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public List<JsonObject> getAuthChain() {
        return authChain;
    }

    public void setAuthChain(List<JsonObject> authChain) {
        this.authChain = authChain;
    }

    public List<JsonObject> getState() {
        return state;
    }

    public void setState(List<JsonObject> state) {
        this.state = state;
    }

    public void setState(RoomState state) {
        setState(state.getEvents().stream().map(ChannelEvent::getData).collect(Collectors.toList()));
    }

}
