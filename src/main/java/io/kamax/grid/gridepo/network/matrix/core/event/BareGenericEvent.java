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

import java.util.List;

public class BareGenericEvent extends BareEvent<JsonObject> {

    public static BareGenericEvent fromJson(JsonObject doc) {
        return GsonUtil.fromJson(doc, BareGenericEvent.class);
    }

    public static String extractRoomId(JsonObject doc) {
        return GsonUtil.findString(doc, EventKey.RoomId).orElse("");
    }

    public static Long extractDepth(JsonObject doc) {
        return GsonUtil.getLong(doc, EventKey.Depth);
    }

    public static Long extractTimestampt(JsonObject doc) {
        return GsonUtil.getLong(doc, EventKey.Timestamp);
    }

    public static List<String> getAuthEvents(JsonObject doc) {
        return GsonUtil.tryArrayAsList(doc, EventKey.AuthEvents, String.class);
    }

    public static List<String> getPrevEvents(JsonObject doc) {
        return GsonUtil.tryArrayAsList(doc, EventKey.PrevEvents, String.class);
    }

    public BareGenericEvent() {
        setContent(new JsonObject());
    }

}
