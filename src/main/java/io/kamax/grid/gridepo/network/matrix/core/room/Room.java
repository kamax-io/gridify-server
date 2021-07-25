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
import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.core.channel.state.ChannelEventAuthorization;
import io.kamax.grid.gridepo.exception.NotImplementedException;
import io.kamax.grid.gridepo.network.matrix.core.event.BareEvent;
import io.kamax.grid.gridepo.network.matrix.core.room.algo.RoomAlgo;

import java.util.List;

public class Room {

    private final Gridepo g;
    private final String id;
    private final RoomAlgo algo;

    public Room(Gridepo g, String id, RoomAlgo algo) {
        this.g = g;
        this.id = id;
        this.algo = algo;
    }

    public String getId() {
        return id;
    }

    public JsonObject populate(JsonObject event) {
        throw new NotImplementedException();
    }

    public JsonObject finalize(JsonObject event) {
        throw new NotImplementedException();
    }

    public ChannelEventAuthorization offer(JsonObject event) {
        throw new NotImplementedException();
    }

    public ChannelEventAuthorization offer(BareEvent<?> event) {
        return offer(finalize(populate(event.getJson())));
    }

    public ChannelEventAuthorization inject(String sender, JsonObject seedDoc, List<JsonObject> stateDocs) {
        return new ChannelEventAuthorization.Builder("unknown").deny("Not implemented");
    }

    RoomView getView() {
        return new RoomView();
    }

}
