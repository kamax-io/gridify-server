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
import io.kamax.gridify.server.util.GsonUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FileKeyStore implements KeyStore {

    private final String base;

    public FileKeyStore(String path) {
        if (StringUtils.isBlank(path)) {
            throw new IllegalArgumentException("File key store location cannot be blank");
        }

        base = new File(path).getAbsoluteFile().toString();
        File f = new File(base);

        if (!f.exists()) {
            try {
                FileUtils.forceMkdir(f);
            } catch (IOException e) {
                throw new RuntimeException("Unable to create key store: {}", e);
            }
        }

        if (!f.isDirectory()) {
            throw new RuntimeException("Key store path is not a directory: " + f);
        }
    }

    private Path ensureDirExists(KeyIdentifier id) {
        File b = Paths.get(base, id.getAlgorithm()).toFile();

        if (b.exists()) {
            if (!b.isDirectory()) {
                throw new RuntimeException("Key store path already exists but is not a directory: " + b);
            }
        } else {
            try {
                FileUtils.forceMkdir(b);
            } catch (IOException e) {
                throw new RuntimeException("Unable to create key store path at " + b, e);
            }
        }

        return b.toPath();
    }

    @Override
    public boolean has(KeyIdentifier id) {
        return Paths.get(base, id.getAlgorithm(), id.getSerial()).toFile().isFile();
    }

    @Override
    public List<KeyIdentifier> list() {
        List<KeyIdentifier> keyIds = new ArrayList<>();

        File algoDir = Paths.get(base).toFile();
        File[] algos = algoDir.listFiles();
        if (Objects.isNull(algos)) {
            return keyIds;
        }

        for (File algo : algos) {
            File[] serials = algo.listFiles();
            if (Objects.isNull(serials)) {
                throw new IllegalStateException("Cannot list stored key serials: was expecting " + algo + " to be a directory");
            }

            for (File serial : serials) {
                keyIds.add(new GenericKeyIdentifier(algo.getName(), serial.getName()));
            }
        }

        return keyIds;
    }

    @Override
    public Key get(KeyIdentifier id) throws ObjectNotFoundException {
        File keyFile = ensureDirExists(id).resolve(id.getSerial()).toFile();
        if (!keyFile.exists() || !keyFile.isFile()) {
            throw new ObjectNotFoundException("Key", id.getId());
        }

        try (FileInputStream keyIs = new FileInputStream(keyFile)) {
            FileKeyJson json = GsonUtil.get().fromJson(IOUtils.toString(keyIs, StandardCharsets.UTF_8), FileKeyJson.class);
            return new GenericKey(id, json.isValid(), json.getPurpose(), json.getKey());
        } catch (IOException e) {
            throw new RuntimeException("Unable to read key " + id.getId(), e);
        }
    }

    @Override
    public void add(Key key) throws IllegalStateException {
        File keyFile = ensureDirExists(key.getId()).resolve(key.getId().getSerial()).toFile();
        if (keyFile.exists()) {
            throw new IllegalStateException("Key " + key.getId().getId() + " already exists");
        }

        FileKeyJson json = FileKeyJson.get(key);
        try (FileOutputStream keyOs = new FileOutputStream(keyFile, false)) {
            IOUtils.write(GsonUtil.getPrettyForLog(json), keyOs, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Unable to create key " + key.getId().getId(), e);
        }
    }

    @Override
    public void update(Key key) throws ObjectNotFoundException {
        File keyFile = ensureDirExists(key.getId()).resolve(key.getId().getSerial()).toFile();
        if (!keyFile.exists() || !keyFile.isFile()) {
            throw new ObjectNotFoundException("Key", key.getId().getId());
        }

        FileKeyJson json = FileKeyJson.get(key);
        try (FileOutputStream keyOs = new FileOutputStream(keyFile, false)) {
            IOUtils.write(GsonUtil.get().toJson(json), keyOs, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Unable to create key " + key.getId().getId(), e);
        }
    }

    @Override
    public void delete(KeyIdentifier id) throws ObjectNotFoundException {
        File keyFile = ensureDirExists(id).resolve(id.getSerial()).toFile();
        if (!keyFile.exists() || !keyFile.isFile()) {
            throw new ObjectNotFoundException("Key", id.getId());
        }

        if (!keyFile.delete()) {
            throw new RuntimeException("Unable to delete key " + id.getId());
        }
    }

}
