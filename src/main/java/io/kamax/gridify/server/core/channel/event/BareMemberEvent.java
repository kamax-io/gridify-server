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

package io.kamax.gridify.server.core.channel.event;

import io.kamax.gridify.server.core.channel.ChannelMembership;
import io.kamax.gridify.server.network.grid.core.UserID;

public class BareMemberEvent extends BareEvent<BareMemberEvent.Content> {

    public static BareMemberEvent joinAs(UserID uId) {
        BareMemberEvent ev = new BareMemberEvent();
        ev.setSender(uId.full());
        ev.setScope(uId.full());
        ev.getContent().setAction(ChannelMembership.Join);
        return ev;
    }

    public static BareMemberEvent inviteAs(UserID invitee, UserID inviter) {
        BareMemberEvent ev = new BareMemberEvent();
        ev.setSender(inviter.full());
        ev.setScope(invitee.full());
        ev.getContent().setAction(ChannelMembership.Invite);
        return ev;
    }

    public static class Content {

        private String action;

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public void setAction(ChannelMembership m) {
            setAction(m.getId());
        }

    }

    public BareMemberEvent() {
        setType(ChannelEventType.Member);
        setScope("");
        setContent(new Content());
    }

}
