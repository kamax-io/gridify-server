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

package io.kamax.gridify.server.core.channel;

import io.kamax.gridify.server.core.channel.event.BareAliasEvent;
import io.kamax.gridify.server.core.channel.event.BareGenericEvent;
import io.kamax.gridify.server.core.channel.event.ChannelEventType;
import io.kamax.gridify.server.core.federation.DataServerManager;
import io.kamax.gridify.server.core.signal.ChannelMessageProcessed;
import io.kamax.gridify.server.core.signal.SignalBus;
import io.kamax.gridify.server.core.signal.SignalTopic;
import io.kamax.gridify.server.core.store.DataStore;
import io.kamax.gridify.server.network.grid.core.ChannelAlias;
import io.kamax.gridify.server.network.grid.core.ChannelID;
import io.kamax.gridify.server.network.grid.core.GridDataServer;
import io.kamax.gridify.server.network.grid.core.ServerID;
import io.kamax.gridify.server.util.GsonUtil;
import io.kamax.gridify.server.util.KxLog;
import net.engio.mbassy.listener.Handler;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public class ChannelDirectory {

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    private final ServerID origin;
    private final DataStore store;
    private final DataServerManager srvMgr;

    public ChannelDirectory(GridDataServer g) {
        this(g.server().getOrigin(), g.server().gridify().getStore(), g.server().gridify().getBus(), g.dataServerMgr());
    }

    public ChannelDirectory(ServerID origin, DataStore store, SignalBus bus, DataServerManager srvMgr) {
        this.origin = origin;
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

        if (!origin.equals(ServerID.parse(bEv.getOrigin()))) {
            return;
        }

        BareAliasEvent ev = GsonUtil.fromJson(evP.getEvent().getData(), BareAliasEvent.class);
        setAliases(ChannelID.parse(ev.getChannelId()), ev.getContent().getAliases());
    }

    public Optional<ChannelLookup> lookup(ChannelAlias alias, boolean recursive) {
        ServerID aSrvID = ServerID.fromDns(alias.network());
        if (origin.equals(aSrvID)) {
            log.info("Looking for our own alias {}", alias);
            return store.lookupChannelAlias("grid", alias.full())
                    .map(id -> new ChannelLookup(alias, ChannelID.parse(id), Collections.singleton(origin)));
        }

        if (!recursive) {
            log.info("Recursive lookup is not requested, returning empty lookup");
            return Optional.empty();
        }

        log.info("Looking recursively on {} for {}", aSrvID, alias);
        return srvMgr.get(aSrvID).lookup(origin.full(), alias);
    }

    public Set<String> getAliases(ChannelID id) {
        return store.findChannelAlias(origin, id.full());
    }

    public void setAliases(ChannelID id, Set<String> aliases) {
        store.setAliases(origin, id, aliases);
    }

}
