/*
 * Gridify Server
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

package io.kamax.gridify.server.core.channel;

import io.kamax.gridify.server.core.store.DataStore;
import io.kamax.gridify.server.network.grid.core.EventID;

public class ChannelTimeline {

    private long id;
    private DataStore store;

    public ChannelTimeline(long id, DataStore store) {
        this.id = id;
        this.store = store;
    }

    public TimelineChunk getNext(EventID evId, long amount) {
        long eTid = store.getEventTid(id, evId.full());
        TimelineChunk chunk = new TimelineChunk();
        chunk.setStart(evId.full());
        chunk.setEnd(evId.full());
        chunk.setEvents(store.getTimelineNext(id, eTid, amount));
        if (!chunk.getEvents().isEmpty()) {
            chunk.setEnd(chunk.getEvents().get(chunk.getEvents().size() - 1).getId());
        }
        return chunk;
    }

    public TimelineChunk getPrevious(EventID evId, long amount) {
        long eTid = store.getEventTid(id, evId.full());
        TimelineChunk chunk = new TimelineChunk();
        chunk.setStart(evId.full());
        chunk.setEnd(evId.full());
        chunk.setEvents(store.getTimelinePrevious(id, eTid, amount));
        if (!chunk.getEvents().isEmpty()) {
            chunk.setEnd(chunk.getEvents().get(chunk.getEvents().size() - 1).getId());
        }
        return chunk;
    }

}
