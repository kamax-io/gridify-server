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

package io.kamax.gridify.server.core.channel.algo;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.core.channel.event.BareEvent;
import io.kamax.gridify.server.core.channel.event.BarePowerEvent;
import io.kamax.gridify.server.core.channel.state.ChannelEventAuthorization;
import io.kamax.gridify.server.core.channel.state.ChannelState;
import io.kamax.gridify.server.network.grid.core.EventID;

import java.util.List;

public interface ChannelAlgo {

    String getVersion();

    long getBaseDepth();

    long getCreateDepth();

    BarePowerEvent.Content getDefaultPowers(String creator);

    EventID generateEventId(String domain);

    String validate(JsonObject ev);

    default ChannelEventAuthorization authorize(ChannelState state, String evId, JsonObject ev) {
        return authorize(state, EventID.parse(evId), ev);
    }

    ChannelEventAuthorization authorize(ChannelState state, EventID evId, JsonObject ev);

    List<BareEvent<?>> getCreationEvents(String creator);

}
