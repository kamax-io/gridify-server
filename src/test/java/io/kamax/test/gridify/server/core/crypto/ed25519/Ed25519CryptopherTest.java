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

package io.kamax.test.gridify.server.core.crypto.ed25519;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.codec.CanonicalJson;
import io.kamax.gridify.server.core.crypto.*;
import io.kamax.gridify.server.core.crypto.ed25519.Ed25519Cryptopher;
import io.kamax.gridify.server.core.crypto.ed25519.Ed25519Key;
import io.kamax.gridify.server.core.store.crypto.MemoryKeyStore;
import io.kamax.gridify.server.network.matrix.core.crypto.CryptoJson;
import io.kamax.gridify.server.network.matrix.core.crypto.MatrixDomainCryptopher;
import io.kamax.gridify.server.network.matrix.core.room.algo.RoomAlgo;
import io.kamax.gridify.server.network.matrix.core.room.algo.RoomAlgoV6;
import io.kamax.gridify.server.util.GsonUtil;
import io.kamax.gridify.server.util.KxLog;
import io.kamax.test.gridify.server.TestData;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;

import static junit.framework.TestCase.assertTrue;

// FIXME uses other classes like MatrixDomainCrypto, should be self-contained
public class Ed25519CryptopherTest {

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    private final static String prefix = "src/test/resources/events/";
    private static Key key;
    private static MatrixDomainCryptopher domainCrypto;
    private static Ed25519Cryptopher cryptopher;

    @BeforeClass
    public static void beforeClass() {
        KeyIdentifier keyId = new RegularKeyIdentifier(KeyAlgorithm.Ed25519, "1");
        key = new Ed25519Key(keyId, TestData.SIGN_KEY_SEED);
        MemoryKeyStore keyStore = new MemoryKeyStore();
        keyStore.add(key);
        cryptopher = new Ed25519Cryptopher(keyStore);
        domainCrypto = new MatrixDomainCryptopher() {
            @Override
            public String getDomain() {
                return "example.org";
            }

            @Override
            public Signature sign(JsonObject obj) {
                return cryptopher.sign(obj, key.getId());
            }

            @Override
            public Signature sign(byte[] data) {
                return cryptopher.sign(data, keyId);
            }
        };
    }

    private String loadFrom(String path) throws IOException {
        return IOUtils.toString(new FileInputStream(path), StandardCharsets.UTF_8);
    }

    public void signJson(String docFile) throws IOException {
        signJson(prefix + docFile + "-raw.txt", prefix + docFile + "-signed.txt");
    }

    public void signJson(String source, String control) throws IOException {
        String controlJson = CanonicalJson.encode(GsonUtil.parseObj(loadFrom(control)));

        JsonObject sourceData = GsonUtil.parseObj(loadFrom(source));
        sourceData = CryptoJson.signUnsafe(sourceData, domainCrypto);
        String sourceJson = CanonicalJson.encode(sourceData);

        log.info("Signed JSON: {}", sourceJson);
        log.info("Valid JSON:  {}", controlJson);
        assertTrue(StringUtils.equals(sourceJson, controlJson));
    }

    public void signEvent(String docPath) throws IOException {
        signEvent(prefix + docPath + "-raw.txt", prefix + docPath + "-signed.txt");
    }

    public void signEvent(String source, String control) throws IOException {
        String controlJson = CanonicalJson.encode(GsonUtil.parseObj(loadFrom(control)));
        RoomAlgo algo = new RoomAlgoV6();
        JsonObject sourceData = GsonUtil.parseObj(loadFrom(source));
        sourceData = algo.signEvent(sourceData, domainCrypto);
        String sourceJson = CanonicalJson.encode(sourceData);

        log.info("Signed JSON: {}", sourceJson);
        log.info("Valid JSON:  {}", controlJson);
        assertTrue(StringUtils.equals(sourceJson, controlJson));
    }

    @Test
    public void signEmpty() throws IOException {
        signJson("empty");
    }

    @Test
    public void signSimple() throws IOException {
        signJson("simple");
    }

    @Test
    public void signMinimal() throws IOException {
        signEvent("event-minimal");
    }

    @Test
    public void signRedactable() throws IOException {
        signEvent("event-redactable");
    }

    @Test
    public void signOffCreate() throws IOException {
        signEvent("m.room.create");
    }

    @Test
    public void signOffMessage() throws IOException {
        signEvent("m.room.message");
    }

}
