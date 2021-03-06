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

package io.kamax.gridify.server.core.identity.store.local;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.core.GridType;
import io.kamax.gridify.server.core.auth.AuthPasswordDocument;
import io.kamax.gridify.server.core.auth.AuthResult;
import io.kamax.gridify.server.core.auth.Credentials;
import io.kamax.gridify.server.core.auth.SecureCredentials;
import io.kamax.gridify.server.core.identity.AuthIdentityStore;
import io.kamax.gridify.server.core.identity.GenericThreePid;
import io.kamax.gridify.server.core.identity.ThreePid;
import io.kamax.gridify.server.core.store.DataStore;
import io.kamax.gridify.server.core.store.UserDao;
import io.kamax.gridify.server.util.KxLog;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class LocalAuthIdentityStore implements AuthIdentityStore {

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    private final DataStore store;

    public LocalAuthIdentityStore(DataStore store) {
        this.store = store;
    }

    @Override
    public Set<String> getSupportedTypes() {
        return new HashSet<>(Arrays.asList(GridType.of("auth.id.password"), "m.login.password"));
    }

    @Override
    public Optional<AuthResult> authenticate(String type, JsonObject docJson) {
        AuthPasswordDocument doc = AuthPasswordDocument.from(docJson);
        if ("m.login.password".equals(doc.getType())) {
            doc.setType(GridType.of("auth.id.password"));
        }
        Credentials creds = new Credentials(doc.getType(), doc.getPassword());

        ThreePid username = new GenericThreePid(doc.getIdentifier().getType(), doc.getIdentifier().getValue());
        log.debug("Authenticating {}", username);
        Optional<UserDao> daoOpt = store.findUserByTreePid(username);
        if (!daoOpt.isPresent()) {
            username = new GenericThreePid("g.id.local.username", doc.getIdentifier().getValue());
            daoOpt = store.findUserByTreePid(username);
            if (!daoOpt.isPresent()) {
                log.info("Authentication of {}: no user found", doc.getIdentifier().getValue());
                return Optional.empty();
            }
        }

        UserDao dao = daoOpt.get();
        SecureCredentials pass = store.getCredentials(dao.getLid(), creds.getType());
        if (pass.matches(creds)) {
            log.info("Authentication of {}: via password: success", dao.getId());
            return Optional.of(AuthResult.success(username));
        } else {
            log.info("Authentication of {}: via password: failure", dao.getId());
            return Optional.of(AuthResult.failed());
        }
    }

}
