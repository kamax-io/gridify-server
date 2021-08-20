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

package io.kamax.gridify.server.http.admin.handler;

import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.exception.*;
import io.kamax.gridify.server.http.Exchange;
import io.kamax.gridify.server.network.matrix.core.MatrixException;
import io.kamax.gridify.server.util.KxLog;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

public abstract class AdminHandler implements HttpHandler {

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    protected final GridifyServer g;
    private String contentType;

    public AdminHandler(GridifyServer g) {
        this.g = g;
    }

    protected void requireContentType(String contentType) {
        this.contentType = contentType;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.startBlocking();

        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
        } else {
            Exchange ex = new Exchange(exchange);
            try {
                // CORS headers as per spec
                // FIXME set origin value properly
                exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Origin"), "*");
                exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Origin, X-Requested-With, Content-Type, Accept, Authorization");

                if (StringUtils.isBlank(contentType) || StringUtils.startsWith(ex.getContentType(), contentType)) {
                    handle(ex);
                } else {
                    throw new MatrixException(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type", "Request sent data in a format which is not understood");
                }
            } catch (MatrixException e) {
                ex.respond(e.getCode(), e.getErrCode(), e.getError());
                log.debug("Trigger:", e);
            } catch (IllegalArgumentException e) {
                ex.respond(HttpStatus.SC_BAD_REQUEST, "G_INVALID_PARAM", e.getMessage());
                log.debug("Trigger:", e);
            } catch (MissingTokenException e) {
                ex.respond(HttpStatus.SC_UNAUTHORIZED, "G_MISSING_TOKEN", e.getMessage());
                log.debug("Trigger:", e);
            } catch (InvalidTokenException e) {
                ex.respond(HttpStatus.SC_UNAUTHORIZED, "G_UNKNOWN_TOKEN", e.getMessage());
                log.debug("Trigger:", e);
            } catch (ForbiddenException e) {
                ex.respond(HttpStatus.SC_FORBIDDEN, "G_FORBIDDEN", e.getReason());
                log.debug("Trigger:", e);
            } catch (ObjectNotFoundException e) {
                ex.respond(HttpStatus.SC_NOT_FOUND, "G_NOT_FOUND", e.getMessage());
                log.debug("Trigger:", e);
            } catch (NotImplementedException e) {
                ex.respond(HttpStatus.SC_NOT_IMPLEMENTED, "G_NOT_IMPLEMENTED", e.getMessage());
                log.debug("Trigger:", e);
            } catch (EntityUnreachableException e) {
                ex.respond(523, "ORG_GRIDIFY_ENTITY_UNREACHABLE", e.getMessage());
                log.debug("Trigger:", e);
            } catch (RemoteServerException e) {
                String code = e.getCode();
                ex.respond(HttpStatus.SC_BAD_GATEWAY, code, e.getReason());
                log.debug("Trigger:", e);
            } catch (RuntimeException | Error e) {
                log.error("Unknown error when handling {} - CHECK THE SURROUNDING LOG LINES TO KNOW THE ACTUAL CAUSE!", exchange.getRequestURL(), e);
                ex.respond(HttpStatus.SC_INTERNAL_SERVER_ERROR, ex.buildErrorBody("G_UNKNOWN",
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

    public void serveResource(Exchange ex) {
        serveResource(ex, ex.getUnderlying().getRequestPath());
    }

    public void serveResource(Exchange ex, String logicalPath) {
        String contentType = "application/octet-stream";
        String basePath = "/web/root" + logicalPath;
        String path = basePath;
        log.debug("Path: {}", path);
        InputStream elIs;
        if (StringUtils.endsWith(basePath, "/")) {
            path = basePath + "index.html";
            elIs = getClass().getResourceAsStream(path);
        } else {
            elIs = getClass().getResourceAsStream(path);
        }

        if (Objects.isNull(elIs)) {
            path = basePath + ".html";
            elIs = getClass().getResourceAsStream(path);
        }

        if (Objects.isNull(elIs)) {
            log.debug("Could not find resource at {}", path);
            ex.getUnderlying().setStatusCode(404);
            ex.getUnderlying().getResponseSender().send("Not found", StandardCharsets.UTF_8);
            ex.getUnderlying().endExchange();
            return;
        }
        try {
            try {
                if (path.endsWith(".html")) {
                    contentType = "text/html";
                }
                if (path.endsWith(".svg")) {
                    contentType = "image/svg+xml";
                }
                ex.getUnderlying().getResponseHeaders().put(HttpString.tryFromString("Content-Type"), contentType);
                IOUtils.copy(elIs, ex.getUnderlying().getOutputStream());
                ex.getUnderlying().endExchange();
            } finally {
                elIs.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract void handle(Exchange ex) throws Exception;

}
