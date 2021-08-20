package io.kamax.gridify.server.network.matrix.http.handler.home.server;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.http.Exchange;
import io.kamax.gridify.server.network.matrix.core.base.ServerSession;
import io.kamax.gridify.server.network.matrix.core.room.RoomJoinSeed;

public class RoomSendJoinHandler extends AuthenticatedServerApiHandler {

    public RoomSendJoinHandler(GridifyServer g) {
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
