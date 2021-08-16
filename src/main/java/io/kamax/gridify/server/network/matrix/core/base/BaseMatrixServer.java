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
import io.kamax.gridify.server.core.GridType;
import io.kamax.gridify.server.core.auth.UIAuthSession;
import io.kamax.gridify.server.core.crypto.Key;
import io.kamax.gridify.server.core.crypto.RegularKeyIdentifier;
import io.kamax.gridify.server.core.crypto.Signature;
import io.kamax.gridify.server.core.event.EventStreamer;
import io.kamax.gridify.server.core.identity.GenericThreePid;
import io.kamax.gridify.server.core.identity.User;
import io.kamax.gridify.server.core.signal.SignalBus;
import io.kamax.gridify.server.network.matrix.core.MatrixDataClient;
import io.kamax.gridify.server.network.matrix.core.MatrixDataServer;
import io.kamax.gridify.server.network.matrix.core.MatrixServer;
import io.kamax.gridify.server.network.matrix.core.crypto.MatrixDomainCryptopher;
import io.kamax.gridify.server.network.matrix.core.domain.MatrixDomain;
import io.kamax.gridify.server.network.matrix.core.federation.FederationPusher;
import io.kamax.gridify.server.network.matrix.core.federation.HomeServerRequest;
import io.kamax.gridify.server.network.matrix.core.room.RoomDirectory;
import io.kamax.gridify.server.network.matrix.core.room.RoomManager;
import io.kamax.gridify.server.util.GsonUtil;

import java.time.Instant;
import java.util.*;

public class BaseMatrixServer implements MatrixServer, MatrixDataClient, MatrixDataServer {

    private final GridifyServer g;

    private final MatrixDomain domain;

    public BaseMatrixServer(GridifyServer g, MatrixDomain domain) {
        this.g = g;
        this.domain = domain;
    }

    @Override
    public String getDomain() {
        return domain.getHost();
    }

    @Override
    public Set<String> getRoomVersions() {
        return g.overMatrix().roomMgr().getVersions();
    }

    @Override
    public MatrixDomainCryptopher crypto() {
        return new MatrixDomainCryptopher() {

            @Override
            public String getDomain() {
                return domain.getHost();
            }

            @Override
            public Signature sign(JsonObject obj) {
                return g.getCrypto().sign(obj, domain.getSigningKey());
            }

            @Override
            public Signature sign(byte[] data) {
                return g.getCrypto().sign(data, domain.getSigningKey());
            }

        };
    }

    @Override
    public RoomManager roomMgr() {
        return g.overMatrix().roomMgr();
    }

    @Override
    public RoomDirectory roomDir() {
        return g.overMatrix().roomDir();
    }

    @Override
    public EventStreamer getStreamer() {
        return g.getStreamer();
    }

    @Override
    public SignalBus getBus() {
        return g.getBus();
    }

    @Override
    public FederationPusher getFedPusher() {
        return g.overMatrix().getFedPusher();
    }

    @Override
    public Queue<JsonObject> getCommandResponseQueue(String userId) {
        return g.overMatrix().getCommandResponseQueue(userId);
    }

    @Override
    public MatrixDataClient asClient() {
        return this;
    }

    @Override
    public MatrixDataServer asServer() {
        return this;
    }

    @Override
    public boolean isStopping() {
        return g.isStopping();
    }

    @Override
    public UserSession withToken(String token) {
        User u = g.validateSessionToken(token);
        String userId = u.findNetworkId("matrix").orElseGet(() -> "@" + u.getUsername() + ":" + domain.getHost());
        return new UserSession(this, domain.getHost(), userId, token);
    }

    @Override
    public User register(String username, String password) {
        User u = g.register(username, password);
        u.addThreePid(new GenericThreePid(GridType.id().make("net.matrix"), "@" + username + ":" + domain.getHost()));
        return u;
    }

    @Override
    public UserSession login(User u) {
        return withToken(g.createSessionToken("matrix", u));
    }

    @Override
    public UserSession login(String username, String password) {
        JsonObject id = new JsonObject();
        id.addProperty("type", "m.id.user");
        id.addProperty("user", username);
        JsonObject doc = new JsonObject();
        doc.addProperty("type", "m.login.password");
        doc.addProperty("password", password);
        doc.add("identifier", id);
        return login(doc);
    }

    @Override
    public UserSession login(JsonObject credentials) {
        if ("m.login.password".equals(GsonUtil.getStringOrNull(credentials, "type"))) {
            GsonUtil.findObj(credentials, "identifier").ifPresent(id -> {
                if ("m.id.user".equals(GsonUtil.getStringOrNull(id, "type"))) {
                    id.addProperty("type", "g.id.net.matrix.localpart");
                    id.addProperty("value", GsonUtil.getStringOrNull(id, "user"));
                }
            });
        }
        Optional<String> sessionId = GsonUtil.findString(credentials, "session");
        UIAuthSession session;
        if (sessionId.isPresent()) {
            session = g.getAuth().getSession(sessionId.get());
        } else {
            session = g.login("matrix");
        }

        session.complete(credentials);
        User u = g.login(session, "m.login.password");
        return login(u);
    }

    @Override
    public MatrixDomainCryptopher getCrypto() {
        return crypto();
    }

    @Override
    public JsonObject getKeyDocument(String keyId) {
        List<Key> keys = new ArrayList<>();
        keys.add(g.getCrypto().getKey(RegularKeyIdentifier.parse(keyId)));

        JsonObject verifyKeysDoc = new JsonObject();
        JsonObject doc = new JsonObject();
        doc.add("old_verify_keys", new JsonObject());
        doc.addProperty("server_name", domain.getHost());
        doc.addProperty("valid_until_ts", Instant.now().plusSeconds(60 * 60 * 24).toEpochMilli()); // 24h
        doc.add("verify_keys", verifyKeysDoc);

        for (Key key : keys) {
            JsonObject verifyKeyDoc = GsonUtil.makeObj("key", g.getCrypto().getPublicKeyBase64(key.getId()));
            verifyKeysDoc.add(key.getId().getAlgorithm() + ":" + key.getId().getSerial(), verifyKeyDoc);
        }

        JsonObject signDomains = new JsonObject();
        for (Key key : keys) {
            Signature sign = g.getCrypto().sign(doc, key.getId());
            JsonObject signDomain = GsonUtil.makeObj(sign.getKey().getAlgorithm() + ":" + sign.getKey().getSerial(), sign.getSignature());
            signDomains.add(domain.getHost(), signDomain);
        }

        doc.add("signatures", signDomains);
        return doc;
    }

    @Override
    public ServerSession forRequest(HomeServerRequest mxReq) {
        return new ServerSession(g.overMatrix(), domain.getHost(), mxReq.getDoc().getOrigin());
    }

}
