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

package io.kamax.gridify.server.core;

import java.util.Objects;

public class EntityAlias {

    private final String sigill;
    private final String local;
    private final String delimiter;
    private final String network;
    private final String full;

    public EntityAlias(String sigill, String delimiter, String local, String network) {
        this.sigill = Objects.requireNonNull(sigill);
        this.local = Objects.requireNonNull(local).toLowerCase();
        this.delimiter = Objects.requireNonNull(delimiter);
        this.network = Objects.requireNonNull(network).toLowerCase();
        full = sigill() + noSigill();
    }

    public String sigill() {
        return sigill;
    }

    public String local() {
        return local;
    }

    public String delimiter() {
        return delimiter;
    }

    public String network() {
        return network;
    }

    public String full() {
        return full;
    }

    public String noSigill() {
        return local() + delimiter() + network();
    }

}
