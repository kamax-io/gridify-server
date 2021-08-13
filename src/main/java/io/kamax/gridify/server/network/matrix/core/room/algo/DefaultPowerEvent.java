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

import io.kamax.gridify.server.network.matrix.core.event.BarePowerEvent;

import java.util.Objects;

public class DefaultPowerEvent extends BarePowerEvent {

    public static Content applyDefaults(Content c) {
        if (Objects.isNull(c.getEventsDefault())) {
            c.setEventsDefault(0L);
        }

        if (Objects.isNull(c.getStateDefault())) {
            c.setStateDefault(50L);
        }

        if (Objects.isNull(c.getUsersDefault())) {
            c.setUsersDefault(0L);
        }

        if (Objects.isNull(c.getBan())) {
            c.setBan(50L);
        }

        if (Objects.isNull(c.getInvite())) {
            c.setInvite(c.getUsersDefault());
        }

        if (Objects.isNull(c.getKick())) {
            c.setKick(50L);
        }

        return c;
    }

    public DefaultPowerEvent() {
        applyDefaults(this.getContent());
    }

}
