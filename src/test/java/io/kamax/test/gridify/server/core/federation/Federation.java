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

package io.kamax.test.gridify.server.core.federation;

import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.config.GridifyConfig;
import io.kamax.gridify.server.core.UserSession;
import io.kamax.gridify.server.core.channel.Channel;
import io.kamax.gridify.server.core.channel.event.BareAliasEvent;
import io.kamax.gridify.server.core.channel.event.BareJoiningEvent;
import io.kamax.gridify.server.core.federation.DataServerHttpClient;
import io.kamax.gridify.server.core.identity.GenericThreePid;
import io.kamax.gridify.server.core.identity.User;
import io.kamax.gridify.server.http.MonolithHttpGridifyServer;
import io.kamax.gridify.server.network.grid.core.ChannelAlias;
import io.kamax.gridify.server.network.grid.core.UserID;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.util.Objects;

import static org.junit.Assert.assertEquals;

public class Federation {

    protected static String pass = "gridify";

    protected static MonolithHttpGridifyServer mg1;
    protected static GridifyServer g1;
    protected static String dn1;
    protected static String n1;
    protected static String a1;
    protected static UserID u1;
    protected static UserSession s1;

    protected static MonolithHttpGridifyServer mg2;
    protected static GridifyServer g2;
    protected static String dn2;
    protected static String n2;
    protected static String a2;
    protected static UserID u2;
    protected static UserSession s2;

    @BeforeClass
    public static void init() {
        deinit();

        DataServerHttpClient.useHttps = false;

        String dh1 = "localhost";
        int dp1 = 60001;
        dn1 = dh1 + ":" + dp1;
        GridifyConfig.Listener l1 = new GridifyConfig.Listener();
        l1.addNetwork(GridifyConfig.NetworkListeners.forGridDataServer());
        l1.addNetwork(GridifyConfig.NetworkListeners.forGridIdentityServer());
        l1.setPort(dp1);
        GridifyConfig cfg1 = GridifyConfig.inMemory();
        //cfg1.setDomain(dn1);
        cfg1.getListeners().add(l1);

        String dh2 = "localhost";
        int dp2 = 60002;
        dn2 = dh2 + ":" + dp2;
        GridifyConfig.Listener l2 = new GridifyConfig.Listener();
        l2.addNetwork(GridifyConfig.NetworkListeners.forGridDataServer());
        l2.addNetwork(GridifyConfig.NetworkListeners.forGridIdentityServer());
        l2.setPort(dp2);
        GridifyConfig cfg2 = GridifyConfig.inMemory();
        //cfg2.setDomain(dn2);
        cfg2.getListeners().add(l2);

        MonolithHttpGridifyServer mg1 = new MonolithHttpGridifyServer(cfg1);
        MonolithHttpGridifyServer mg2 = new MonolithHttpGridifyServer(cfg2);

        n1 = "dark";
        a1 = "@" + n1 + "@" + dn1;
        g1 = mg1.start();
        g1.overGrid().fedPusher().setAsync(false);
        User u1b = g1.register(n1, pass);
        s1 = g1.overGrid().vHost(dn1).forData().asClient().login(u1b);
        s1.getUser().addThreePid(new GenericThreePid("g.id.net.grid.alias", a1));
        u1 = s1.getUser().getGridId();

        n2 = "light";
        a2 = "@" + n2 + "@" + dn2;
        g2 = mg2.start();
        g2.overGrid().fedPusher().setAsync(false);
        User u2b = g2.register(n2, pass);
        s2 = g2.overGrid().vHost(dn2).forData().asClient().login(u2b);
        s2.getUser().addThreePid(new GenericThreePid("g.id.net.grid.alias", a2));
        u2 = s2.getUser().getGridId();
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

    protected String makeSharedChannel() {
        Channel g1c1 = s1.createChannel();
        String cId = g1c1.getId().full();
        ChannelAlias c1Alias = new ChannelAlias("test", dn1);

        BareAliasEvent aEv = new BareAliasEvent();
        aEv.addAlias(c1Alias);
        s1.send(cId, aEv.getJson());

        BareJoiningEvent joinRules = new BareJoiningEvent();
        joinRules.getContent().setRule("public");
        s1.send(cId, joinRules.getJson());

        Channel g2c1 = s2.joinChannel(c1Alias);

        assertEquals(g1c1.getId(), g2c1.getId());
        assertEquals(2, g1c1.getView().getAllServers().size());
        assertEquals(2, g2c1.getView().getAllServers().size());

        return cId;
    }

}
