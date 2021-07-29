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

import io.kamax.grid.gridepo.network.matrix.core.room.RoomMembership;

import java.time.Instant;

public class BareMemberEvent extends BareEvent<BareMemberEvent.Content> {

    public static BareMemberEvent leave(String userId) {
        BareMemberEvent ev = new BareMemberEvent();
        ev.setSender(userId);
        ev.setStateKey(userId);
        ev.getContent().setMembership(RoomMembership.Leave);
        return ev;
    }

    public static class Content {

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
        setTimestamp(Instant.now().toEpochMilli());
        setContent(new BareMemberEvent.Content());
    }

}
