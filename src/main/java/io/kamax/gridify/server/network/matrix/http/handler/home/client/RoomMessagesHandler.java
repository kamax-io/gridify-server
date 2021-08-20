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

package io.kamax.gridify.server.network.matrix.http.handler.home.client;

import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.core.channel.TimelineDirection;
import io.kamax.gridify.server.http.Exchange;
import io.kamax.gridify.server.network.matrix.core.base.UserSession;
import io.kamax.gridify.server.network.matrix.core.room.RoomTimelineChunck;
import org.apache.commons.lang3.StringUtils;

public class RoomMessagesHandler extends AuthenticatedClientApiHandler {

    public RoomMessagesHandler(GridifyServer g) {
        super(g);
    }

    @Override
    protected void handle(UserSession session, Exchange ex) {
        String roomId = ex.getPathVariable("roomId");
        String anchor = ex.getQueryParameter("from");
        String dir = ex.getQueryParameter("dir");
        TimelineDirection direction = StringUtils.equals("b", dir) ? TimelineDirection.Backward : TimelineDirection.Forward;

        RoomTimelineChunck chunk = session.paginateTimeline(roomId, anchor, direction, 10);
        ex.respondJson(chunk);
    }

}
