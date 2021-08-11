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

package io.kamax.grid.gridepo.http;

import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.config.GridepoConfig;
import io.kamax.grid.gridepo.core.MonolithGridepo;
import io.kamax.grid.gridepo.network.grid.http.handler.grid.data.*;
import io.kamax.grid.gridepo.network.grid.http.handler.grid.data.channel.GetEventHandler;
import io.kamax.grid.gridepo.network.grid.http.handler.grid.identity.AuthHandler;
import io.kamax.grid.gridepo.network.grid.http.handler.grid.identity.LoginPostHandler;
import io.kamax.grid.gridepo.network.grid.http.handler.grid.identity.UserLookupHandler;
import io.kamax.grid.gridepo.network.matrix.http.handler.OptionsHandler;
import io.kamax.grid.gridepo.network.matrix.http.handler.UnrecognizedEndpointHandler;
import io.kamax.grid.gridepo.network.matrix.http.handler.home.MatrixHomeServerEndpointRegister;
import io.kamax.grid.gridepo.network.matrix.http.handler.home.MatrixhHomeClientEndpointRegister;
import io.kamax.grid.gridepo.network.matrix.http.handler.identity.HelloHandler;
import io.kamax.grid.gridepo.util.KxLog;
import io.kamax.grid.gridepo.util.TlsUtils;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.RoutingHandler;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public class MonolithHttpGridepo {

    static {
        // Used in XNIO package, dependency of Undertow
        // We switch to slf4j
        System.setProperty("org.jboss.logging.provider", "slf4j");
    }

    private static final Logger log = KxLog.make(MonolithHttpGridepo.class);

    private GridepoConfig cfg;
    private MonolithGridepo g;
    private Undertow u;

    public MonolithHttpGridepo(GridepoConfig cfg) {
        init(cfg);
    }

    private void init(GridepoConfig cfg) {
        this.cfg = cfg;
    }

    private void buildGridDataClient(RoutingHandler handler) {
        log.warn("Tried to add Grid Data client endpoints but not implemented yet");

        handler
                .add("OPTIONS", "/data/client", new OptionsHandler())
        ;
    }

    private void buildGridDataServer(RoutingHandler handler) {
        handler
                .get("/data/server/version", new VersionHandler())
                .post("/data/server/v0/do/approve/invite", new DoApproveInvite(g))
                .post("/data/server/v0/do/approve/join", new DoApproveJoin(g))
                .post("/data/server/v0/do/lookup/channel/alias", new ChannelDirectoryLookupHandler(g))
                .post("/data/server/v0/do/push", new DoPushHandler(g))

                .get("/data/server/v0/channels/{channelId}/events/{eventId}", new GetEventHandler(g))
        ;

        log.info("Added Grid Data server endpoints");
    }

    private void buildGridData(RoutingHandler handler, GridepoConfig.NetworkListener network) {
        if (StringUtils.equals("client", network.getApi())) {
            buildGridDataClient(handler);
        } else if (StringUtils.equals("server", network.getApi())) {
            buildGridDataServer(handler);
        } else {
            throw new RuntimeException(network.getApi() + " is not a supported Grid Data API");
        }
    }

    private void buildGridIdentityClient(RoutingHandler handler) {
        log.warn("Tried to add Grid Identity client endpoints but not implemented yet");

        handler
                .add("OPTIONS", "/identity/client", new OptionsHandler())
                .post("/identity/client/v0/do/auth", new AuthHandler(g))
                .get("/identity/client/v0/do/login", new io.kamax.grid.gridepo.network.grid.http.handler.grid.identity.LoginGetHandler(g)) // FIXME
                .post("/identity/client/v0/do/login", new LoginPostHandler(g))
        ;
    }

    private void buildGridIdentityServer(RoutingHandler handler) {
        handler
                .post("/identity/server/v0/do/lookup/user/threepid", new UserLookupHandler(g))
        ;
    }

    private void buildGridIdentity(RoutingHandler handler, GridepoConfig.NetworkListener network) {
        if (StringUtils.equals("client", network.getApi())) {
            buildGridIdentityClient(handler);
        } else if (StringUtils.equals("server", network.getApi())) {
            buildGridIdentityServer(handler);
        } else {
            throw new RuntimeException(network.getApi() + " is not a supported Grid Identity API");
        }
    }

    private void buildGrid(RoutingHandler handler, GridepoConfig.NetworkListener network) {
        if (StringUtils.equalsAny("data", network.getRole())) {
            buildGridData(handler, network);
        } else if (StringUtils.equalsAny("identity", network.getRole())) {
            buildGridIdentity(handler, network);
        } else {
            throw new RuntimeException(network.getRole() + " is not a supported Grid Role");
        }
    }

    private void buildMatrixHome(RoutingHandler handler, GridepoConfig.NetworkListener network) {
        if (StringUtils.equals("client", network.getApi())) {
            MatrixhHomeClientEndpointRegister.apply(g, handler);
            log.info("Added Matrix client endpoints");
        } else if (StringUtils.equals("server", network.getApi())) {
            MatrixHomeServerEndpointRegister.apply(g, handler);
            log.info("Added Matrix server endpoints");
        } else {
            throw new RuntimeException(network.getApi() + " is not a supported Matrix Home API");
        }

        handler.setFallbackHandler(new UnrecognizedEndpointHandler()).setInvalidMethodHandler(new UnrecognizedEndpointHandler());
    }

    private void buildMatrixIdentity(RoutingHandler handler, GridepoConfig.NetworkListener network) {
        log.warn("Tried to add Matrix Identity role but not implemented yet");

        HelloHandler helloHandler = new HelloHandler();

        handler
                // CORS support
                .add("OPTIONS", "/_matrix/identity/**", new OptionsHandler())
                .get(HelloHandler.Path, helloHandler)
                .get(HelloHandler.Path + "/", helloHandler) // Be lax with possibly trailing slash
        ;
    }

    private void buildMatrix(RoutingHandler handler, GridepoConfig.NetworkListener network) {
        if (StringUtils.equalsAny(network.getRole(), "home", "data")) {
            buildMatrixHome(handler, network);
        } else if (StringUtils.equals("identity", network.getRole())) {
            buildMatrixIdentity(handler, network);
        } else {
            throw new RuntimeException(network.getRole() + " is not a supported Matrix Role");
        }
    }

    private void build() {
        if (cfg.getListeners().isEmpty()) {
            log.info("No listener configured, adding default");
            GridepoConfig.Listener l = new GridepoConfig.Listener();
            l.setAddress("0.0.0.0");
            l.setPort(9009);
            cfg.getListeners().add(l);
        }

        for (GridepoConfig.Listener l : cfg.getListeners()) {
            if (Objects.isNull(l.getNetwork())) {
                log.info("Absent network configuration on listener {}:{}, adding default", l.getAddress(), l.getPort());
                l.setNetwork(new ArrayList<>());
                l.addNetwork(GridepoConfig.NetworkListener.build("grid", "data", "client"));
                l.addNetwork(GridepoConfig.NetworkListener.build("grid", "data", "server"));
                l.addNetwork(GridepoConfig.NetworkListener.build("grid", "identity", "client"));
                l.addNetwork(GridepoConfig.NetworkListener.build("grid", "identity", "server"));
                l.addNetwork(GridepoConfig.NetworkListener.build("matrix", "home", "client"));
                l.addNetwork(GridepoConfig.NetworkListener.build("matrix", "home", "server"));
                l.addNetwork(GridepoConfig.NetworkListener.build("matrix", "identity", "client"));
                l.addNetwork(GridepoConfig.NetworkListener.build("matrix", "identity", "server"));
            }
        }

        g = new MonolithGridepo(cfg);

        Undertow.Builder b = Undertow.builder();
        for (GridepoConfig.Listener cfg : cfg.getListeners()) {
            log.info("Creating HTTP listener on {}:{}", cfg.getAddress(), cfg.getPort());
            RoutingHandler handler = Handlers.routing();

            for (GridepoConfig.NetworkListener network : cfg.getNetwork()) {
                if (StringUtils.equals("grid", network.getProtocol())) {
                    buildGrid(handler, network);
                } else if (StringUtils.equals("matrix", network.getProtocol())) {
                    buildMatrix(handler, network);
                } else {
                    throw new RuntimeException(network.getProtocol() + " is not a supported listener protocol");
                }
            }

            if (cfg.isTls()) {
                log.info("Setting listener {}:{} as HTTPS", cfg.getAddress(), cfg.getPort());
                b.addHttpsListener(cfg.getPort(), cfg.getAddress(), TlsUtils.buildContext(cfg.getKey(), cfg.getCert()))
                        .setHandler(handler);
            } else {
                log.info("Setting listener {}:{} as HTTP", cfg.getAddress(), cfg.getPort());
                b.addHttpListener(cfg.getPort(), cfg.getAddress())
                        .setHandler(handler);
            }
        }

        u = b.build();
    }

    public Gridepo start() {
        build();

        g.start();
        u.start();

        return g;
    }

    public void stop() {
        try {
            ForkJoinPool.commonPool().submit(new RecursiveAction() {
                @Override
                protected void compute() {
                    invokeAll(new RecursiveAction() {
                        @Override
                        protected void compute() {
                            // Protect against early exception and then null pointer
                            if (Objects.nonNull(u)) {
                                u.stop();
                            }
                        }
                    }, new RecursiveAction() {
                        @Override
                        protected void compute() {
                            // Protect against early exception and then null pointer
                            if (Objects.nonNull(g)) {
                                g.stop();
                            }
                        }
                    });
                }
            }).get();
        } catch (InterruptedException e) {
            log.info("Shutdown is dirty: interrupted while waiting for components to stop");
        } catch (ExecutionException e) {
            log.info("Shutdown is dirty: unknown failure while waiting for components to stop", e.getCause());
        }
    }

}
