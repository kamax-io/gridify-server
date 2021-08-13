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

package io.kamax.gridify.server.network.matrix.http.json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SyncResponse {

    public static class RoomState {

        public List<RoomEvent> events = new ArrayList<>();

        public List<RoomEvent> getEvents() {
            return events;
        }

        public void setEvents(List<RoomEvent> events) {
            this.events = events;
        }

    }

    public static class RoomTimeline {

        public List<RoomEvent> events = new ArrayList<>();
        public String prevBatch;
        public boolean limited = false;

        public List<RoomEvent> getEvents() {
            return events;
        }

        public void setEvents(List<RoomEvent> events) {
            this.events = events;
        }

        public String getPrevBatch() {
            return prevBatch;
        }

        public void setPrevBatch(String prevBatch) {
            this.prevBatch = prevBatch;
        }

        public boolean isLimited() {
            return limited;
        }

        public void setLimited(boolean limited) {
            this.limited = limited;
        }

    }

    public static class Room {

        public RoomState state = new RoomState();
        public RoomTimeline timeline = new RoomTimeline();
        public RoomState inviteState = new RoomState();

        public RoomState getState() {
            return state;
        }

        public void setState(RoomState state) {
            this.state = state;
        }

        public RoomTimeline getTimeline() {
            return timeline;
        }

        public void setTimeline(RoomTimeline timeline) {
            this.timeline = timeline;
        }

    }

    public static class Rooms {

        public Map<String, Room> invite = new HashMap<>();
        public Map<String, Room> join = new HashMap<>();
        public Map<String, Room> leave = new HashMap<>();

        public Map<String, Room> getInvite() {
            return invite;
        }

        public void setInvite(Map<String, Room> invite) {
            this.invite = invite;
        }

        public Map<String, Room> getJoin() {
            return join;
        }

        public void setJoin(Map<String, Room> join) {
            this.join = join;
        }

        public Map<String, Room> getLeave() {
            return leave;
        }

        public void setLeave(Map<String, Room> leave) {
            this.leave = leave;
        }

    }

    public String nextBatch = "";
    public final Rooms rooms = new Rooms();

}
