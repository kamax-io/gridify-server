package io.kamax.gridify.server.network.matrix.http.handler.home.server;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.http.Exchange;
import io.kamax.gridify.server.network.matrix.core.MatrixServer;
import io.undertow.util.Headers;
import org.apache.commons.lang.StringUtils;

public class WellKnownHandler extends ServerApiHandler {

    public WellKnownHandler(GridifyServer g) {
        this.g = g;
    }

    @Override
    protected void handle(Exchange ex) {
        MatrixServer srv = g.overMatrix().forDomain(ex.requireHost());
        String host = srv.id().getHost();
        // Check if we have a port in it
        int i = host.lastIndexOf(":");
        // Check if this is an IPv6 address
        int j = host.lastIndexOf("]");
        if (j > i || i == -1) {
            // No port, add it
            String port = ex.getHeader(Headers.X_FORWARDED_PORT_STRING);
            if (StringUtils.isBlank(port)) {
                String proto = ex.getHeader(Headers.X_FORWARDED_PROTO_STRING);
                if (StringUtils.isBlank(proto)) {
                    host += ":8448";
                } else if (StringUtils.equals("https", proto)) {
                    host += ":443";
                } else {
                    host += ":80";
                }
            } else {
                host += ":" + port;
            }
        }

        JsonObject body = new JsonObject();
        body.addProperty("m.server", host);
        ex.respond(body);
    }

}
