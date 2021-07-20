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

package io.kamax.grid.gridepo.network.grid.core;

import io.kamax.grid.gridepo.core.EntityID;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

public class GridEntityID extends EntityID {

    public static final String Delimiter = "@";

    public GridEntityID(String sigill, String id) {
        super(sigill, id);
    }

    protected static String encode(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8.name()));
        } catch (UnsupportedEncodingException e) {
            // Nothing we can do about it
            throw new RuntimeException(e);
        }
    }

    protected Optional<String> tryDecode(String base) {
        try {
            return Optional.of(new String(Base64.getUrlDecoder().decode(base), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Optional<String> tryDecode() {
        return tryDecode(base());
    }

}
