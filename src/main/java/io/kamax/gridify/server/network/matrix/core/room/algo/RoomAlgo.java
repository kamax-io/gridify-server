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

package io.kamax.gridify.server.network.matrix.core.room.algo;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.core.channel.state.ChannelEventAuthorization;
import io.kamax.gridify.server.network.matrix.core.crypto.MatrixDomainCryptopher;
import io.kamax.gridify.server.network.matrix.core.event.BareEvent;
import io.kamax.gridify.server.network.matrix.core.event.BarePowerEvent;
import io.kamax.gridify.server.network.matrix.core.federation.RoomJoinTemplate;
import io.kamax.gridify.server.network.matrix.core.room.RoomID;
import io.kamax.gridify.server.network.matrix.core.room.RoomState;
import io.kamax.gridify.server.util.Base64Codec;
import org.apache.commons.lang3.RandomStringUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface RoomAlgo {

    String getVersion();

    default String generateRoomId(String domain) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(Instant.now().toEpochMilli());
        String localpartBytes = new String(buffer.array(), StandardCharsets.UTF_8) + RandomStringUtils.randomAlphanumeric(4);
        String localpart = Base64Codec.encode(localpartBytes);
        return RoomID.Sigill + localpart + RoomID.Delimiter + domain;
    }

    long getBaseDepth();

    long getCreateDepth();

    BarePowerEvent.Content getDefaultPowers(String creator);

    List<JsonObject> orderTopologically(List<JsonObject> events);

    String validate(JsonObject ev);

    ChannelEventAuthorization authorizeCreate(JsonObject doc);

    default ChannelEventAuthorization authorize(RoomState state, JsonObject ev) {
        return authorize(state, getEventId(ev), ev);
    }

    ChannelEventAuthorization authorize(RoomState state, String evId, JsonObject ev);

    List<BareEvent<?>> getCreationEvents(String domain, String creator, JsonObject options);

    JsonObject buildJoinEvent(RoomJoinTemplate template);

    Set<String> getAuthEvents(JsonObject eventDoc, RoomState state);

    String getEventId(JsonObject event);

    String computeEventHash(JsonObject event);

    /**
     * Complete the doc structure then hash and sign it, making it final and signed off by the domain.
     * This may not be equivalent to calling hash() then sign() on the doc.
     *
     * @param doc    The doc to sign off
     * @param crypto The cryptopher to sign with
     * @return A copy of the doc with the added hash and signature
     */
    JsonObject signEvent(JsonObject doc, MatrixDomainCryptopher crypto);

    JsonObject redact(JsonObject doc);

}
