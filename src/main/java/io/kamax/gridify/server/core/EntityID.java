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

public class EntityID {

    private final String sigill;
    private final String base;

    public EntityID(String sigill, String id) {
        this.sigill = Objects.requireNonNull(sigill);
        this.base = Objects.requireNonNull(id);
    }

    public String sigill() {
        return sigill;
    }

    public String base() {
        return base;
    }

    public String full() {
        return sigill + base;
    }

    @Override
    public boolean equals(Object o) {
        if (Objects.isNull(o)) return false;
        if (this == o) return true;
        if (!(o instanceof EntityID)) return false;

        EntityID entityID = (EntityID) o;

        if (!sigill.equals(entityID.sigill)) return false;
        return base.equals(entityID.base);
    }

    @Override
    public int hashCode() {
        int result = sigill.hashCode();
        result = 31 * result + base.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return full();
    }

}
