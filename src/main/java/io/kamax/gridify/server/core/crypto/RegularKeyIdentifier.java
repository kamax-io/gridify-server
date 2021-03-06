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

package io.kamax.gridify.server.core.crypto;

import org.apache.commons.lang3.StringUtils;

public class RegularKeyIdentifier extends GenericKeyIdentifier {

    public static RegularKeyIdentifier parse(String keyId) {
        String[] parts = StringUtils.split(keyId, ":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid key ID " + keyId);
        }

        return new RegularKeyIdentifier(parts[0], parts[1]);
    }

    public RegularKeyIdentifier(String algo, String serial) {
        super(algo, serial);
    }

}
