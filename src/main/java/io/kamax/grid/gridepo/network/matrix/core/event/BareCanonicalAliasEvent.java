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

package io.kamax.grid.gridepo.network.matrix.core.event;

import java.util.List;

public class BareCanonicalAliasEvent extends BareEvent<BareCanonicalAliasEvent.Content> {

    public static class Content {

        private String alias;
        private List<String> altAliases;

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }

        public List<String> getAltAliases() {
            return altAliases;
        }

        public void setAltAliases(List<String> altAliases) {
            this.altAliases = altAliases;
        }

    }

    public BareCanonicalAliasEvent() {
        setType(RoomEventType.Address);
        setStateKey("");
        setContent(new Content());
    }

}
