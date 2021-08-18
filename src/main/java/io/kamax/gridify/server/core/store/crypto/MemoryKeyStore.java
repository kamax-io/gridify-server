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

package io.kamax.gridify.server.core.store.crypto;

import io.kamax.gridify.server.core.crypto.GenericKey;
import io.kamax.gridify.server.core.crypto.GenericKeyIdentifier;
import io.kamax.gridify.server.core.crypto.Key;
import io.kamax.gridify.server.core.crypto.KeyIdentifier;
import io.kamax.gridify.server.exception.ObjectNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryKeyStore implements KeyStore {

    private final Map<String, Map<String, FileKeyJson>> keys = new ConcurrentHashMap<>();

    private Map<String, FileKeyJson> getMap(String algo) {
        return keys.computeIfAbsent(algo, k -> new ConcurrentHashMap<>());
    }

    @Override
    public boolean has(KeyIdentifier id) {
        return getMap(id.getAlgorithm()).containsKey(id.getSerial());
    }

    @Override
    public List<KeyIdentifier> list() {
        List<KeyIdentifier> keyIds = new ArrayList<>();
        keys.forEach((key, value) -> value.forEach((key1, value1) -> keyIds.add(new GenericKeyIdentifier(key, key1))));
        return keyIds;
    }

    @Override
    public Key get(KeyIdentifier id) throws ObjectNotFoundException {
        FileKeyJson data = getMap(id.getAlgorithm()).get(id.getSerial());
        if (Objects.isNull(data)) {
            throw new ObjectNotFoundException("Key", id.getId());
        }

        return new GenericKey(new GenericKeyIdentifier(id), data.isValid(), data.getPurpose(), data.getKey());
    }

    private void set(Key key) {
        FileKeyJson data = FileKeyJson.get(key);
        getMap(key.getId().getAlgorithm()).put(key.getId().getSerial(), data);
    }

    @Override
    public void add(Key key) throws IllegalStateException {
        if (has(key.getId())) {
            throw new IllegalStateException("Key " + key.getId().getId() + " already exists");
        }

        set(key);
    }

    @Override
    public void update(Key key) throws ObjectNotFoundException {
        if (!has(key.getId())) {
            throw new ObjectNotFoundException("Key", key.getId().getId());
        }

        set(key);
    }

    @Override
    public void delete(KeyIdentifier keyId) throws ObjectNotFoundException {
        if (!has(keyId)) {
            throw new ObjectNotFoundException("Key", keyId.getId());
        }

        keys.computeIfAbsent(keyId.getAlgorithm(), k -> new ConcurrentHashMap<>()).remove(keyId.getSerial());
    }

}
