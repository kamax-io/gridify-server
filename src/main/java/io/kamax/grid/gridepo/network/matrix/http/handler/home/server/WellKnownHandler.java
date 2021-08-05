package io.kamax.grid.gridepo.network.matrix.http.handler.home.server;

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.http.handler.Exchange;
import io.kamax.grid.gridepo.util.KxLog;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;

public class WellKnownHandler extends ServerApiHandler {

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    public WellKnownHandler(Gridepo g) {
        // nope
    }

    @Override
    protected void handle(Exchange ex) {
        String host = ex.requireHost();

        // Check if we have a port in it
        int i = host.lastIndexOf(":");
        // Check if this is an IPv6 address
        int j = host.lastIndexOf("]");
        if (j > i || i == -1) {
            // No port, add it
            host += ":443";
        }

        JsonObject body = new JsonObject();
        body.addProperty("m.server", host);
        ex.respond(body);
    }

}
