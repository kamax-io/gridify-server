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

package io.kamax.grid.gridepo.network.matrix.core.room;

import io.kamax.grid.gridepo.core.EntityID;
import org.apache.commons.lang.StringUtils;

public class RoomID extends EntityID {

    public static final String Sigill = "!";
    public static final String Delimiter = ":";

    public static RoomID from(String localpart, String domain) {
        return new RoomID(localpart + Delimiter + domain);
    }

    public static RoomID parse(String raw) {
        if (!StringUtils.startsWith(raw, Sigill)) {
            throw new IllegalArgumentException("Room ID must start with a " + Sigill);
        }

        String id = StringUtils.substringAfter(raw, Sigill);
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("Room ID is empty");
        }

        return new RoomID(id);
    }

    private RoomID(String id) {
        super(Sigill, id);
    }

}
