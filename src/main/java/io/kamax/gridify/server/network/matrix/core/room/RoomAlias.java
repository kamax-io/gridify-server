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

package io.kamax.gridify.server.network.matrix.core.room;

import io.kamax.gridify.server.core.EntityAlias;
import org.apache.commons.lang.StringUtils;

import java.util.Optional;

public class RoomAlias extends EntityAlias {

    public static final String Sigill = "#";
    public static final String Delimiter = ":";

    public static boolean sigillMatch(String raw) {
        return StringUtils.startsWith(raw, Sigill);
    }

    public static Optional<RoomAlias> tryParse(String raw) {
        try {
            return Optional.of(parse(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static RoomAlias parse(String raw) {
        // We could also do this in regex, but for the sake of readability and clarity, we keep it linear
        if (StringUtils.isBlank(raw)) {
            throw new IllegalArgumentException("Room alias cannot be blank");
        }

        if (!StringUtils.startsWith(raw, Sigill)) {
            throw new IllegalArgumentException("Room alias must start with the character " + Sigill);
        }

        String withoutSigill = StringUtils.substringAfter(raw, Sigill);
        // We split only at the first occurrence (array size of 2)
        String[] parts = StringUtils.split(withoutSigill, Delimiter, 2);
        String localpart = parts[0];
        String domain = parts[1];
        if (StringUtils.isBlank(domain)) {
            throw new IllegalArgumentException("Room alias contains domain which is blank");
        }

        return new RoomAlias(localpart, domain);
    }

    public static RoomAlias from(String local, String domain) {
        return new RoomAlias(local, domain);
    }

    RoomAlias(String local, String network) {
        super(Sigill, Delimiter, local, network);
    }

    @Override
    public String toString() {
        return full();
    }

}
