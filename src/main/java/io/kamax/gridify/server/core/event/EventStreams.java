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

package io.kamax.gridify.server.core.event;

public class EventStreams {

    private static final EventStreamID gc = new EventStreamID("g:c", "");
    private static final EventStreamID mr = new EventStreamID("m:r", "");

    public static EventStreamID GridChannels() {
        return gc;
    }

    public static EventStreamID MatrixRooms() {
        return mr;
    }

    public static EventStreamID MatrixUserAccount(String userId) {
        return new EventStreamID("m:u:a", userId);
    }

    public static EventStreamID MatrixUserProfile(String userId) {
        return new EventStreamID("m:u:p", userId);
    }

    private EventStreams() {
        // Nope
    }

}
