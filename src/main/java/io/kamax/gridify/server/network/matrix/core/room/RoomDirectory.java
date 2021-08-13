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

package io.kamax.gridify.server.network.matrix.core.room;

import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.core.signal.ChannelMessageProcessed;
import io.kamax.gridify.server.core.signal.SignalBus;
import io.kamax.gridify.server.core.signal.SignalTopic;
import io.kamax.gridify.server.core.store.DataStore;
import io.kamax.gridify.server.network.matrix.core.event.BareCanonicalAliasEvent;
import io.kamax.gridify.server.network.matrix.core.event.BareGenericEvent;
import io.kamax.gridify.server.network.matrix.core.event.RoomEventType;
import io.kamax.gridify.server.network.matrix.core.federation.HomeServerManager;
import io.kamax.gridify.server.util.GsonUtil;
import io.kamax.gridify.server.util.KxLog;
import net.engio.mbassy.listener.Handler;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.stream.Collectors;

public class RoomDirectory {

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    private final GridifyServer g;
    private final DataStore store;
    private final HomeServerManager srvMgr;

    public RoomDirectory(GridifyServer g, DataStore store, SignalBus bus, HomeServerManager srvMgr) {
        this.g = g;
        this.store = store;
        this.srvMgr = srvMgr;

        bus.forTopic(SignalTopic.Room).subscribe(this);
    }

    @Handler
    public void handler(ChannelMessageProcessed evP) {
        try {
            if (!evP.getAuth().isAuthorized()) {
                return;
            }

            BareGenericEvent bEv = GsonUtil.fromJson(evP.getEvent().getData(), BareGenericEvent.class);
            if (!RoomEventType.Address.match(bEv.getType())) {
                return;
            }

            if (!g.overMatrix().isLocal((bEv.getOrigin()))) {
                return;
            }

            Set<String> newAliases = new HashSet<>();
            BareCanonicalAliasEvent ev = GsonUtil.fromJson(evP.getEvent().getData(), BareCanonicalAliasEvent.class);
            Optional<RoomAlias> tryCanonical = RoomAlias.tryParse(ev.getContent().getAlias());
            if (!tryCanonical.isPresent()) {
                log.debug("Event {} contains an invalid canonical room alias, ignoring", ev.getId());
            } else {
                newAliases.add(tryCanonical.get().full());
            }

            List<String> altAliases = ev.getContent().getAltAliases();
            if (!Objects.isNull(altAliases)) {
                newAliases.addAll(altAliases.stream()
                        .map(RoomAlias::tryParse)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .map(RoomAlias::full)
                        .collect(Collectors.toSet())
                );
            }

            setAliases(ev.getOrigin(), bEv.getRoomId(), newAliases);
        } catch (RuntimeException | Error e) {
            log.error("Couldn't deal with message publication", e);
        }
    }

    public Optional<RoomLookup> lookup(String origin, RoomAlias alias, boolean recursive) {
        if (g.overMatrix().isLocal((alias.network()))) {
            log.info("Looking for our own alias {}", alias);
            return store.lookupChannelAlias("matrix", alias.full())
                    .map(id -> new RoomLookup(alias.full(), id, Collections.singleton(alias.network())));
        }

        if (!recursive) {
            log.info("Recursive lookup is not requested, returning empty lookup");
            return Optional.empty();
        }

        log.info("Looking recursively on {} for {}", alias.network(), alias);
        return srvMgr.getLink(origin, alias.network()).lookup(alias.full());
    }

    public Set<String> getAliases(String rId) {
        return store.findChannelAlias(null, rId);
    }

    public void setAliases(String origin, String roomid, Set<String> aliases) {
        store.setAliases("matrix", roomid, origin, aliases);
    }

}
