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
        JsonObject body = new JsonObject();
        body.addProperty("m.server", ex.getHeader("Host") + ":443");
        ex.respond(body);
    }

}
