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

package io.kamax.test.grid.gridepo.network.matrix.core.federation;

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.config.GridepoConfig;
import io.kamax.grid.gridepo.core.identity.User;
import io.kamax.grid.gridepo.http.MonolithHttpGridepo;
import io.kamax.grid.gridepo.network.matrix.core.MatrixCore;
import io.kamax.grid.gridepo.network.matrix.core.base.UserSession;
import io.kamax.grid.gridepo.network.matrix.core.event.BareCanonicalAliasEvent;
import io.kamax.grid.gridepo.network.matrix.core.event.BareJoinRulesEvent;
import io.kamax.grid.gridepo.network.matrix.core.federation.HomeServerHttpClient;
import io.kamax.grid.gridepo.network.matrix.core.room.Room;
import io.kamax.grid.gridepo.network.matrix.core.room.RoomAlias;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.util.Objects;

import static org.junit.Assert.assertEquals;

public class FederationTest {

    protected static String pass = "gridepo";

    protected static MonolithHttpGridepo mg1;
    protected static Gridepo g1;
    protected static MatrixCore mx1;
    protected static String n1;
    protected static String a1;
    protected static String u1;
    protected static UserSession s1;

    protected static MonolithHttpGridepo mg2;
    protected static Gridepo g2;
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
        GridepoConfig.Listener l1 = new GridepoConfig.Listener();
        l1.addNetwork(GridepoConfig.NetworkListeners.forMatrixHomeServer());
        l1.setPort(dp1);
        GridepoConfig cfg1 = GridepoConfig.inMemory();
        cfg1.setDomain(dn1);
        cfg1.getListeners().add(l1);

        String dh2 = "localhost";
        int dp2 = 60002;
        String dn2 = dh2 + ":" + dp2;
        GridepoConfig.Listener l2 = new GridepoConfig.Listener();
        l2.addNetwork(GridepoConfig.NetworkListeners.forMatrixHomeServer());
        l2.setPort(dp2);
        GridepoConfig cfg2 = GridepoConfig.inMemory();
        cfg2.setDomain(dn2);
        cfg2.getListeners().add(l2);

        mg1 = new MonolithHttpGridepo(cfg1);
        g1 = mg1.start();
        mx1 = g1.overMatrix();
        n1 = "jane";
        a1 = "@" + n1 + ":" + dn1;
        mx1.getFedPusher().setAsync(false);
        User u1b = mx1.vHost(dn1).asClient().register(n1, pass);
        //u1b.addThreePid(new GenericThreePid("g.id.net.matrix", a1));
        s1 = mx1.vHost(dn1).asClient().login(u1b);
        u1 = s1.getUser();

        mg2 = new MonolithHttpGridepo(cfg2);
        g2 = mg2.start();
        mx2 = g2.overMatrix();
        n2 = "john";
        a2 = "@" + n2 + ":" + dn2;
        mx2.getFedPusher().setAsync(false);
        User u2b = g2.register(n2, pass);
        //u2b.addThreePid(new GenericThreePid("g.id.net.matrix", a2));
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

    protected String makeSharedRoom() {
        Room u1r1 = s1.createRoom(new JsonObject());
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

        return rId;
    }

}
