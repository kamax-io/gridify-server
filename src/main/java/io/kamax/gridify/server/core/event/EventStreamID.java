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

package io.kamax.gridify.server.core.event;

import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

public class EventStreamID {

    private final String type;
    private final String scope;

    public EventStreamID(String type, String scope) {
        this.type = Objects.requireNonNull(type);
        this.scope = Objects.requireNonNull(scope);
    }

    public String getType() {
        return type;
    }

    public String getScope() {
        return scope;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventStreamID that = (EventStreamID) o;
        return type.equals(that.type) && scope.equals(that.scope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, scope);
    }

    @Override
    public String toString() {
        return type + (StringUtils.isBlank(scope) ? "" : (":" + scope));
    }

}
