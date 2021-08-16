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

package io.kamax.test.gridify.server.core;

import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.config.GridifyConfig;
import io.kamax.gridify.server.core.MonolithGridifyServer;
import io.kamax.gridify.server.core.SyncData;
import io.kamax.gridify.server.core.SyncOptions;
import io.kamax.gridify.server.core.UserSession;
import io.kamax.gridify.server.core.channel.Channel;
import io.kamax.gridify.server.core.channel.ChannelMembership;
import io.kamax.gridify.server.core.identity.User;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import static org.junit.Assert.*;

public class MonolithGridifyServerTest {

    @Test
    public void channelCreate() {
        GridifyConfig cfg = GridifyConfig.inMemory();
        GridifyServer g = new MonolithGridifyServer(cfg);
        g.start();

        User u = g.register("gridify", "gridify");
        UserSession uSess = g.overGrid().vHost("localhost").forData().asClient().login(u);
        String uId = uSess.getUser().getNetworkId("grid");

        Channel ch = g.overGrid().getChannelManager().createChannel(uId);
        assertEquals(ChannelMembership.Join, ch.getView().getState().getMembership(uId));

        SyncData data = uSess.sync(new SyncOptions().setToken("0").setTimeout(0));
        assertFalse(data.getEvents().isEmpty());
        assertTrue(StringUtils.isNotBlank(data.getPosition()));
        assertNotEquals("0", data.getPosition());

        g.stop();
    }

}
