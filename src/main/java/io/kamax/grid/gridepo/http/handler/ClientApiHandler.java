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

package io.kamax.grid.gridepo.http.handler;

import io.kamax.grid.gridepo.exception.InvalidTokenException;
import io.kamax.grid.gridepo.exception.MissingTokenException;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public abstract class ClientApiHandler implements HttpHandler {

    private transient final Logger log = LoggerFactory.getLogger(ClientApiHandler.class);

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        exchange.startBlocking();

        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
        } else {
            Exchange ex = new Exchange(exchange);
            try {
                // CORS headers as per spec
                exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Origin"), "*");
                exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Origin, X-Requested-With, Content-Type, Accept, Authorization");

                handle(ex);
            } catch (IllegalArgumentException e) {
                ex.respond(HttpStatus.SC_BAD_REQUEST, "M_INVALID_PARAM", e.getMessage());
            } catch (MissingTokenException e) {
                ex.respond(HttpStatus.SC_UNAUTHORIZED, "M_MISSING_TOKEN", e.getMessage());
            } catch (InvalidTokenException e) {
                ex.respond(HttpStatus.SC_UNAUTHORIZED, "M_UNKNOWN_TOKEN", e.getMessage());
            } catch (NotImplementedException e) {
                ex.respond(HttpStatus.SC_NOT_IMPLEMENTED, "M_NOT_IMPLEMENTED", e.getMessage());
            } catch (RuntimeException e) {
                log.error("Unknown error when handling {}", exchange.getRequestURL(), e);
                ex.respond(HttpStatus.SC_INTERNAL_SERVER_ERROR, ex.buildErrorBody("M_UNKNOWN",
                        StringUtils.defaultIfBlank(
                                e.getMessage(),
                                "An internal server error occurred. If this error persists, please contact support with reference #" +
                                        Instant.now().toEpochMilli()
                        )
                ));
            } finally {
                exchange.endExchange();
            }
        }
    }

    protected abstract void handle(Exchange exchange);

}