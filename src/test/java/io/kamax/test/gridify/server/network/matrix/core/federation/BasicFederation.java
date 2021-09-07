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

import io.kamax.gridify.server.network.matrix.core.event.BareCanonicalAliasEvent;
import io.kamax.gridify.server.network.matrix.core.event.BareJoinRulesEvent;
import io.kamax.gridify.server.network.matrix.core.room.Room;
import io.kamax.gridify.server.network.matrix.core.room.RoomAlias;
import io.kamax.gridify.server.network.matrix.core.room.RoomMembership;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

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

}
