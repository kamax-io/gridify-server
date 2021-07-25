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

package io.kamax.grid.gridepo.network.matrix.core;

import io.kamax.grid.gridepo.core.EntityAlias;
import org.apache.commons.lang.StringUtils;

public class UserID extends EntityAlias {

    public static final String Sigill = "@";
    public static final String Delimiter = ":";

    public static UserID parse(String raw) {
        // We could also do this in regex, but for the sake of readability and clarity, we keep it linear
        if (StringUtils.isBlank(raw)) {
            throw new IllegalArgumentException("User ID cannot be blank");
        }

        if (!StringUtils.startsWith(raw, Sigill)) {
            throw new IllegalArgumentException("User ID must start with the character " + Sigill);
        }

        String withoutSigill = StringUtils.substringAfter(raw, Sigill);
        // We split only at the first occurrence (array size of 2)
        String[] parts = StringUtils.split(withoutSigill, Delimiter, 2);
        String localpart = parts[0];
        String domain = parts[1];
        if (StringUtils.isBlank(domain)) {
            throw new IllegalArgumentException("Room alias contains domain which is blank");
        }

        return new UserID(localpart, domain);
    }

    public UserID(String local, String network) {
        super(Sigill, Delimiter, local, network);
    }

}
