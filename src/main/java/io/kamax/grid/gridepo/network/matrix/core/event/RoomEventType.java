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

package io.kamax.grid.gridepo.network.matrix.core.event;

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.apache.commons.lang3.StringUtils;

public enum RoomEventType {

    Address("m.room.canonical_alias"),
    Alias("m.room.aliases"),
    Create("m.room.create"),
    HistoryVisibility("m.room.history_visibility"),
    JoinRules("m.room.join_rules"),
    Member("m.room.member"),
    Message("m.room.message"),
    Name("m.room.name"),
    Power("m.room.power_levels"),
    Topic("m.room.topic");

    private String id;

    RoomEventType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public boolean match(String id) {
        return StringUtils.equals(this.id, id);
    }

    public boolean match(JsonObject event) {
        return match(GsonUtil.findString(event, EventKey.Type).orElse(""));
    }

}
