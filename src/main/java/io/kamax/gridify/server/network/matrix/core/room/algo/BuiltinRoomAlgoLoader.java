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

package io.kamax.gridify.server.network.matrix.core.room.algo;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class BuiltinRoomAlgoLoader implements RoomAlgoLoader {

    @Override
    public Optional<RoomAlgo> apply(String s) {
        if (getVersions().contains(s)) {
            return Optional.of(new RoomAlgoV6());
        }

        return Optional.empty();
    }

    @Override
    public Set<String> getVersions() {
        Set<String> versions = new HashSet<>();
        versions.add("4");
        versions.add("5");
        versions.add("6");
        versions.add("7");
        return versions;
    }

}
