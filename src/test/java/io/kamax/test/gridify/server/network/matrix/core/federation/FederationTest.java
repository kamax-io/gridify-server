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
import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.config.GridifyConfig;
import io.kamax.gridify.server.core.identity.User;
import io.kamax.gridify.server.http.MonolithHttpGridifyServer;
import io.kamax.gridify.server.network.matrix.core.MatrixCore;
import io.kamax.gridify.server.network.matrix.core.base.UserSession;
import io.kamax.gridify.server.network.matrix.core.federation.HomeServerHttpClient;
import io.kamax.gridify.server.network.matrix.core.room.Room;
import io.kamax.gridify.server.network.matrix.core.room.RoomMembership;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.util.Objects;

import static org.junit.Assert.assertEquals;

public class FederationTest {

    protected static String pass = "gridify";

    protected static MonolithHttpGridifyServer mg1;
    protected static GridifyServer g1;
    protected static MatrixCore mx1;
    protected static String n1;
    protected static String a1;
    protected static String u1;
    protected static UserSession s1;

    protected static MonolithHttpGridifyServer mg2;
    protected static GridifyServer g2;
    protected static MatrixCore mx2;
    protected static String n2;
    protected static String a2;
    protected static String u2;
    protected static UserSession s2;

    @BeforeClass
    public static void init() {
        deinit();

        HomeServerHttpClient.useHttps = false;

        String dh1 = "localhost";
        int dp1 = 60001;
        String dn1 = dh1 + ":" + dp1;
        GridifyConfig.Listener l1 = new GridifyConfig.Listener();
        l1.addNetwork(GridifyConfig.NetworkListeners.forMatrixHomeServer());
        l1.setPort(dp1);
        GridifyConfig cfg1 = GridifyConfig.inMemory();
        cfg1.getListeners().add(l1);

        String dh2 = "localhost";
        int dp2 = 60002;
        String dn2 = dh2 + ":" + dp2;
        GridifyConfig.Listener l2 = new GridifyConfig.Listener();
        l2.addNetwork(GridifyConfig.NetworkListeners.forMatrixHomeServer());
        l2.setPort(dp2);
        GridifyConfig cfg2 = GridifyConfig.inMemory();
        cfg2.getListeners().add(l2);

        mg1 = new MonolithHttpGridifyServer(cfg1);
        g1 = mg1.start();
        mx1 = g1.overMatrix();
        mx1.addDomain(dn1, dn1);
        n1 = "jane";
        a1 = "@" + n1 + ":" + dn1;
        mx1.getFedPusher().setAsync(false);
        User u1b = mx1.vHost(dn1).asClient().register(n1, pass);
        s1 = mx1.vHost(dn1).asClient().login(u1b);
        u1 = s1.getUser();

        mg2 = new MonolithHttpGridifyServer(cfg2);
        g2 = mg2.start();
        mx2 = g2.overMatrix();
        mx2.addDomain(dn2, dn2);
        n2 = "john";
        a2 = "@" + n2 + ":" + dn2;
        mx2.getFedPusher().setAsync(false);
        User u2b = g2.register(n2, pass);
        s2 = mx2.vHost(dn2).asClient().login(u2b);
        u2 = s2.getUser();
    }

    @AfterClass
    public static void deinit() {
        if (Objects.nonNull(mg1)) {
            mg1.stop();
        }

        if (Objects.nonNull(mg2)) {
            mg2.stop();
        }
    }

    protected String makeSharedRoomViaInvite() {
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

        return g1c1.getId();
    }

}
