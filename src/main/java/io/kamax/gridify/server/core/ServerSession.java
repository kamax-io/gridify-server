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

package io.kamax.gridify.server.core;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.core.channel.Channel;
import io.kamax.gridify.server.core.channel.ChannelLookup;
import io.kamax.gridify.server.core.channel.ChannelMembership;
import io.kamax.gridify.server.core.channel.event.BareGenericEvent;
import io.kamax.gridify.server.core.channel.event.BareMemberEvent;
import io.kamax.gridify.server.core.channel.event.ChannelEvent;
import io.kamax.gridify.server.core.channel.event.ChannelEventType;
import io.kamax.gridify.server.core.channel.state.ChannelEventAuthorization;
import io.kamax.gridify.server.core.channel.structure.ApprovalExchange;
import io.kamax.gridify.server.core.channel.structure.InviteApprovalRequest;
import io.kamax.gridify.server.core.event.EventKey;
import io.kamax.gridify.server.exception.EntityUnreachableException;
import io.kamax.gridify.server.exception.ForbiddenException;
import io.kamax.gridify.server.exception.ObjectNotFoundException;
import io.kamax.gridify.server.network.grid.core.*;
import io.kamax.gridify.server.util.GsonUtil;
import io.kamax.gridify.server.util.KxLog;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.stream.Collectors;

public class ServerSession {

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    private final GridServer gSrv;
    private final ServerID id;

    public ServerSession(GridServer gSrv) {
        this.gSrv = gSrv;
        this.id = gSrv.getOrigin();
    }

    private void markActive() {
        gSrv.forData().dataServerMgr().get(gSrv.getOrigin()).setActive();
    }

    public JsonObject approveInvite(InviteApprovalRequest request) {
        markActive();

        BareGenericEvent evGen = GsonUtil.fromJson(request.getObject(), BareGenericEvent.class);
        if (!ChannelEventType.Member.match(evGen.getType())) {
            throw new IllegalArgumentException("Illegal event type " + evGen.getType());
        }

        BareMemberEvent mEv = GsonUtil.fromJson(request.getObject(), BareMemberEvent.class);
        if (!ChannelMembership.Invite.match(mEv.getContent().getAction())) {
            throw new IllegalArgumentException("Illegal membership action " + mEv.getContent().getAction());
        }

        if (!gSrv.isLocal(UserID.parse(mEv.getScope()))) {
            throw new IllegalArgumentException("Not authoritative for user " + mEv.getScope());
        }

        String chId = mEv.getChannelId();
        log.info("Approving invite from {} for {} in {}", mEv.getSender(), mEv.getScope(), chId);

        JsonObject invEv = gSrv.evSvc().sign(request.getObject());
        Optional<Channel> chOpt = gSrv.forData().getChannelManager().find(ChannelID.parse(chId));

        if (chOpt.isPresent()) {
            ChannelEventAuthorization auth = chOpt.get().offer(id.full(), invEv);
            if (!auth.isAuthorized()) {
                throw new ForbiddenException("Invite is not allowed given state");
            }
        } else {
            gSrv.forData().getChannelManager().create(id.full(), invEv, request.getContext().getState());
        }

        log.info("Invite is approved");

        return invEv;
    }

    // FIXME we are not atomic when it comes to state - we auth on an unknown state, and then fetch state again to send back
    public ApprovalExchange approveJoin(BareMemberEvent ev) {
        markActive();

        // We make sure we are given a valid Channel ID
        ChannelID cId = ChannelID.parse(ev.getChannelId());

        // We make sure we know the channel itself
        Channel c = gSrv.forData().getChannelManager().find(cId).orElseThrow(() -> new ObjectNotFoundException("Channel", cId));

        // We make sure we are in the channel in the first place, else it's not possible for us to approve the join
        if (!c.getView().isJoined(gSrv.getOrigin())) {
            throw new EntityUnreachableException();
        }

        ev.setOrigin(gSrv.getOrigin().full());
        JsonObject evBuilt = c.makeEvent(ev);
        JsonObject evFinal = gSrv.evSvc().finalize(evBuilt);
        ChannelEventAuthorization auth = c.authorize(evFinal);
        if (!auth.isAuthorized()) {
            throw new ForbiddenException("Approval to join channel " + cId + ": " + auth.getReason());
        }

        // We actively check that the server can be reached before approving a join
        if (!gSrv.forData().dataServerMgr().get(id).ping(gSrv.getOrigin().full())) {
            throw new ForbiddenException("Your origin " + id + " is not reachable");
        }

        List<JsonObject> state = c.getView().getState().getEvents().stream()
                .sorted(Comparator.comparingLong(v -> v.getBare().getDepth()))
                .map(ChannelEvent::getData)
                .collect(Collectors.toList());

        ApprovalExchange reply = new ApprovalExchange();
        reply.setObject(evFinal);
        reply.getContext().setState(state);

        return reply;
    }

    public List<ChannelEventAuthorization> push(List<JsonObject> events) {
        markActive();

        log.info("Got pushed {} event(s) from {}", events.size(), id);

        List<ChannelEventAuthorization> results = new ArrayList<>();
        Map<String, List<JsonObject>> evChanMap = new HashMap<>();
        for (JsonObject event : events) {
            String cId = GsonUtil.findString(event, EventKey.ChannelId).orElse("");
            if (StringUtils.isEmpty(cId)) {
                continue;
            }

            evChanMap.computeIfAbsent(cId, i -> new ArrayList<>()).add(event);
        }

        evChanMap.forEach((cId, objs) -> {
            log.info("Injecting {} event(s) in room {}", objs.size(), cId);
            results.addAll(gSrv.forData().getChannelManager().get(cId).offer(id.full(), objs));
        });

        return results;
    }

    public Optional<ChannelLookup> lookup(ChannelAlias chAlias) {
        markActive();

        if (!StringUtils.equalsIgnoreCase(gSrv.getDomain(), chAlias.network())) {
            return Optional.empty();
        }

        Optional<ChannelLookup> lookup = gSrv.forData().getChannelDirectory().lookup(chAlias, false);
        if (!lookup.isPresent()) {
            return Optional.empty();
        }

        ChannelID cId = lookup.get().getId();
        Optional<Channel> ch = gSrv.forData().getChannelManager().find(cId);
        if (!ch.isPresent()) {
            return Optional.empty();
        }

        Set<ServerID> servers = ch.get().getView().getState().getEvents().stream()
                .map(ev -> ServerID.parse(ev.getOrigin()))
                .collect(Collectors.toSet());

        return Optional.of(new ChannelLookup(chAlias, cId, servers));
    }

    public Optional<ChannelEvent> getEvent(ChannelID cId, EventID eId) {
        markActive();

        return gSrv.store().findEvent(cId.full(), eId.full());
    }

}
