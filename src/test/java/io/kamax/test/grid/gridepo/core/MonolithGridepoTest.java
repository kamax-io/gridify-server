/*
 * Gridepo - Grid Data Server
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

package io.kamax.test.grid.gridepo.core;

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.config.GridepoConfig;
import io.kamax.grid.gridepo.core.MonolithGridepo;
import io.kamax.grid.gridepo.core.identity.User;
import io.kamax.grid.gridepo.network.matrix.core.MatrixDataClient;
import io.kamax.grid.gridepo.network.matrix.core.base.UserSession;
import io.kamax.grid.gridepo.network.matrix.core.room.Room;
import org.junit.Test;

public class MonolithGridepoTest {

    /*
    @Test
    public void basicChannelCreate() {
        GridepoConfig cfg = GridepoConfig.inMemory();
        cfg.setDomain("localhost");
        Gridepo g = new MonolithGridepo(cfg);
        g.start();

        User u = g.register("gridepo", "gridepo");
        UserSession uSess = g.overGrid().forData().asClient().login(u);
        String uId = uSess.getUser().getGridId().full();

        Channel ch = g.getChannelManager().createChannel(uId);
        assertEquals(ChannelMembership.Join, ch.getView().getState().getMembership(uId));

        SyncData data = uSess.sync(new SyncOptions().setToken("0").setTimeout(0));
        assertFalse(data.getEvents().isEmpty());
        assertTrue(StringUtils.isNotBlank(data.getPosition()));
        assertNotEquals("0", data.getPosition());

        g.stop();
    }
    */

    @Test
    public void basicRoomCreate() {
        GridepoConfig cfg = GridepoConfig.inMemory();
        cfg.setDomain("localhost");
        Gridepo g = new MonolithGridepo(cfg);
        g.start();
        MatrixDataClient client = g.overMatrix().vHost("localhost").asClient();

        User u = client.register("test", "test");
        UserSession session = client.login(u);
        Room r = session.createRoom(new JsonObject());
    }

}
