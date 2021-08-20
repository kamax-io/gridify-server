package io.kamax.gridify.server.network.grid.http.handler.matrix.home.client;

import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.http.Exchange;
import io.kamax.gridify.server.network.matrix.http.handler.home.client.ClientApiHandler;

public class PublicChannelListingHandler extends ClientApiHandler {

    private final GridifyServer g;

    public PublicChannelListingHandler(GridifyServer g) {
        this.g = g;
    }

    @Override
    protected void handle(Exchange exchange) {
        exchange.respondJson("{\"chunk\":[],\"next_batch\":\"\",\"prev_batch\":\"\",\"total_room_count_estimate\":0}");
    }

}
