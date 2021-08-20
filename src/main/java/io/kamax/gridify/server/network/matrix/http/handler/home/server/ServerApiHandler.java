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

package io.kamax.gridify.server.network.matrix.http.handler.home.server;

import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.exception.*;
import io.kamax.gridify.server.http.handler.Exchange;
import io.kamax.gridify.server.network.matrix.core.MatrixDataServer;
import io.kamax.gridify.server.network.matrix.core.MatrixException;
import io.kamax.gridify.server.network.matrix.core.base.ServerSession;
import io.kamax.gridify.server.network.matrix.core.federation.HomeServerRequest;
import io.kamax.gridify.server.util.KxLog;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.time.Instant;

public abstract class ServerApiHandler implements HttpHandler {

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    protected GridifyServer g;

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        exchange.startBlocking();

        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
        } else {
            Exchange ex = new Exchange(exchange);
            try {
                // CORS headers
                exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Origin"), "*");
                exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Origin, X-Requested-With, Content-Type, Accept, Authorization");

                handle(ex);
            } catch (MatrixException e) {
                ex.respond(e.getCode(), e.getErrCode(), e.getError());
                log.debug("Trigger:", e);
            } catch (IllegalArgumentException e) {
                ex.respond(HttpStatus.SC_BAD_REQUEST, "M_INVALID_PARAM", e.getMessage());
                log.debug("Trigger:", e);
            } catch (MissingTokenException e) {
                ex.respond(HttpStatus.SC_UNAUTHORIZED, "M_MISSING_TOKEN", e.getMessage());
                log.debug("Trigger:", e);
            } catch (InvalidTokenException e) {
                ex.respond(HttpStatus.SC_UNAUTHORIZED, "M_UNKNOWN_TOKEN", e.getMessage());
                log.debug("Trigger:", e);
            } catch (ForbiddenException e) {
                ex.respond(HttpStatus.SC_FORBIDDEN, "M_FORBIDDEN", e.getReason());
                log.debug("Trigger:", e);
            } catch (ObjectNotFoundException e) {
                ex.respond(HttpStatus.SC_NOT_FOUND, "M_NOT_FOUND", e.getMessage());
                log.debug("Trigger:", e);
            } catch (NotImplementedException e) {
                ex.respond(HttpStatus.SC_NOT_IMPLEMENTED, "M_NOT_IMPLEMENTED", e.getMessage());
                log.debug("Trigger:", e);
            } catch (EntityUnreachableException e) {
                ex.respond(523, "ORG_GRIDIFY_ENTITY_UNREACHABLE", e.getMessage());
                log.debug("Trigger:", e);
            } catch (RemoteServerException e) {
                String code = e.getCode();
                if (StringUtils.startsWith(code, "G_")) {
                    code = "M_" + code.substring(2); // TODO Generic transform, be smarter about it
                }
                ex.respond(HttpStatus.SC_BAD_GATEWAY, code, e.getReason());
                log.debug("Trigger:", e);
            } catch (RuntimeException | Error e) {
                log.error("Unknown error when handling {} - CHECK THE SURROUNDING LOG LINES TO KNOW THE ACTUAL CAUSE!", exchange.getRequestURL(), e);
                ex.respond(HttpStatus.SC_INTERNAL_SERVER_ERROR, ex.buildErrorBody("M_UNKNOWN",
                        StringUtils.defaultIfBlank(
                                e.getMessage(),
                                "An internal server error occurred. Contact your system administrator with Log Reference " +
                                        Instant.now().toEpochMilli()
                        )
                ));
            } finally {
                exchange.endExchange();
            }

            // TODO refactor the common code from the various API handlers into a single class
            if (log.isInfoEnabled()) {
                String remotePeer = exchange.getConnection().getPeerAddress(InetSocketAddress.class).getAddress().getHostAddress();
                String method = exchange.getRequestMethod().toString();
                String path = exchange.getRequestURI();
                int statusCode = exchange.getStatusCode();
                long writtenByes = exchange.getResponseBytesSent();

                if (StringUtils.isEmpty(ex.getError())) {
                    log.info("{} - {} {} - {} - {}", remotePeer, method, path, statusCode, writtenByes);
                } else {
                    log.info("{} - {} {} - {} - {} - {}", remotePeer, method, path, statusCode, writtenByes, ex.getError());
                }
            }
        }
    }

    protected MatrixDataServer getVhostServer(GridifyServer g, Exchange ex) {
        return g.overMatrix().vHost(ex.requireHost()).asServer();
    }

    protected ServerSession getAuthenticatedSession(GridifyServer g, Exchange ex) {
        HomeServerRequest request = new HomeServerRequest();
        String authHeader = ex.getHeader(Headers.AUTHORIZATION_STRING);
        if (!StringUtils.startsWith(authHeader, "X-Matrix ")) {
            throw new UnauthenticatedException(null); // FIXME
        }
        for (String arg : StringUtils.split(authHeader, ",")) {
            if (StringUtils.startsWith(arg, "origin=")) {
                request.getDoc().setOrigin(StringUtils.substringAfter(arg, "="));
            }
            // TODO complete
        }

        return getVhostServer(g, ex).forRequest(request);
    }

    protected abstract void handle(Exchange ex);

}
