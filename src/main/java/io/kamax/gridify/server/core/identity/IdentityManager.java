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

import io.kamax.gridify.server.config.IdentityConfig;
import io.kamax.gridify.server.core.crypto.Cryptopher;
import io.kamax.gridify.server.core.store.DataStore;
import io.kamax.gridify.server.core.store.UserDao;
import io.kamax.gridify.server.exception.ObjectNotFoundException;

import java.util.Optional;
import java.util.UUID;

public class IdentityManager {

    private IdentityConfig cfg;
    private DataStore store;
    private Cryptopher crypto;

    public IdentityManager(IdentityConfig cfg, DataStore store, Cryptopher crypto) {
        this.cfg = cfg;
        this.store = store;
        this.crypto = crypto;
    }

    public boolean canRegister() {
        return hasUsers() || cfg.getRegister().isEnabled();
    }

    public boolean isUsernameAvailable(String username) {
        return !store.hasUsername(username);
    }

    public User createUser() {
        String id = UUID.randomUUID().toString().replace("-", "");
        long lid = store.addUser(id);
        return new User(lid, id, store, crypto);
    }

    public User createUserWithKey() {
        User user = createUser();
        user.generateKey();
        return user;
    }

    private User getUser(UserDao dao) {
        return new User(dao.getLid(), dao.getId(), store, crypto);
    }

    public User getUser(String id) {
        UserDao dao = store.findUser(id).orElseThrow(() -> new ObjectNotFoundException("User with ID " + id));
        return getUser(dao);
    }

    public Optional<User> findUser(ThreePid tpid) {
        return store.findUserByTreePid(tpid).map(this::getUser);
    }

    public boolean hasUsers() {
        return store.getUserCount() > 0;
    }

}
