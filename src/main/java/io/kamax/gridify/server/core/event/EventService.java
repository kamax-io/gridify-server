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

package io.kamax.gridify.server.core.event;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.codec.CanonicalJson;
import io.kamax.gridify.server.codec.GridHash;
import io.kamax.gridify.server.core.channel.event.*;
import io.kamax.gridify.server.core.crypto.*;
import io.kamax.gridify.server.network.grid.core.ServerID;
import io.kamax.gridify.server.util.GsonUtil;

import java.util.HashMap;
import java.util.Map;

public class EventService {

    private final ServerID origin;
    private final KeyIdentifier signingKey;
    private final Cryptopher crypto;

    private final Map<String, Class<? extends BareEvent<?>>> bares = new HashMap<>();

    public EventService(ServerID origin, PublicKey signingKey, Cryptopher crypto) {
        this(origin, RegularKeyIdentifier.parse(signingKey.getId()), crypto);
    }

    public EventService(ServerID origin, KeyIdentifier signingKey, Cryptopher crypto) {
        this.origin = origin;
        this.signingKey = signingKey;
        this.crypto = crypto;

        bares.put(ChannelEventType.Create.getId(), BareCreateEvent.class);
        bares.put(ChannelEventType.JoinRules.getId(), BareJoiningEvent.class);
        bares.put(ChannelEventType.Member.getId(), BareMemberEvent.class);
        bares.put(ChannelEventType.Power.getId(), BarePowerEvent.class);
    }

    public JsonObject hash(JsonObject ev) {
        String fullCanonical = CanonicalJson.encode(ev);
        String hash = GridHash.get().hashFromUtf8(fullCanonical);
        ev.add(EventKey.Hashes, GsonUtil.makeObj("sha256", hash));
        return ev;
    }

    public JsonObject sign(JsonObject ev) {
        JsonObject signatures = GsonUtil.findObj(ev, EventKey.Signatures).orElseGet(JsonObject::new);
        String type = GsonUtil.getStringOrThrow(ev, EventKey.Type);
        Class<? extends BareEvent<?>> evClass = bares.getOrDefault(type, BareGenericEvent.class);
        BareEvent<?> minEv = GsonUtil.get().fromJson(ev, evClass);
        JsonObject minEvJson = minEv.getJson();
        String minCanonical = CanonicalJson.encode(minEvJson);

        Signature sign = crypto.sign(minCanonical, signingKey);
        JsonObject signLocal = GsonUtil.makeObj(sign.getKey().getId(), sign.getSignature());
        signatures.add(origin.full(), signLocal);
        ev.add(EventKey.Signatures, signatures);

        return ev;
    }

    public JsonObject finalize(JsonObject ev) {
        return sign(hash(ev));
    }

}
