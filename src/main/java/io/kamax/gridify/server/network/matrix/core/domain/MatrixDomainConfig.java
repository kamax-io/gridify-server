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

package io.kamax.gridify.server.network.matrix.core.domain;

import com.google.gson.JsonObject;

import java.util.Objects;

public class MatrixDomainConfig {

    public static class API {

        public static class Federation {

            public static class Version {

                JsonObject overwrite;

                public JsonObject getOverwrite() {
                    return overwrite;
                }

                public void setOverwrite(JsonObject overwrite) {
                    this.overwrite = overwrite;
                }

            }

            private Version version;

            public Version getVersion() {
                if (Objects.isNull(version)) {
                    version = new Version();
                }

                return version;
            }

            public void setVersion(Version version) {
                this.version = version;
            }

        }

        private Federation federation;

        public Federation getFederation() {
            if (Objects.isNull(federation)) {
                federation = new Federation();
            }

            return federation;
        }

        public void setFederation(Federation federation) {
            this.federation = federation;
        }

    }

    public static class Registration {

        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

    }

    private API api;
    private Registration registration;

    public API getApi() {
        if (Objects.isNull(api)) {
            api = new API();
        }

        return api;
    }

    public Registration getRegistration() {
        if (Objects.isNull(registration)) {
            registration = new Registration();
        }
        return registration;
    }

}
