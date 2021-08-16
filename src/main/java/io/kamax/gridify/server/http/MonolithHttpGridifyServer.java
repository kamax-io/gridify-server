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

package io.kamax.gridify.server.http;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.config.GridifyConfig;
import io.kamax.gridify.server.core.MonolithGridifyServer;
import io.kamax.gridify.server.http.handler.Exchange;
import io.kamax.gridify.server.network.grid.http.handler.grid.data.*;
import io.kamax.gridify.server.network.grid.http.handler.grid.data.channel.GetEventHandler;
import io.kamax.gridify.server.network.grid.http.handler.grid.identity.AuthHandler;
import io.kamax.gridify.server.network.grid.http.handler.grid.identity.LoginGetHandler;
import io.kamax.gridify.server.network.grid.http.handler.grid.identity.LoginPostHandler;
import io.kamax.gridify.server.network.grid.http.handler.grid.identity.UserLookupHandler;
import io.kamax.gridify.server.network.matrix.http.handler.OptionsHandler;
import io.kamax.gridify.server.network.matrix.http.handler.UnrecognizedEndpointHandler;
import io.kamax.gridify.server.network.matrix.http.handler.home.MatrixHomeClientEndpointRegister;
import io.kamax.gridify.server.network.matrix.http.handler.home.MatrixHomeServerEndpointRegister;
import io.kamax.gridify.server.network.matrix.http.handler.identity.HelloHandler;
import io.kamax.gridify.server.util.GsonUtil;
import io.kamax.gridify.server.util.KxLog;
import io.kamax.gridify.server.util.TlsUtils;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.RedirectHandler;
import io.undertow.util.HttpString;
import io.undertow.util.QueryParameterUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public class MonolithHttpGridifyServer {

    static {
        // Used in XNIO package, dependency of Undertow
        // We switch to slf4j
        System.setProperty("org.jboss.logging.provider", "slf4j");
    }

    private static final Logger log = KxLog.make(MonolithHttpGridifyServer.class);

    private GridifyConfig cfg;
    private MonolithGridifyServer g;
    private Undertow u;

    public MonolithHttpGridifyServer(GridifyConfig cfg) {
        init(cfg);
    }

    private void init(GridifyConfig cfg) {
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

    private void buildGridData(RoutingHandler handler, GridifyConfig.NetworkListener network) {
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
                .get("/identity/client/v0/do/login", new LoginGetHandler(g)) // FIXME
                .post("/identity/client/v0/do/login", new LoginPostHandler(g))
        ;
    }

    private void buildGridIdentityServer(RoutingHandler handler) {
        handler
                .post("/identity/server/v0/do/lookup/user/threepid", new UserLookupHandler(g))
        ;
    }

    private void buildGridIdentity(RoutingHandler handler, GridifyConfig.NetworkListener network) {
        if (StringUtils.equals("client", network.getApi())) {
            buildGridIdentityClient(handler);
        } else if (StringUtils.equals("server", network.getApi())) {
            buildGridIdentityServer(handler);
        } else {
            throw new RuntimeException(network.getApi() + " is not a supported Grid Identity API");
        }
    }

    private void buildGrid(RoutingHandler handler, GridifyConfig.NetworkListener network) {
        if (StringUtils.equalsAny("data", network.getRole())) {
            buildGridData(handler, network);
        } else if (StringUtils.equalsAny("identity", network.getRole())) {
            buildGridIdentity(handler, network);
        } else {
            throw new RuntimeException(network.getRole() + " is not a supported Grid Role");
        }
    }

    private void buildMatrixHome(RoutingHandler handler, GridifyConfig.NetworkListener network) {
        if (StringUtils.equals("client", network.getApi())) {
            MatrixHomeClientEndpointRegister.apply(g, handler);
            log.info("Added Matrix client endpoints");
        } else if (StringUtils.equals("server", network.getApi())) {
            MatrixHomeServerEndpointRegister.apply(g, handler);
            log.info("Added Matrix server endpoints");
        } else {
            throw new RuntimeException(network.getApi() + " is not a supported Matrix Home API");
        }

        handler.setFallbackHandler(new UnrecognizedEndpointHandler()).setInvalidMethodHandler(new UnrecognizedEndpointHandler());
    }

    private void buildMatrixIdentity(RoutingHandler handler, GridifyConfig.NetworkListener network) {
        log.warn("Tried to add Matrix Identity role but not implemented yet");

        HelloHandler helloHandler = new HelloHandler();

        handler
                // CORS support
                .add("OPTIONS", "/_matrix/identity/**", new OptionsHandler())
                .get(HelloHandler.Path, helloHandler)
                .get(HelloHandler.Path + "/", helloHandler) // Be lax with possibly trailing slash
        ;
    }

    private void buildMatrix(RoutingHandler handler, GridifyConfig.NetworkListener network) {
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
            GridifyConfig.Listener l = new GridifyConfig.Listener();
            l.setAddress("0.0.0.0");
            l.setPort(9009);
            cfg.getListeners().add(l);
        }

        for (GridifyConfig.Listener l : cfg.getListeners()) {
            if (Objects.isNull(l.getNetwork())) {
                log.info("Absent network configuration on listener {}:{}, adding default", l.getAddress(), l.getPort());
                l.setNetwork(new ArrayList<>());
                //l.addNetwork(GridifyConfig.NetworkListener.build("grid", "data", "client"));
                //l.addNetwork(GridifyConfig.NetworkListener.build("grid", "data", "server"));
                //l.addNetwork(GridifyConfig.NetworkListener.build("grid", "identity", "client"));
                //l.addNetwork(GridifyConfig.NetworkListener.build("grid", "identity", "server"));
                l.addNetwork(GridifyConfig.NetworkListener.build("matrix", "home", "client"));
                l.addNetwork(GridifyConfig.NetworkListener.build("matrix", "home", "server"));
                //l.addNetwork(GridifyConfig.NetworkListener.build("matrix", "identity", "client"));
                //l.addNetwork(GridifyConfig.NetworkListener.build("matrix", "identity", "server"));
            }
        }

        g = new MonolithGridifyServer(cfg);

        Undertow.Builder b = Undertow.builder();
        for (GridifyConfig.Listener cfg : cfg.getListeners()) {
            log.info("Creating HTTP listener on {}:{}", cfg.getAddress(), cfg.getPort());
            RoutingHandler handler = Handlers.routing();

            for (GridifyConfig.NetworkListener network : cfg.getNetwork()) {
                if (StringUtils.equals("grid", network.getProtocol())) {
                    buildGrid(handler, network);
                } else if (StringUtils.equals("matrix", network.getProtocol())) {
                    buildMatrix(handler, network);
                } else {
                    throw new RuntimeException(network.getProtocol() + " is not a supported listener protocol");
                }
            }

            handler.post("/admin/firstRunWizard", new BlockingHandler(exchange -> {
                Exchange ex = new Exchange(exchange);
                if (g.getIdentity().hasUsers()) {
                    throw new IllegalStateException("Server is already setup");
                }

                JsonObject body = new JsonObject();
                String reqContentType = ex.getContentType().orElse("application/octet-stream");
                String bodyRaw = ex.getBodyUtf8();
                if (StringUtils.startsWith(reqContentType, "application/x-www-form-urlencoded")) {
                    Map<String, Deque<String>> parms = QueryParameterUtils.parseQueryString(bodyRaw, StandardCharsets.UTF_8.name());
                    for (Map.Entry<String, Deque<String>> entry : parms.entrySet()) {
                        if (entry.getValue().size() <= 0) {
                            return;
                        }

                        if (entry.getValue().size() > 1) {
                            body.add(entry.getKey(), GsonUtil.asArray(entry.getValue()));
                        } else {
                            body.addProperty(entry.getKey(), entry.getValue().peekFirst());
                        }
                    }
                } else if (StringUtils.startsWith(reqContentType, "application/json")) {
                    body = GsonUtil.parseObj(bodyRaw);
                }

                g.setup(body);

                new RedirectHandler("/").handleRequest(exchange);
            }));

            handler.get("/admin", new RedirectHandler("/admin/"));
            handler.get("/admin/**", new HttpHandler() {
                @Override
                public void handleRequest(HttpServerExchange exchange) throws Exception {
                    String path = "/html" + exchange.getRequestPath();
                    log.debug("Path: {}", path);
                    InputStream elIs = getClass().getResourceAsStream(path);
                    if (Objects.isNull(elIs)) {
                        String htmlPath = path + ".html";
                        elIs = getClass().getResourceAsStream(htmlPath);
                    }
                    if (Objects.isNull(elIs)) {
                        String indexPath = path + "/index.html";
                        elIs = getClass().getResourceAsStream(indexPath);
                    }
                    if (Objects.isNull(elIs)) {
                        exchange.setStatusCode(404);
                        exchange.getResponseSender().send("Not found", StandardCharsets.UTF_8);
                        exchange.endExchange();
                        return;
                    }
                    try {
                        String data = IOUtils.toString(Objects.requireNonNull(elIs), StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().put(HttpString.tryFromString("Content-Type"), "text/html");
                        exchange.getResponseSender().send(data, StandardCharsets.UTF_8);
                        exchange.endExchange();
                    } finally {
                        elIs.close();
                    }
                }
            });

            handler.get("/", new HttpHandler() {
                @Override
                public void handleRequest(HttpServerExchange exchange) throws Exception {
                    if (!g.getIdentity().hasUsers()) {
                        new RedirectHandler("/admin/firstRunWizard").handleRequest(exchange);
                    } else {
                        log.info("Serving homepage");
                        try (InputStream elIs = getClass().getResourceAsStream("/html/index.html")) {
                            String data = IOUtils.toString(Objects.requireNonNull(elIs), StandardCharsets.UTF_8);
                            exchange.getResponseHeaders().put(HttpString.tryFromString("Content-Type"), "text/html");
                            exchange.getResponseSender().send(data, StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            });

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

    public GridifyServer start() {
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
