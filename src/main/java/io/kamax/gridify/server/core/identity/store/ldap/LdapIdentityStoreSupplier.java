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

package io.kamax.gridify.server.core.identity.store.ldap;

import io.kamax.gridify.server.config.Identity.store.GenericLdapConfig;
import io.kamax.gridify.server.config.IdentityConfig;
import io.kamax.gridify.server.core.identity.IdentityStore;
import io.kamax.gridify.server.core.identity.IdentityStoreSupplier;
import io.kamax.gridify.server.util.GsonUtil;

import java.util.Collections;
import java.util.Set;

public class LdapIdentityStoreSupplier implements IdentityStoreSupplier {

    @Override
    public Set<String> getSupportedTypes() {
        return Collections.singleton("ldap.internal");
    }

    @Override
    public IdentityStore build(IdentityConfig.Store cfg) {
        return new LdapIdentityStore(GsonUtil.map(GsonUtil.getConfig(), cfg.getConfig(), GenericLdapConfig.class));
    }

}
