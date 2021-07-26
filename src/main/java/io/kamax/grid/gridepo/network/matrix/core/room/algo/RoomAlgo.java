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

package io.kamax.grid.gridepo.network.matrix.core.room.algo;

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.core.channel.state.ChannelEventAuthorization;
import io.kamax.grid.gridepo.network.matrix.core.event.BareEvent;
import io.kamax.grid.gridepo.network.matrix.core.event.BarePowerEvent;
import io.kamax.grid.gridepo.network.matrix.core.room.RoomID;
import io.kamax.grid.gridepo.network.matrix.core.room.RoomState;
import org.apache.commons.lang3.RandomStringUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

public interface RoomAlgo {

    String getVersion();

    default String generateRoomId(String domain) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(Instant.now().toEpochMilli());
        String localpart = new String(buffer.array(), StandardCharsets.UTF_8) + RandomStringUtils.randomAlphanumeric(4);
        return RoomID.from(localpart, domain).full();
    }

    long getBaseDepth();

    long getCreateDepth();

    BarePowerEvent.Content getDefaultPowers(String creator);

    String validate(JsonObject ev);

    ChannelEventAuthorization authorize(RoomState state, String evId, JsonObject ev);

    List<BareEvent<?>> getCreationEvents(String creator, JsonObject options);

    JsonObject buildJoinEvent(String origin, JsonObject template);

}
