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

import com.google.gson.JsonObject;
import io.kamax.gridify.server.network.matrix.core.room.Room;
import io.kamax.gridify.server.network.matrix.core.room.RoomMembership;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BasicFederation extends FederationTest {

    @Test
    public void simpleJoin() {
        makeSharedRoom();
    }

    @Test
    public void inviteAndJoin() {
        Room g1c1 = s1.createRoom(new JsonObject());
        s1.inviteToRoom(g1c1.getId(), u2);

        RoomMembership g1u2c1 = g1c1.getView().getState().getMembership(u2);
        assertEquals(RoomMembership.Invite, g1u2c1);

        Room g2c1 = mx2.roomMgr().get(g1c1.getId());
        RoomMembership g2u2c1 = g2c1.getView().getState().getMembership(u2);
        assertEquals(RoomMembership.Invite, g2u2c1);

        Room g2c1Joined = s2.joinRoom(g1c1.getId());
        assertEquals(g1c1.getId(), g2c1Joined.getId());

        g1u2c1 = g1c1.getView().getState().getMembership(u2);
        assertEquals(RoomMembership.Join, g1u2c1);
        g2u2c1 = g2c1.getView().getState().getMembership(u2);
        assertEquals(RoomMembership.Join, g2u2c1);
    }

}
