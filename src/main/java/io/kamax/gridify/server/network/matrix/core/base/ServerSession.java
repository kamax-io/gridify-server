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

package io.kamax.gridify.server.network.matrix.core.base;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.core.channel.event.ChannelEvent;
import io.kamax.gridify.server.core.channel.state.ChannelEventAuthorization;
import io.kamax.gridify.server.exception.ForbiddenException;
import io.kamax.gridify.server.exception.ObjectNotFoundException;
import io.kamax.gridify.server.network.matrix.core.IncompatibleRoomVersionException;
import io.kamax.gridify.server.network.matrix.core.MatrixCore;
import io.kamax.gridify.server.network.matrix.core.event.BareGenericEvent;
import io.kamax.gridify.server.network.matrix.core.federation.RoomJoinTemplate;
import io.kamax.gridify.server.network.matrix.core.room.*;
import io.kamax.gridify.server.network.matrix.http.json.ServerTransaction;
import io.kamax.gridify.server.util.GsonUtil;
import io.kamax.gridify.server.util.KxLog;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.*;

public class ServerSession {

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    private final MatrixCore core;

    private final String vHost;
    private final String remote;

    public ServerSession(MatrixCore core, String vHost, String remote) {
        this.core = core;
        this.vHost = vHost;
        this.remote = remote;
    }

    public String getVhost() {
        return vHost;
    }

    public String getDomain() {
        return remote;
    }

    public Optional<RoomLookup> lookupRoomAlias(String roomAlias) {
        RoomAlias alias = RoomAlias.parse(roomAlias);
        return core.roomDir().lookup(remote, alias, false);
    }

    public RoomJoinTemplate makeJoin(String roomId, String userId, Set<String> roomVersions) {
        Room r = core.roomMgr().get(roomId);
        String roomVersion = r.getVersion();
        if (!roomVersions.contains(roomVersion)) {
            throw new IncompatibleRoomVersionException(roomVersion);
        }
        JsonObject template = r.makeJoinTemplate(userId);
        log.debug("Built join template for User {} in Room {} : {}", userId, roomId, GsonUtil.getPrettyForLog(template));
        return new RoomJoinTemplate(roomVersion, template);
    }

    public RoomJoinSeed sendJoin(String roomId, String remoteUserId, JsonObject eventDoc) {
        Room r = core.roomMgr().get(roomId);
        ChannelEventAuthorization auth = r.offer(remote, vHost, eventDoc);
        if (!auth.isAuthorized()) {
            throw new ForbiddenException(auth.getReason());
        }

        RoomState state = r.getFullState(auth.getEventId());
        List<JsonObject> authChain = r.getAuthChainJson(state);

        RoomJoinSeed seed = new RoomJoinSeed();
        seed.setDomain(vHost);
        seed.setState(state);
        seed.setAuthChain(authChain);

        return seed;
    }

    public List<ChannelEventAuthorization> push(ServerTransaction txn) {
        log.info("Txn {}/{} - {} PDU(s) and {} EDU(s)", remote, txn.getId(), txn.getPdus().size(), txn.getEdus().size());
        List<ChannelEventAuthorization> auths = new ArrayList<>();
        Map<String, List<JsonObject>> pdusPerRoom = new HashMap<>();
        for (JsonObject pdu : txn.getPdus()) {
            String roomId = BareGenericEvent.extractRoomId(pdu);
            pdusPerRoom.computeIfAbsent(roomId, v -> new ArrayList<>()).add(pdu);
        }

        for (Map.Entry<String, List<JsonObject>> roomPdus : pdusPerRoom.entrySet()) {
            Optional<Room> roomOpt = core.roomMgr().find(roomPdus.getKey());
            if (!roomOpt.isPresent()) {
                core.roomMgr().queueForDiscovery(roomPdus.getValue());
                continue;
            }

            Room r = roomOpt.get();
            List<ChannelEventAuthorization> auth = r.offer(remote, vHost, roomPdus.getValue());
            auths.addAll(auth);
            log.debug("Txn {}/{} - Offered {} PDU(s) to {}", remote, txn.getTimestamp(), roomPdus.getValue().size(), r.getId());
        }

        return auths;
    }

    public List<ChannelEvent> getEventsTree(String roomId, List<String> latestEventIds, List<String> earliestEventIds, long limit, long minDepth) {
        Optional<Room> rOpt = core.roomMgr().find(roomId);
        if (!rOpt.isPresent()) {
            throw new ObjectNotFoundException("Room", roomId);
        }
        Room r = rOpt.get();

        List<String> toRetrieve = new ArrayList<>(latestEventIds);
        List<ChannelEvent> events = new ArrayList<>();
        while (events.size() < limit && !toRetrieve.isEmpty()) {
            String eventId = toRetrieve.remove(0);
            r.findEvent(eventId).ifPresent(ev -> {
                if (earliestEventIds.contains(ev.getId())) {
                    return;
                }

                events.add(ev);
                if (events.size() == limit) {
                    toRetrieve.clear();
                    return;
                }

                if (ev.asMatrix().getDepth() > minDepth) {
                    toRetrieve.addAll(ev.asMatrix().getPreviousEvents());
                }
            });
        }

        return events;
    }

    public List<ChannelEvent> backfill(String roomId, Collection<String> eventIds, long limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be greater than 0");
        }

        Optional<Room> rOpt = core.roomMgr().find(roomId);
        if (!rOpt.isPresent()) {
            throw new ObjectNotFoundException("Room", roomId);
        }
        Room r = rOpt.get();

        Queue<String> toRetrieve = new LinkedList<>(eventIds);
        Set<String> eventIDs = new HashSet<>();
        List<ChannelEvent> events = new ArrayList<>();
        while (!toRetrieve.isEmpty()) {
            Optional<ChannelEvent> evOpt = r.findEvent(toRetrieve.remove());
            if (!evOpt.isPresent()) {
                continue;
            }

            ChannelEvent ev = evOpt.get();
            // FIXME this should not be needed, find out why we get duplicate events
            if (!eventIDs.contains(ev.getId())) {
                events.add(ev);
                eventIDs.add(ev.getId());
            }

            if (events.size() >= limit) {
                break;
            }

            toRetrieve.addAll(ev.asMatrix().getPreviousEvents());
        }

        return events;
    }

    public EventState getState(String roomId, String eventId) {
        if (StringUtils.isBlank(roomId)) {
            throw new IllegalArgumentException("Room ID not provided");
        }

        if (StringUtils.isBlank(eventId)) {
            throw new IllegalArgumentException("Event ID not provided");
        }

        Room r = core.roomMgr().get(roomId);
        RoomState state = core.roomMgr().get(roomId).getFullState(eventId);
        List<ChannelEvent> authChain = r.getAuthChain(state);

        EventState evState = new EventState();
        evState.setAuthChain(authChain);
        evState.setState(state.getEvents());
        return evState;
    }

    public EventStateIds getStateIds(String roomId, String eventId) {
        return EventStateIds.getIds(getState(roomId, eventId));
    }

    public JsonObject getEvent(String eventId) {
        List<ChannelEvent> events = core.store().findEvents("matrix", eventId);

        for (ChannelEvent event : events) {
            if (event.getMeta().isPresent()) {
                return event.getData();
            }
        }

        throw new ObjectNotFoundException("Event", eventId);
    }

}
