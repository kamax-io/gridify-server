/*
 * Gridepo - Grid Data Server
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

package io.kamax.grid.gridepo.network.matrix.core.room;

import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.core.channel.event.BareAliasEvent;
import io.kamax.grid.gridepo.core.channel.event.BareGenericEvent;
import io.kamax.grid.gridepo.core.channel.event.ChannelEventType;
import io.kamax.grid.gridepo.core.signal.ChannelMessageProcessed;
import io.kamax.grid.gridepo.core.signal.SignalBus;
import io.kamax.grid.gridepo.core.signal.SignalTopic;
import io.kamax.grid.gridepo.core.store.DataStore;
import io.kamax.grid.gridepo.network.grid.core.ChannelID;
import io.kamax.grid.gridepo.network.matrix.core.federation.HomeServerManager;
import io.kamax.grid.gridepo.util.GsonUtil;
import io.kamax.grid.gridepo.util.KxLog;
import net.engio.mbassy.listener.Handler;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public class RoomDirectory {

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    private final Gridepo g;
    private final DataStore store;
    private final HomeServerManager srvMgr;

    public RoomDirectory(Gridepo g, DataStore store, SignalBus bus, HomeServerManager srvMgr) {
        this.g = g;
        this.store = store;
        this.srvMgr = srvMgr;

        bus.forTopic(SignalTopic.Channel).subscribe(this);
    }

    @Handler
    public void handler(ChannelMessageProcessed evP) {
        if (!evP.getAuth().isAuthorized()) {
            return;
        }

        BareGenericEvent bEv = evP.getEvent().getBare();
        String type = bEv.getType();
        if (!ChannelEventType.Alias.match(type)) {
            return;
        }

        if (!g.overMatrix().isLocal((bEv.getOrigin()))) {
            return;
        }

        BareAliasEvent ev = GsonUtil.fromJson(evP.getEvent().getData(), BareAliasEvent.class);
        setAliases(ChannelID.parse(ev.getChannelId()), ev.getContent().getAliases());
    }

    public Optional<RoomLookup> lookup(String origin, RoomAlias alias, boolean recursive) {
        if (g.overMatrix().isLocal((alias.network()))) {
            log.info("Looking for our own alias {}", alias);
            return store.lookupChannelAlias(alias.full()).map(id -> new RoomLookup(alias.full(), id.full(), Collections.singleton(alias.network())));
        }

        if (!recursive) {
            log.info("Recursive lookup is not requested, returning empty lookup");
            return Optional.empty();
        }

        log.info("Looking recursively on {} for {}", alias.network(), alias);
        return srvMgr.get(alias.network()).lookup(origin, alias.full());
    }

    public Set<String> getAliases(String rId) {
        return store.findChannelAlias(null, rId);
    }

    public void setAliases(ChannelID id, Set<String> aliases) {
        store.setAliases(null, id, aliases);
    }

}
