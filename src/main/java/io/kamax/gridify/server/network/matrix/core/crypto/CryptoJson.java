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

package io.kamax.gridify.server.network.matrix.core.crypto;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.codec.CanonicalJson;
import io.kamax.gridify.server.core.crypto.Cryptopher;
import io.kamax.gridify.server.core.crypto.Signature;
import io.kamax.gridify.server.network.matrix.core.event.EventKey;
import io.kamax.gridify.server.util.GsonUtil;
import io.kamax.gridify.server.util.KxLog;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;

public class CryptoJson {

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    public static Signature computeSignature(JsonObject event, Cryptopher crypto) {
        // We get the canonical version
        String eventCanonical = CanonicalJson.encode(event);
        log.debug("Signing HS request: {}", eventCanonical);
        // We generate the signature for the event
        return crypto.sign(eventCanonical, crypto.getServerSigningKey().getId());
    }

    public static JsonObject signUnsafe(JsonObject doc, Cryptopher crypto, String domain) {
        JsonObject signaturesDoc = GsonUtil.popOrCreateObj(doc, EventKey.Signatures);

        // We generate the signature for the event
        Signature sign = computeSignature(doc, crypto);

        // We add the signature to the signatures dictionary
        JsonObject signLocalDoc = GsonUtil.makeObj(sign.getKey().getId(), sign.getSignature());
        signaturesDoc.add(domain, signLocalDoc);

        // We replace the event signatures original dictionary with the new one
        doc.add(EventKey.Signatures, signaturesDoc);

        return doc;
    }

    /**
     * Sign the doc, adding the relevant key(s)
     *
     * @param doc    The doc to sign
     * @param crypto The cryptopher to use
     * @param domain The domain under which the signature should be made
     * @return A copy of the doc with the added signature
     */
    public JsonObject signJson(JsonObject doc, Cryptopher crypto, String domain) {
        return CryptoJson.signUnsafe(doc.deepCopy(), crypto, domain);
    }

}
