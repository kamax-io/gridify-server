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

package io.kamax.gridify.server.network.matrix.core.event;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import io.kamax.gridify.server.util.GsonUtil;

import java.util.Optional;

public class BareCreateEvent extends BareEvent<BareCreateEvent.Content> {

    public static BareCreateEvent withCreator(String uId) {
        BareCreateEvent ev = new BareCreateEvent();
        ev.setSender(uId);
        ev.getContent().setCreator(uId);
        return ev;
    }

    public static Optional<String> findVersion(JsonObject event) {
        return GsonUtil.findObj(event, EventKey.Content).flatMap(o -> GsonUtil.findString(o, Content.RoomVersion));
    }

    public static class Content {

        public static Content forUnknownCreator() {
            Content c = new Content();
            c.setCreator("");
            return c;
        }

        public static final String RoomVersion = "room_version";
        public static final String Federate = "m.federate";

        private String creator;
        @SerializedName(RoomVersion)
        private String version;
        @SerializedName(Federate)
        private Boolean federate;

        public String getCreator() {
            return creator;
        }

        public void setCreator(String creator) {
            this.creator = creator;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

    }

    public BareCreateEvent() {
        setType(RoomEventType.Create);
        setStateKey("");
        setContent(new Content());
    }

}
