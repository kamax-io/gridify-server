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

package io.kamax.gridify.server.network.matrix.core.federation;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class URIPath {

    public static String encode(String element) {
        try {
            return URLEncoder.encode(element, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            // This would be madness if it happened, but we need to handle it still
            throw new RuntimeException(e);
        }
    }

    private static URIPath with(String base) {
        return new URIPath().add(base);
    }

    public static URIPath root() {
        return with("_matrix");
    }

    public static URIPath federation() {
        return root().add("federation");
    }

    private final StringBuilder path = new StringBuilder();

    /**
     * Add the raw element to this path
     *
     * @param element The raw element to be added as is to the path, without encoding or path separator
     * @return The MatrixPath
     */
    public URIPath put(String element) {
        path.append(element);
        return this;
    }

    public URIPath put(String... elements) {
        for (String element : elements) {
            path.append(element);
        }

        return this;
    }

    /**
     * URL encode and add a new path element
     * <p>
     * This method handle path separators
     *
     * @param element The element to be encoded and added.
     * @return The MatrixPath
     */
    public URIPath add(String element) {
        // We add a path separator if this is the first character or if the last character is not a path separator
        // already
        if (path.length() == 0 || path.lastIndexOf("/", 0) < path.length() - 1) {
            put("/");
        }
        put(encode(element));

        return this;
    }

    public URIPath add(String... elements) {
        for (String element : elements) {
            add(element);
        }

        return this;
    }

    public String get() {
        return path.toString();
    }

    public String toString() {
        return get();
    }

    // Helper methods
    public URIPath v1() {
        return add("v1");
    }

    public URIPath v2() {
        return add("v2");
    }

}
