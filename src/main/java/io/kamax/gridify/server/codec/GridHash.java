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

package io.kamax.gridify.server.codec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;

public class GridHash {

    private static GridHash instance;

    public static GridHash get() {
        synchronized (GridHash.class) {
            if (Objects.isNull(instance)) {
                instance = new GridHash();
            }
        }

        return instance;
    }

    private MessageDigest md;

    private GridHash(String digest) {
        try {
            md = MessageDigest.getInstance(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public GridHash() {
        this("SHA-256");
    }

    public String hash(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(md.digest(data));
    }

    public String hashRaw(byte[] data) {
        return Base64.getEncoder().withoutPadding().encodeToString(md.digest(data));
    }

    public String hashFromUtf8(String data) {
        return hash(data.getBytes(StandardCharsets.UTF_8));
    }

    public String hashRawFromUtf8(String data) {
        return hashRaw(data.getBytes(StandardCharsets.UTF_8));
    }

}
