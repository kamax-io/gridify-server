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
import com.google.gson.annotations.SerializedName;
import io.kamax.grid.gridepo.network.matrix.core.room.RoomMembership;
import io.kamax.grid.gridepo.util.GsonUtil;

import java.util.Optional;

public class BareMemberEvent extends BareEvent<BareMemberEvent.Content> {

    public static Optional<String> findMembership(JsonObject doc) {
        return GsonUtil
                .findObj(doc, EventKey.Content)
                .flatMap(c -> GsonUtil.findString(c, BareMemberEvent.Content.MembershipKey));
    }

    public static String computeMembership(JsonObject doc) {
        return findMembership(doc).orElseGet(RoomMembership.Leave::getId);
    }

    public static BareMemberEvent makeFor(String userId, RoomMembership membership) {
        BareMemberEvent ev = new BareMemberEvent();
        ev.setStateKey(userId);
        ev.getContent().setMembership(membership);
        return ev;
    }

    public static BareMemberEvent join(String userId) {
        BareMemberEvent ev = makeFor(userId, RoomMembership.Join);
        ev.setSender(userId);
        return ev;
    }

    public static BareMemberEvent leave(String userId) {
        return makeFor(userId, RoomMembership.Leave);
    }

    public static class Content {

        public static final String MembershipKey = "membership";

        @SerializedName(MembershipKey)
        private String membership;

        public String getMembership() {
            return membership;
        }

        public void setMembership(String membership) {
            this.membership = membership;
        }

        public void setMembership(RoomMembership m) {
            setMembership(m.getId());
        }

    }

    public BareMemberEvent() {
        setType(RoomEventType.Member);
        setStateKey("");
        setContent(new Content());
    }

}
