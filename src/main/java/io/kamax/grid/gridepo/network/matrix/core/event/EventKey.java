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

public class EventKey {

    public static final String Id = "event_id";
    public static final String ChannelId = "room_id";
    public static final String Type = "type";
    public static final String Scope = "state_key";
    public static final String Origin = "origin";
    public static final String Sender = "sender";
    public static final String PrevEvents = "prev_events";
    public static final String Depth = "depth";
    public static final String Timestamp = "origin_server_ts";
    public static final String Content = "content";
    public static final String Hashes = "hashes";
    public static final String Signatures = "signatures";

}
