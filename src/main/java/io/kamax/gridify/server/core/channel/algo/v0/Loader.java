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

package io.kamax.gridify.server.core.channel.algo.v0;

import io.kamax.gridify.server.core.channel.algo.ChannelAlgo;
import io.kamax.gridify.server.core.channel.algo.ChannelAlgoLoader;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.Optional;

public class Loader implements ChannelAlgoLoader {

    private ChannelAlgoV0_0 obj;

    @Override
    public Optional<ChannelAlgo> apply(String version) {
        if (!StringUtils.equals(ChannelAlgoV0_0.Version, version)) {
            return Optional.empty();
        }

        synchronized (this) {
            if (Objects.isNull(obj)) {
                obj = new ChannelAlgoV0_0();
            }
        }

        return Optional.of(obj);
    }

}
