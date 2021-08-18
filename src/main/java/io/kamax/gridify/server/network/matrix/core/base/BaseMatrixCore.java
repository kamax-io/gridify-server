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

package io.kamax.gridify.server.network.matrix.core.base;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.core.crypto.Cryptopher;
import io.kamax.gridify.server.core.crypto.KeyIdentifier;
import io.kamax.gridify.server.core.event.EventStreamer;
import io.kamax.gridify.server.core.signal.SignalBus;
import io.kamax.gridify.server.core.store.DataStore;
import io.kamax.gridify.server.core.store.DomainDao;
import io.kamax.gridify.server.exception.NotImplementedException;
import io.kamax.gridify.server.network.matrix.core.MatrixCore;
import io.kamax.gridify.server.network.matrix.core.MatrixServer;
import io.kamax.gridify.server.network.matrix.core.domain.MatrixDomain;
import io.kamax.gridify.server.network.matrix.core.federation.FederationPusher;
import io.kamax.gridify.server.network.matrix.core.federation.HomeServerManager;
import io.kamax.gridify.server.network.matrix.core.room.RoomDirectory;
import io.kamax.gridify.server.network.matrix.core.room.RoomManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BaseMatrixCore implements MatrixCore {

    private final GridifyServer g;
    private final Map<String, MatrixServer> vHosts;
    private final RoomManager rMgr;
    private final HomeServerManager hsMgr;
    private final FederationPusher fedPusher;
    private final RoomDirectory rDir;
    private final Map<String, Queue<JsonObject>> commandResponseQueues;

    public BaseMatrixCore(GridifyServer g) {
        this.g = g;

        vHosts = new HashMap<>();
        rMgr = new RoomManager(g);
        hsMgr = new HomeServerManager(g);
        fedPusher = new FederationPusher(this);
        rDir = new RoomDirectory(g, g.getStore(), g.getBus(), hsMgr);
        commandResponseQueues = new ConcurrentHashMap<>();

        init();
    }

    private void init() {
        List<DomainDao> domainDaos = g.getStore().listDomains("matrix");
        for (DomainDao dao : domainDaos) {
            MatrixDomain domain = MatrixDomain.fromDao(dao);
            vHosts.put(domain.getHost(), new BaseMatrixServer(this, domain));
        }
    }

    @Override
    public GridifyServer gridify() {
        return g;
    }

    @Override
    public SignalBus bus() {
        return g.getBus();
    }

    @Override
    public DataStore store() {
        return g.getStore();
    }

    @Override
    public Cryptopher crypto() {
        return g.getCrypto();
    }

    @Override
    public RoomManager roomMgr() {
        return rMgr;
    }

    @Override
    public RoomDirectory roomDir() {
        return rDir;
    }

    @Override
    public HomeServerManager hsMgr() {
        return hsMgr;
    }

    @Override
    public boolean isLocal(String host) {
        return vHosts.containsKey(host);
    }

    @Override
    public MatrixDomain addDomain(String vHost) {
        if (isLocal(vHost)) {
            throw new IllegalStateException("Domain " + vHost + " is already registered");
        }

        KeyIdentifier keyId = crypto().generateKey("Key of Matrix domain [" + vHost + "]");
        MatrixDomain domain = new MatrixDomain();
        domain.setHost(vHost);
        domain.setSigningKey(keyId);
        domain.setOldSigningKeys(new ArrayList<>());

        DomainDao dao = store().saveDomain(domain.toDao());
        domain.setLid(dao.getLocalId());

        vHosts.put(domain.getHost(), new BaseMatrixServer(this, domain));
        return domain;
    }

    @Override
    public MatrixServer forDomain(String domain) {
        return vHost(domain);
    }

    @Override
    public void removeDomain(String domain) {
        throw new NotImplementedException();
    }

    @Override
    public MatrixServer vHost(String host) {
        MatrixServer srv = vHosts.get(host);
        if (Objects.isNull(srv)) {
            throw new IllegalArgumentException("Unknown host " + host);
        }
        return srv;
    }

    @Override
    public EventStreamer getStreamer() {
        return new EventStreamer(g.getStore());
    }

    @Override
    public FederationPusher getFedPusher() {
        return fedPusher;
    }

    @Override
    public Queue<JsonObject> getCommandResponseQueue(String userId) {
        return commandResponseQueues.computeIfAbsent(userId, uId -> new LinkedList<>());
    }

}
