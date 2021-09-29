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

package io.kamax.test.gridify.server.network.matrix.core.federation;

import io.kamax.gridify.server.core.channel.event.ChannelEvent;
import io.kamax.gridify.server.network.matrix.core.event.BareCanonicalAliasEvent;
import io.kamax.gridify.server.network.matrix.core.event.BareJoinRulesEvent;
import io.kamax.gridify.server.network.matrix.core.event.BareMessageEvent;
import io.kamax.gridify.server.network.matrix.core.room.Room;
import io.kamax.gridify.server.network.matrix.core.room.RoomAlias;
import io.kamax.gridify.server.network.matrix.core.room.RoomMembership;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BasicFederation extends FederationTest {

    @Test
    public void inviteAndJoin() {
        makeSharedRoomViaInvite();
    }

    @Test
    public void inviteAndReject() {
        Room u1r1 = s1.createRoom();
        s1.inviteToRoom(u1r1.getId(), u2);
        s2.leaveRoom(u1r1.getId());
        Room u2r1 = s2.getRoom(u1r1.getId());
        assertEquals(RoomMembership.Leave, u2r1.getView().getState().getMembership(s2.getUser()));
        assertEquals(RoomMembership.Leave, u1r1.getView().getState().getMembership(s2.getUser()));
    }

    @Test
    public void makePublicAndJoin() {
        Room u1r1 = s1.createRoom();
        String rId = u1r1.getId();
        String r1Alias = RoomAlias.from("test", s1.getDomain()).full();

        BareCanonicalAliasEvent aEv = new BareCanonicalAliasEvent();
        aEv.getContent().setAlias(r1Alias);
        s1.send(rId, aEv);

        BareJoinRulesEvent jEv = new BareJoinRulesEvent();
        jEv.getContent().setRule("public");
        s1.send(rId, jEv);

        Room u2r1 = s2.joinRoom(r1Alias);
        assertEquals(u1r1.getId(), u2r1.getId());
        assertEquals(2, u1r1.getView().getAllServers().size());
        assertEquals(2, u2r1.getView().getAllServers().size());

        s2.leaveRoom(u2r1.getId());
        assertEquals(RoomMembership.Leave, u2r1.getView().getState().getMembership(s2.getUser()));
        assertEquals(RoomMembership.Leave, u1r1.getView().getState().getMembership(s2.getUser()));

        u2r1 = s2.joinRoom(r1Alias);
        assertEquals(RoomMembership.Join, u2r1.getView().getState().getMembership(s2.getUser()));
        assertEquals(RoomMembership.Join, u1r1.getView().getState().getMembership(s2.getUser()));
    }

    @Test
    public void backfillComplex() throws InterruptedException {
        String roomId = makeSharedRoomViaInvite();

        mx1.getFedPusher().setEnabled(false);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 128; i++) {
            int j = i;
            tasks.add(() -> s1.send(roomId, BareMessageEvent.makeText("Message " + j)));
        }

        List<String> events = executor.invokeAll(tasks).stream().map(f -> {
            try {
                return f.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());

        mx1.getFedPusher().setEnabled(true);
        events.add(s1.send(roomId, BareMessageEvent.makeText("Final message")));

        for (String evId : events) {
            Optional<ChannelEvent> g1c1evOpt = mx1.store().findEvent(roomId, evId);
            assertTrue(g1c1evOpt.isPresent());
            ChannelEvent g1c1ev = g1c1evOpt.get();
            assertTrue(g1c1ev.getMeta().isPresent());
            assertTrue(g1c1ev.getMeta().isProcessed());
            assertTrue(g1c1ev.getMeta().isAllowed());

            Optional<ChannelEvent> g2c1evOpt = mx2.store().findEvent(roomId, evId);
            assertTrue(g2c1evOpt.isPresent());
            ChannelEvent g2c1ev = g2c1evOpt.get();
            assertTrue(g2c1ev.getMeta().isPresent());
            assertTrue(g2c1ev.getMeta().isProcessed());
            assertTrue(g2c1ev.getMeta().isAllowed());
        }
    }

}
