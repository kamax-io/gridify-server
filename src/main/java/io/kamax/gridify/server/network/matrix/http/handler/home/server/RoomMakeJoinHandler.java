package io.kamax.gridify.server.network.matrix.http.handler.home.server;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.http.Exchange;
import io.kamax.gridify.server.network.matrix.core.base.ServerSession;
import io.kamax.gridify.server.network.matrix.core.federation.RoomJoinTemplate;

import java.util.HashSet;
import java.util.Set;

public class RoomMakeJoinHandler extends AuthenticatedServerApiHandler {

    public RoomMakeJoinHandler(GridifyServer g) {
        super(g);
    }

    @Override
    protected void handle(ServerSession session, Exchange ex) {
        String roomId = ex.getPathVariable("roomId");
        String userId = ex.getPathVariable("userId");
        Set<String> roomVersions = new HashSet<>(ex.getQueryParameters("ver"));

        RoomJoinTemplate template = session.makeJoin(roomId, userId, roomVersions);
        JsonObject response = new JsonObject();
        response.addProperty("room_version", template.getRoomVersion());
        response.add("event", template.getEvent());

        ex.respond(response);
    }

}
