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

package io.kamax.gridify.server.core.auth.multi;

import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.config.IdentityConfig;
import io.kamax.gridify.server.config.UIAuthConfig;
import io.kamax.gridify.server.core.auth.AuthService;
import io.kamax.gridify.server.core.auth.UIAuthSession;
import io.kamax.gridify.server.core.identity.IdentityStore;
import io.kamax.gridify.server.core.identity.IdentityStoreSupplier;
import io.kamax.gridify.server.core.identity.IdentityStoreSuppliers;
import io.kamax.gridify.server.exception.ObjectNotFoundException;
import io.kamax.gridify.server.util.KxLog;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MultiStoreAuthService implements AuthService {

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    private final Map<String, IdentityStore> storeByLabel = new ConcurrentHashMap<>();
    private final Set<IdentityStore> stores = new HashSet<>();
    private final Map<String, UIAuthSession> sessions = new ConcurrentHashMap<>();

    private IdentityStore forLabel(String label, IdentityConfig.Store storeCfg) {
        return storeByLabel.computeIfAbsent(label, l -> {
            Optional<IdentityStoreSupplier> store = IdentityStoreSuppliers.get(storeCfg.getType());
            if (!store.isPresent()) {
                store = IdentityStoreSuppliers.get(storeCfg.getType() + ".internal");
                if (!store.isPresent()) {
                    throw new IllegalStateException("No provider found for identity store type '" + storeCfg.getType() + "'");
                }
            }

            return store.get().build(storeCfg);
        });
    }

    public MultiStoreAuthService(GridifyServer g) {
        // We configure some default identity stores if none was given in the config
        if (g.getConfig().getIdentity().getStores().isEmpty()) {
            if (g.getStore() instanceof IdentityStore) {
                stores.add((IdentityStore) g.getStore());
            }
        }

        g.getConfig().getIdentity().getStores().forEach((label, storeCfg) -> {
            IdentityStore store = forLabel(label, storeCfg);
            stores.add(store);
        });

        if (stores.isEmpty()) {
            log.warn("No auth type supported, users will not be able to login!");
        } else {
            log.debug("Supported auth types:");
            for (IdentityStore store : stores) {
                log.debug("  - " + store.getClass() + ": " + store.forAuth().getSupportedTypes());
            }
        }
    }

    @Override
    public UIAuthSession getSession(String network, UIAuthConfig cfg) {
        UIAuthSession session = new MultiStoreUIAuthSession(UUID.randomUUID().toString(), network, stores, cfg);
        if (sessions.containsKey(session.getId())) {
            throw new IllegalStateException("Session ID " + session.getId() + " cannot be used: already exists");
        }

        sessions.put(session.getId(), session);
        return session;
    }

    @Override
    public UIAuthSession getSession(String sessionId) throws ObjectNotFoundException {
        UIAuthSession session = sessions.get(sessionId);
        if (Objects.isNull(session)) {
            throw new ObjectNotFoundException("Session ID " + sessionId);
        }

        return session;
    }

}
