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

package io.kamax.grid.gridepo.network.matrix.core.room.algo;

import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.Optional;

public class Loader implements RoomAlgoLoader {

    private RoomAlgoV7 obj;

    @Override
    public Optional<RoomAlgo> apply(String version) {
        if (!StringUtils.equals(RoomAlgoV7.Version, version)) {
            return Optional.empty();
        }

        synchronized (this) {
            if (Objects.isNull(obj)) {
                obj = new RoomAlgoV7();
            }
        }

        return Optional.of(obj);
    }

}
