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

package io.kamax.gridify.server.core.identity;

import io.kamax.gridify.server.core.auth.Credentials;
import io.kamax.gridify.server.core.crypto.Cryptopher;
import io.kamax.gridify.server.core.crypto.KeyIdentifier;
import io.kamax.gridify.server.core.store.DataStore;
import io.kamax.gridify.server.network.grid.core.UserID;
import io.kamax.gridify.server.util.KxLog;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class User {

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    private long lid;
    private String id;
    private DataStore store;
    private Cryptopher keyring;

    public User(long lid, String id, DataStore store, Cryptopher keyring) {
        this.lid = lid;
        this.id = id;
        this.store = store;
        this.keyring = keyring;
    }

    public long getLid() {
        return lid;
    }

    public String getId() {
        return id;
    }

    public Set<KeyIdentifier> getKeys() {
        return store.listThreePid(lid, "g.id.key.ed25519").stream()
                .map(tpid -> keyring.getKeyWithPublic(tpid.getAddress()))
                .collect(Collectors.toSet());
    }

    public KeyIdentifier generateKey() {
        KeyIdentifier keyId = keyring.generateKey("Key of user [" + id + "]");
        String pubKeyId = keyring.getPublicKeyBase64(keyId);
        ThreePid tpid = new GenericThreePid("g.id.key.ed25519", pubKeyId);
        ThreePid tpidGrid = new GenericThreePid("g.id.net.grid", new UserID(pubKeyId).full());
        addThreePid(tpid);
        addThreePid(tpidGrid);
        return keyId;
    }

    public void linkToStoreId(ThreePid tpid) {
        if (!tpid.getMedium().startsWith("g.id.local.store.")) {
            throw new IllegalArgumentException("A store ID must use the namespace g.id.local.store");
        }

        store.linkUserToStore(lid, tpid);
    }

    public void addCredentials(Credentials creds) {
        store.addCredentials(lid, creds);
    }

    public void addThreePid(ThreePid tpid) {
        store.addThreePid(lid, tpid);
        log.info("LID {}: 3PID: add: {}", lid, tpid);
    }

    public void removeThreePid(ThreePid tpid) {
        store.removeThreePid(lid, tpid);
    }

    @Deprecated
    public UserID getGridId() {
        return UserID.parse(getNetworkId("grid"));
    }

    public String getUsername() {
        return store.listThreePid(lid, "g.id.local.username").stream().findFirst()
                .orElseThrow(IllegalStateException::new)
                .getAddress();
    }

    public Optional<String> findNetworkId(String network) {
        return store.listThreePid(lid, "g.id.net." + network).stream().findFirst().map(ThreePid::getAddress);
    }

    public String getNetworkId(String network) {
        return findNetworkId(network).orElseThrow(IllegalStateException::new);
    }

}
