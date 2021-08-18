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

package io.kamax.gridify.server.core;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.JsonObject;
import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.codec.GridHash;
import io.kamax.gridify.server.config.GridifyConfig;
import io.kamax.gridify.server.core.auth.AuthService;
import io.kamax.gridify.server.core.auth.Credentials;
import io.kamax.gridify.server.core.auth.UIAuthSession;
import io.kamax.gridify.server.core.auth.UIAuthStage;
import io.kamax.gridify.server.core.auth.multi.MultiStoreAuthService;
import io.kamax.gridify.server.core.crypto.Cryptopher;
import io.kamax.gridify.server.core.crypto.KeyIdentifier;
import io.kamax.gridify.server.core.crypto.PublicKey;
import io.kamax.gridify.server.core.crypto.RegularKeyIdentifier;
import io.kamax.gridify.server.core.crypto.ed25519.Ed25519Cryptopher;
import io.kamax.gridify.server.core.event.EventStreamer;
import io.kamax.gridify.server.core.identity.GenericThreePid;
import io.kamax.gridify.server.core.identity.IdentityManager;
import io.kamax.gridify.server.core.identity.ThreePid;
import io.kamax.gridify.server.core.identity.User;
import io.kamax.gridify.server.core.signal.AppStopping;
import io.kamax.gridify.server.core.signal.SignalBus;
import io.kamax.gridify.server.core.store.DataStore;
import io.kamax.gridify.server.core.store.MemoryStore;
import io.kamax.gridify.server.core.store.crypto.FileKeyStore;
import io.kamax.gridify.server.core.store.crypto.KeyStore;
import io.kamax.gridify.server.core.store.crypto.MemoryKeyStore;
import io.kamax.gridify.server.core.store.postgres.PostgreSQLDataStore;
import io.kamax.gridify.server.exception.InternalServerError;
import io.kamax.gridify.server.exception.InvalidTokenException;
import io.kamax.gridify.server.exception.ObjectNotFoundException;
import io.kamax.gridify.server.exception.UnauthenticatedException;
import io.kamax.gridify.server.network.grid.core.GridCore;
import io.kamax.gridify.server.network.grid.core.SimpleGridServer;
import io.kamax.gridify.server.network.matrix.core.MatrixCore;
import io.kamax.gridify.server.network.matrix.core.base.BaseMatrixCore;
import io.kamax.gridify.server.util.GsonUtil;
import io.kamax.gridify.server.util.KxLog;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MonolithGridifyServer implements GridifyServer {

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    private final String serverId;
    private final PublicKey pubKey;

    private final Algorithm jwtAlgo;
    private final JWTVerifier jwtVerifier;

    private final GridifyConfig cfg;
    private final SignalBus bus;
    private final DataStore store;
    private final KeyStore kStore;
    private final Cryptopher crypto;
    private final AuthService authSvc;
    private final IdentityManager idMgr;

    private final EventStreamer streamer;

    private final MatrixCore mxCore;
    private final GridCore gCore;

    private boolean isStopping;
    private final Map<String, Boolean> tokens = new ConcurrentHashMap<>();

    public MonolithGridifyServer(GridifyConfig cfg) {
        this.cfg = cfg;
        if (cfg.getAuth().getFlows().isEmpty()) {
            cfg.getAuth().addFlow().addStage("m.login.password");
        }

        bus = new SignalBus();

        String kStoreType = cfg.getStorage().getKey().getType();
        String kStoreLoc = cfg.getStorage().getKey().getLocation();
        if (StringUtils.isBlank(kStoreLoc)) {
            kStoreLoc = Paths.get(cfg.getStorage().getData(), "keys").toString();
        }

        //FIXME use ServiceLoader
        if (StringUtils.equals("file", kStoreType)) {
            kStore = new FileKeyStore(kStoreLoc);
        } else if (StringUtils.equals("memory", kStoreType)) {
            kStore = new MemoryKeyStore();
        } else {
            throw new IllegalArgumentException("Unknown keys storage: " + kStoreType);
        }
        crypto = new Ed25519Cryptopher(kStore);

        // FIXME use ServiceLoader
        String dbStoreType = cfg.getStorage().getDatabase().getType();
        if (StringUtils.equals("memory", dbStoreType)) {
            store = MemoryStore.get(cfg.getStorage().getDatabase().getConnection());
        } else if (StringUtils.equals("postgresql", dbStoreType)) {
            store = new PostgreSQLDataStore(cfg.getStorage());
        } else {
            throw new IllegalArgumentException("Unknown database type: " + dbStoreType);
        }

        if (!store.hasConfig("core.server.id")) {
            store.setConfig("core.server.id", UUID.randomUUID());
        }
        serverId = store.getConfigString("core.server.id");

        if (!store.hasConfig("core.crypto.publicKey")) {
            KeyIdentifier id = crypto.generateKey("Server main key");
            String hash = crypto.getPublicKeyBase64(id);
            store.setConfig("core.crypto.publicKey", PublicKey.get(id, hash));
        }
        pubKey = store.getConfig("core.crypto.publicKey", PublicKey.class);

        String jwtSeed = cfg.getCrypto().getSeed().get("jwt");
        if (StringUtils.isBlank(jwtSeed)) {
            // FIXME re-enable warning
            //log.warn("JWT secret is not set, computing one from main signing key. Please set a JWT secret in your config");
            jwtSeed = GridHash.get().hashFromUtf8(serverId + crypto.getKey(RegularKeyIdentifier.parse(pubKey.getId())).getPrivateKeyBase64());
        }

        jwtAlgo = Algorithm.HMAC256(jwtSeed);
        jwtVerifier = JWT.require(jwtAlgo)
                .withIssuer(serverId)
                .build();

        idMgr = new IdentityManager(cfg.getIdentity(), store, crypto);
        authSvc = new MultiStoreAuthService(this);
        streamer = new EventStreamer(store);

        mxCore = new BaseMatrixCore(this);
        gCore = new SimpleGridServer(this, serverId); // FIXME give a proper domain
    }

    // FIXME check where it is used and if it used correctly
    @Override
    public String getServerId() {
        return serverId;
    }

    @Override
    public PublicKey getPublicKey() {
        return pubKey;
    }

    @Override
    public void setup(JsonObject setupDoc) {
        String username = GsonUtil.getStringOrNull(setupDoc, "admin_username");
        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("Admin username is missing");
        }
        String pass = GsonUtil.getStringOrNull(setupDoc, "admin_password");
        if (StringUtils.isBlank(pass)) {
            throw new IllegalArgumentException("Admin password cannot be empty/blank");
        }

        log.info("Creating initial admin account");
        register(username, pass);

        String domain = GsonUtil.getStringOrNull(setupDoc, "matrix_domain");
        if (StringUtils.isNotBlank(domain)) {
            mxCore.addDomain(domain);
        }
    }

    @Override
    public void start() {
        isStopping = false;
    }

    @Override
    public void stop() {
        isStopping = true;

        bus.getMain().publish(AppStopping.Signal);
    }

    @Override
    public boolean isStopping() {
        return isStopping;
    }

    @Override
    public GridifyConfig getConfig() {
        return cfg;
    }

    @Override
    public SignalBus getBus() {
        return bus;
    }

    @Override
    public EventStreamer getStreamer() {
        return streamer;
    }

    @Override
    public AuthService getAuth() {
        return authSvc;
    }

    @Override
    public String createSessionToken(String network, User usr) {
        String token = JWT.create()
                .withIssuer(serverId)
                .withIssuedAt(new Date())
                .withExpiresAt(Date.from(Instant.ofEpochMilli(Long.MAX_VALUE)))
                .withClaim(GridType.id().internal().getId(), usr.getId())
                .sign(jwtAlgo);

        store.insertUserAccessToken(usr.getLid(), token);
        tokens.put(token, true);
        return token;
    }

    @Override
    public User register(String username, String password) {
        User user = getIdentity().createUserWithKey();
        user.addThreePid(new GenericThreePid(GridType.id().local().username(), username));
        user.addCredentials(new Credentials("g.auth.id.password", password));
        return user;
    }

    @Override
    public UIAuthSession login(String network) {
        return getAuth().getSession(network, getConfig().getAuth());
    }

    @Override
    public User login(UIAuthSession auth, String stageId) {
        if (!auth.isAuthenticated()) {
            throw new UnauthenticatedException(auth);
        }

        Set<String> idStages = new HashSet<>(auth.getCompletedStages());
        if (idStages.isEmpty()) {
            throw new InternalServerError("No ID-based authentication was completed, cannot identify the user");
        }

        UIAuthStage stage = auth.getStage(stageId);

        ThreePid uid = stage.getUid();
        return getIdentity().findUser(uid).orElseGet(() -> {
            User newUsr = idMgr.createUserWithKey();
            newUsr.linkToStoreId(uid);
            return newUsr;
        });
    }

    @Override
    public void destroySessionToken(String token) {
        store.deleteUserAccessToken(token);
        tokens.remove(token);
    }

    @Override
    public User validateSessionToken(String token) {
        try {
            DecodedJWT data = jwtVerifier.verify(JWT.decode(token));
            String uid = data.getClaim(GridType.of("id.internal")).asString();
            return getIdentity().getUser(uid); // FIXME check in cluster for missing events
        } catch (ObjectNotFoundException e) {
            throw new InvalidTokenException(e.getMessage());
        } catch (JWTVerificationException e) {
            throw new InvalidTokenException("Invalid token");
        }
    }

    @Override
    public GridCore overGrid() {
        return gCore;
    }

    @Override
    public MatrixCore overMatrix() {
        return mxCore;
    }

    @Override
    public DataStore getStore() {
        return store;
    }

    @Override
    public Cryptopher getCrypto() {
        return crypto;
    }

    @Override
    public IdentityManager getIdentity() {
        return idMgr;
    }

}
