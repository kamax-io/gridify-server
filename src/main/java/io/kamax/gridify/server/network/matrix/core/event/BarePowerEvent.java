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

import java.util.HashMap;
import java.util.Map;

public class BarePowerEvent extends BareEvent<BarePowerEvent.Content> {

    public static class Content {

        private Long ban;
        private Map<String, Long> events = new HashMap<>();
        private Long eventsDefault;
        private Long invite;
        private Long kick;
        private Map<String, Long> notifications = new HashMap<>();
        private Long redact;
        private Long stateDefault;
        private Map<String, Long> users = new HashMap<>();
        private Long usersDefault;

        public Long getBan() {
            return ban;
        }

        public void setBan(Long ban) {
            this.ban = ban;
        }

        public Map<String, Long> getEvents() {
            return events;
        }

        public void setEvents(Map<String, Long> events) {
            this.events = events;
        }

        public Long getEventsDefault() {
            return eventsDefault;
        }

        public void setEventsDefault(Long eventsDefault) {
            this.eventsDefault = eventsDefault;
        }

        public Long getInvite() {
            return invite;
        }

        public void setInvite(Long invite) {
            this.invite = invite;
        }

        public Long getKick() {
            return kick;
        }

        public void setKick(Long kick) {
            this.kick = kick;
        }

        public Map<String, Long> getNotifications() {
            return notifications;
        }

        public void setNotifications(Map<String, Long> notifications) {
            this.notifications = notifications;
        }

        public Long getRedact() {
            return redact;
        }

        public void setRedact(Long redact) {
            this.redact = redact;
        }

        public Long getStateDefault() {
            return stateDefault;
        }

        public void setStateDefault(Long stateDefault) {
            this.stateDefault = stateDefault;
        }

        public Map<String, Long> getUsers() {
            return users;
        }

        public void setUsers(Map<String, Long> users) {
            this.users = users;
        }

        public Long getUsersDefault() {
            return usersDefault;
        }

        public void setUsersDefault(Long usersDefault) {
            this.usersDefault = usersDefault;
        }

    }

    public BarePowerEvent() {
        setType(RoomEventType.Power);
        setStateKey("");
        setContent(new Content());
    }

}
