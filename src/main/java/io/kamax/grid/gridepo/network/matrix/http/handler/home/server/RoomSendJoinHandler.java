package io.kamax.grid.gridepo.network.matrix.http.handler.home.server;

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.http.handler.Exchange;
import io.kamax.grid.gridepo.network.matrix.core.base.ServerSession;
import io.kamax.grid.gridepo.network.matrix.core.room.RoomJoinSeed;

public class RoomSendJoinHandler extends AuthenticatedServerApiHandler {

    public RoomSendJoinHandler(Gridepo g) {
        super(g);
    }

    @Override
    protected void handle(ServerSession session, Exchange ex) {
        String roomId = ex.getPathVariable("roomId");
        String userId = ex.getPathVariable("userId");
        JsonObject joinEvent = ex.parseJsonObject();
        RoomJoinSeed seed = session.sendJoin(roomId, userId, joinEvent);
        ex.respondJson(seed);
    }

}
