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

package io.kamax.gridify.server.core.federation;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.core.channel.ChannelLookup;
import io.kamax.gridify.server.core.channel.event.BareMemberEvent;
import io.kamax.gridify.server.core.channel.event.ChannelEvent;
import io.kamax.gridify.server.core.channel.structure.ApprovalExchange;
import io.kamax.gridify.server.core.channel.structure.InviteApprovalRequest;
import io.kamax.gridify.server.core.identity.ThreePid;
import io.kamax.gridify.server.network.grid.core.*;
import io.kamax.gridify.server.util.GsonUtil;
import io.kamax.gridify.server.util.KxLog;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class DataServer {

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    private final ServerID id;
    private final String hostname;
    private DataServerHttpClient client;
    private volatile Instant lastOut;
    private volatile Instant lastIn;
    private AtomicLong waitTime;

    public DataServer(ServerID id) {
        this.id = id;
        this.hostname = id.tryDecodeDns().orElseThrow(() -> new IllegalArgumentException("Unable to resolve " + id.full() + " to a hostname"));
        this.client = new DataServerHttpClient();
        this.lastOut = Instant.EPOCH;
        this.lastIn = Instant.EPOCH;
        this.waitTime = new AtomicLong();

        setAvailable();
    }

    private <T> T withHealthCheck(Supplier<T> r) {
        return withHealthCheck(false, r);
    }

    private <T> T withHealthCheck(boolean force, Supplier<T> r) {
        if (!force && !isAvailable()) {
            throw new RuntimeException("Host is not available at this time");
        }

        try {
            T v = r.get();
            setAvailable();
            return v;
        } catch (FederationException e) {
            throw e;
        } catch (Throwable t) {
            if (waitTime.get() == 0) {
                waitTime.set(1000);
            } else {
                synchronized (this) {
                    waitTime.updateAndGet(l -> l * 2);
                }
            }
            throw t;
        } finally {
            lastOut = Instant.now();
        }
    }

    public ServerID getId() {
        return id;
    }

    public long getLastOut() {
        return lastOut.toEpochMilli();
    }

    public boolean isAvailable() {
        return !Instant.now().isBefore(lastOut.plusMillis(waitTime.get()));
    }

    public void setAvailable() {
        waitTime.set(0);
    }

    public void setActive() {
        lastIn = Instant.now();
        setAvailable();
    }

    public boolean ping(String as) {
        return withHealthCheck(true, () -> client.ping(as, hostname));
    }

    public Optional<JsonObject> getEvent(String as, ChannelID chId, String evId) {
        return getEvent(as, chId, EventID.parse(evId));
    }

    public Optional<JsonObject> getEvent(String as, ChannelID chId, EventID evId) {
        return withHealthCheck(() -> client.getEvent(as, hostname, chId, evId));
    }

    public JsonObject push(String as, ChannelEvent ev) {
        log.info("Pushing event {} to {} ({}) as {}", ev.getLid(), id, hostname, as);
        return withHealthCheck(() -> client.push(as, hostname, Collections.singletonList(ev)));
    }

    public JsonObject approveInvite(String as, InviteApprovalRequest data) {
        return withHealthCheck(true, () -> client.approveInvite(as, hostname, data));
    }

    public ApprovalExchange approveJoin(String as, BareMemberEvent ev) {
        return withHealthCheck(true, () -> {
            JsonObject json = client.approveJoin(as, hostname, ev);
            return GsonUtil.fromJson(json, ApprovalExchange.class);
        });
    }

    public Optional<ChannelLookup> lookup(String as, ChannelAlias alias) {
        log.info("Looking up {} on {}", alias, hostname);
        return withHealthCheck(true, () -> client.lookup(as, hostname, alias));
    }

    // FIXME this needs to go under Identity, Not data
    public Optional<UserID> lookup(String as, ThreePid tpid) {
        return withHealthCheck(true, () -> client.lookupUser(as, hostname, tpid));
    }

}
