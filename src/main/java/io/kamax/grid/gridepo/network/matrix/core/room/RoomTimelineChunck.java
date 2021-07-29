/*
 * Gridepo - Grid Data Server
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

package io.kamax.grid.gridepo.network.matrix.core.room;

import io.kamax.grid.gridepo.network.matrix.http.json.RoomEvent;

import java.util.Collections;
import java.util.List;

public class RoomTimelineChunck {

    private final String start;
    private final String end;
    private final List<RoomEvent> chunk;

    public RoomTimelineChunck(String start, String end, List<RoomEvent> chunk) {
        this.start = start;
        this.end = end;
        this.chunk = Collections.unmodifiableList(chunk);
    }

    public String getStart() {
        return start;
    }

    public String getEnd() {
        return end;
    }

    public List<RoomEvent> getChunk() {
        return chunk;
    }

}
