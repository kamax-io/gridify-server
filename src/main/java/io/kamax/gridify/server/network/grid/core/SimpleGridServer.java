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

package io.kamax.gridify.server.network.grid.core;

import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.core.GridType;
import io.kamax.gridify.server.core.channel.ChannelDirectory;
import io.kamax.gridify.server.core.channel.ChannelManager;
import io.kamax.gridify.server.core.event.EventService;
import io.kamax.gridify.server.core.federation.DataServerManager;
import io.kamax.gridify.server.core.federation.FederationPusher;
import io.kamax.gridify.server.core.identity.GenericThreePid;
import io.kamax.gridify.server.core.store.DataStore;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

public class SimpleGridServer implements GridCore, GridServer {

    private final String domain;
    private final ServerID origin;

    private final GridifyServer g;
    private final GridDataServer dataSrv;
    private final EventService evSvc;
    private final DataServerManager dsMgr;
    private final FederationPusher fedPush;

    public SimpleGridServer(GridifyServer g, String domain) {
        this.domain = domain;
        this.origin = ServerID.fromDns(domain);

        this.g = g;
        dsMgr = new DataServerManager();
        evSvc = new EventService(origin, g.getPublicKey(), g.getCrypto());
        fedPush = new FederationPusher(g, dsMgr);
        dataSrv = new SimpleGridDataServer(this);
    }

    @Override
    public boolean isLocal(ServerID id) {
        if (Objects.isNull(id)) {
            return false;
        }
        return StringUtils.equals(origin.full(), id.full());
    }

    @Override
    public GridifyServer gridify() {
        return g;
    }

    @Override
    public ServerID getOrigin() {
        return origin;
    }

    @Override
    public String getDomain() {
        return domain;
    }

    @Override
    public DataServerManager dataSrvMgr() {
        return dsMgr;
    }

    @Override
    public EventService evSvc() {
        return evSvc;
    }

    @Override
    public DataStore store() {
        return g.getStore();
    }

    @Override
    public GridDataServer forData() {
        return dataSrv;
    }

    @Override
    public boolean isLocal(UserID uId) {
        return g.getIdentity().findUser(new GenericThreePid(GridType.of("id.net.grid"), uId.full())).isPresent();
    }

    @Override
    public GridServer vHost(String domain) {
        return this;
    }

    @Override
    public boolean isOrigin(String domain) {
        return false;
    }

    @Override
    public ChannelManager getChannelManager() {
        return dataSrv.getChannelManager();
    }

    @Override
    public ChannelDirectory getChannelDirectory() {
        return dataSrv.getChannelDirectory();
    }

    @Override
    public FederationPusher fedPusher() {
        return fedPush;
    }

}
