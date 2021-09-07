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

package io.kamax.test.gridify.server.network.matrix.core;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.config.GridifyConfig;
import io.kamax.gridify.server.core.MonolithGridifyServer;
import io.kamax.gridify.server.core.SyncOptions;
import io.kamax.gridify.server.core.channel.event.ChannelEvent;
import io.kamax.gridify.server.core.identity.User;
import io.kamax.gridify.server.core.store.ChannelStateDao;
import io.kamax.gridify.server.network.matrix.core.MatrixDataClient;
import io.kamax.gridify.server.network.matrix.core.base.UserSession;
import io.kamax.gridify.server.network.matrix.core.event.RoomEventType;
import io.kamax.gridify.server.network.matrix.core.room.Room;
import io.kamax.gridify.server.network.matrix.core.room.RoomMembership;
import io.kamax.gridify.server.network.matrix.http.json.SyncResponse;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class BasicUsageTest {

    @Test
    public void roomCreate() {
        GridifyConfig cfg = GridifyConfig.inMemory();
        GridifyServer g = new MonolithGridifyServer(cfg);
        g.overMatrix().addDomain("localhost", "localhost");
        g.start();
        MatrixDataClient client = g.overMatrix().vHost("localhost").asClient();

        User u = client.register("test", "test");
        UserSession session = client.login(u);
        Room r = session.createRoom(new JsonObject());
        List<Long> extremityIds = g.getStore().getForwardExtremities(r.getLocalId());
        assertEquals(1, extremityIds.size());
        ChannelEvent head = g.getStore().getEvent(extremityIds.get(0));
        ChannelStateDao state = g.getStore().getState(head.getLid());
        assertEquals(3, state.getEvents().size());

        Optional<RoomMembership> membership = r.getView().getState().findMembership(u.getNetworkId("matrix"));
        assertTrue(membership.isPresent());
        assertTrue(RoomMembership.Join.isAny(membership.get()));

        SyncResponse data = session.sync(new SyncOptions().setToken("0").setTimeout(0));
        assertFalse(data.rooms.join.isEmpty());
        assertTrue(StringUtils.isNotBlank(data.nextBatch));

        session.send(r.getId(), RoomEventType.Message.getId(), UUID.randomUUID().toString(), new JsonObject());
        data = session.sync(new SyncOptions().setToken(data.nextBatch));
        assertEquals(1, data.rooms.join.get(r.getId()).getTimeline().getEvents().size());
    }

}
