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
import io.kamax.gridify.server.core.channel.ChannelDirectory;
import io.kamax.gridify.server.core.channel.ChannelManager;
import io.kamax.gridify.server.core.crypto.Cryptopher;
import io.kamax.gridify.server.core.crypto.ed25519.Ed25519Cryptopher;
import io.kamax.gridify.server.core.event.EventService;
import io.kamax.gridify.server.core.event.EventStreamer;
import io.kamax.gridify.server.core.federation.DataServerManager;
import io.kamax.gridify.server.core.federation.FederationPusher;
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
import io.kamax.gridify.server.network.grid.core.*;
import io.kamax.gridify.server.network.matrix.core.MatrixCore;
import io.kamax.gridify.server.network.matrix.core.base.BaseMatrixCore;
import io.kamax.gridify.server.network.matrix.core.room.RoomManager;
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

    private final ServerID origin;
    private final Algorithm jwtAlgo;
    private final JWTVerifier jwtVerifier;

    private final GridifyConfig cfg;
    private final SignalBus bus;
    private final DataStore store;
    private final KeyStore kStore;
    private final Cryptopher crypto;
    private final AuthService authSvc;
    private final IdentityManager idMgr;
    private final EventService evSvc;
    private final ChannelManager chMgr;
    private final RoomManager rMgr;
    private final ChannelDirectory chDir;
    private final EventStreamer streamer;
    private final DataServerManager dsMgr;
    private final FederationPusher fedPush;

    private final MatrixCore mxCore;

    private boolean isStopping;
    private Map<String, Boolean> tokens = new ConcurrentHashMap<>();

    public MonolithGridifyServer(GridifyConfig cfg) {
        this.cfg = cfg;
        if (cfg.getAuth().getFlows().isEmpty()) {
            cfg.getAuth().addFlow().addStage("m.login.password");
        }

        if (StringUtils.isBlank(cfg.getDomain())) {
            throw new RuntimeException("Configuration: domain cannot be blank");
        }
        origin = ServerID.fromDns(cfg.getDomain());

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

        // FIXME use ServiceLoader
        String dbStoreType = cfg.getStorage().getDatabase().getType();
        if (StringUtils.equals("memory", dbStoreType)) {
            store = MemoryStore.get(cfg.getStorage().getDatabase().getConnection());
        } else if (StringUtils.equals("postgresql", dbStoreType)) {
            store = new PostgreSQLDataStore(cfg.getStorage());
        } else {
            throw new IllegalArgumentException("Unknown database type: " + dbStoreType);
        }

        dsMgr = new DataServerManager();
        crypto = new Ed25519Cryptopher(kStore);

        String jwtSeed = cfg.getCrypto().getSeed().get("jwt");
        if (StringUtils.isEmpty(jwtSeed)) {
            log.warn("JWT secret is not set, computing one from main signing key. Please set a JWT secret in your config");
            jwtSeed = GridHash.get().hashFromUtf8(cfg.getDomain() + crypto.getServerSigningKey().getPrivateKeyBase64());
        }

        jwtAlgo = Algorithm.HMAC256(jwtSeed);
        jwtVerifier = JWT.require(jwtAlgo)
                .withIssuer(cfg.getDomain())
                .build();

        evSvc = new EventService(origin, crypto);

        idMgr = new IdentityManager(cfg.getIdentity(), store, crypto);
        authSvc = new MultiStoreAuthService(this);

        chMgr = new ChannelManager(this, bus, evSvc, store, dsMgr);
        rMgr = new RoomManager(this);
        streamer = new EventStreamer(store);

        chDir = new ChannelDirectory(origin, store, bus, dsMgr);
        fedPush = new FederationPusher(this, dsMgr);

        mxCore = new BaseMatrixCore(this);

        log.info("We are {}", getDomain());
        log.info("Serving domain(s):");
        log.info("  - {}", origin.full());
    }

    @Override
    public void start() {
        isStopping = false;

        if (idMgr.isUsernameAvailable("admin")) {
            log.info("Creating initial admin account");
            register("admin", "admin");
        }
    }

    @Override
    public void stop() {
        isStopping = true;

        bus.getMain().publish(AppStopping.Signal);

        fedPush.stop();
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
    public String getDomain() {
        return cfg.getDomain();
    }

    @Override
    public ServerID getOrigin() {
        return origin;
    }

    @Override
    public boolean isLocal(ServerID sId) {
        return getOrigin().equals(sId);
    }

    @Override
    public SignalBus getBus() {
        return bus;
    }

    @Override
    public ChannelManager getChannelManager() {
        return chMgr;
    }

    @Override
    public ChannelDirectory getChannelDirectory() {
        return chDir;
    }

    @Override
    public EventService getEventService() {
        return evSvc;
    }

    @Override
    public EventStreamer getStreamer() {
        return streamer;
    }

    @Override
    public DataServerManager getServers() {
        return dsMgr;
    }

    @Override
    public FederationPusher getFedPusher() {
        return fedPush;
    }

    @Override
    public AuthService getAuth() {
        return authSvc;
    }

    @Override
    public UserSession withToken(String token) {
        return overGrid().forData().asClient().withToken(token);
    }

    @Override
    public String createSessionToken(String network, User usr) {
        String token = JWT.create()
                .withIssuer(cfg.getDomain())
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
    public boolean isLocal(UserID uId) {
        return idMgr.findUser(new GenericThreePid(GridType.of("id.net.grid"), uId.full())).isPresent();
    }

    @Override
    public GridServer overGrid() {
        return () -> new GridDataServer() {
            @Override
            public ServerSession asServer(String srvId) {
                return new ServerSession(MonolithGridifyServer.this, ServerID.parse(srvId));
            }

            @Override
            public GridDataServerClient asClient() {
                return new GridDataServerClient() {
                    @Override
                    public UserSession withToken(String token) {
                        User u = validateSessionToken(token);
                        return new UserSession(MonolithGridifyServer.this, "grid", u);
                    }

                    @Override
                    public UIAuthSession login() {
                        return MonolithGridifyServer.this.login("grid");
                    }

                    @Override
                    public UserSession login(User user) {
                        return withToken(createSessionToken("grid", user));
                    }

                    @Override
                    public UserSession login(UIAuthSession session) {
                        User u = MonolithGridifyServer.this.login(session, GridType.of("auth.id.password"));
                        return login(u);
                    }

                    @Override
                    public UserSession login(JsonObject doc) {
                        Optional<String> sessionId = GsonUtil.findString(doc, "session");
                        UIAuthSession session;
                        if (sessionId.isPresent()) {
                            session = getAuth().getSession(sessionId.get());
                        } else {
                            session = login();
                        }

                        session.complete(doc);
                        return login(session);
                    }

                    @Override
                    public UserSession login(String username, String password) {
                        JsonObject id = new JsonObject();
                        id.addProperty("type", GridType.id().internal().getId());
                        id.addProperty("value", username);
                        JsonObject doc = new JsonObject();
                        doc.addProperty("type", GridType.of("auth.id.password"));
                        doc.addProperty("password", password);
                        doc.add("identifier", id);
                        return login(doc);
                    }
                };
            }
        };
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
