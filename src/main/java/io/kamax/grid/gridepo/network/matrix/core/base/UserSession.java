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

package io.kamax.grid.gridepo.network.matrix.core.base;

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.core.SyncData;
import io.kamax.grid.gridepo.core.SyncOptions;
import io.kamax.grid.gridepo.core.channel.ChannelMembership;
import io.kamax.grid.gridepo.core.channel.TimelineChunk;
import io.kamax.grid.gridepo.core.channel.TimelineDirection;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelEventAuthorization;
import io.kamax.grid.gridepo.core.channel.state.ChannelState;
import io.kamax.grid.gridepo.core.signal.SignalTopic;
import io.kamax.grid.gridepo.exception.ForbiddenException;
import io.kamax.grid.gridepo.exception.NotImplementedException;
import io.kamax.grid.gridepo.network.matrix.core.event.BareGenericEvent;
import io.kamax.grid.gridepo.network.matrix.core.event.BareMemberEvent;
import io.kamax.grid.gridepo.network.matrix.core.room.Room;
import io.kamax.grid.gridepo.network.matrix.core.room.RoomAliasLookup;
import io.kamax.grid.gridepo.network.matrix.core.room.RoomMembership;
import io.kamax.grid.gridepo.network.matrix.core.room.RoomState;
import io.kamax.grid.gridepo.network.matrix.http.json.RoomEvent;
import io.kamax.grid.gridepo.network.matrix.http.json.SyncResponse;
import io.kamax.grid.gridepo.util.GsonUtil;
import io.kamax.grid.gridepo.util.KxLog;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class UserSession {

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    private final Gridepo g;
    private final String vHost;
    private final String userId;
    private final String accessToken;

    public UserSession(Gridepo g, String vHost, String userId, String accessToken) {
        this.g = g;
        this.vHost = vHost;
        this.userId = userId;
        this.accessToken = accessToken;
    }

    public String getUser() {
        return userId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public RoomEvent buildSyncEvent(ChannelEvent ev) {
        return GsonUtil.map(ev.getData(), RoomEvent.class);
    }

    public SyncResponse buildSync(SyncData data) {
        Map<String, SyncResponse.Room> roomCache = new HashMap<>();
        SyncResponse r = new SyncResponse();
        r.nextBatch = data.getPosition();
        for (ChannelEvent ev : data.getEvents()) {
            try {
                RoomEvent rEv = buildSyncEvent(ev);
                if (StringUtils.isEmpty(rEv.getEventId())) {
                    rEv.setEventId(ev.getId());
                }
                SyncResponse.Room room = roomCache.computeIfAbsent(rEv.getRoomId(), id -> new SyncResponse.Room());
                room.getTimeline().getEvents().add(rEv);
                if (data.isInitial()) {
                    // This is the first event, so we set the previous batch info
                    room.getTimeline().setPrevBatch(ev.getId());
                    ChannelState state = g.getChannelManager().get(ev.getChannelId()).getState(ev);
                    state.getEvents().stream()
                            .sorted(Comparator.comparingLong(o -> o.getBare().getDepth())) // FIXME use Timeline ordering
                            .forEach(stateEv -> room.getState().getEvents().add(buildSyncEvent(stateEv)));
                }
                r.rooms.join.put(rEv.getRoomId(), room);

                if ("m.room.member".equals(rEv.getType()) && userId.equals(rEv.getStateKey())) {
                    JsonObject c = GsonUtil.parseObj(GsonUtil.toJson(rEv.getContent()));
                    GsonUtil.findString(c, "membership").ifPresent(m -> {
                        if ("invite".equals(m)) {
                            r.rooms.join.remove(rEv.getRoomId());
                            r.rooms.invite.put(rEv.getRoomId(), room);
                            r.rooms.leave.remove(rEv.getRoomId());

                            room.inviteState.events.addAll(room.timeline.events);
                            room.timeline.events.clear();
                            room.state.events.clear();
                        }

                        if ("leave".equals(m) || "ban".equals(m)) {
                            r.rooms.invite.remove(rEv.getRoomId());
                            r.rooms.join.remove(rEv.getRoomId());
                            r.rooms.leave.put(rEv.getRoomId(), room);
                        }

                        if (ChannelMembership.Join.match(m)) {
                            r.rooms.invite.remove(rEv.getRoomId());
                            r.rooms.join.put(rEv.getRoomId(), room);
                            r.rooms.leave.remove(rEv.getRoomId());

                            room.inviteState.events.clear();

                            room.state.events.clear();
                            // FIXME why?
                            g.getChannelManager().get(rEv.getChannelId()).getState(ev).getEvents().forEach(sEv -> {
                                if (sEv.getLid() != ev.getLid()) {
                                    room.state.events.add(buildSyncEvent(sEv));
                                }
                            });
                        }
                    });
                }
            } catch (RuntimeException e) {
                log.warn("Unable to process Matrix event {}, ignoring", ev.getId(), e);
            }
        }

        return r;
    }

    // FIXME evaluate if we should compute the exact state at the stream position in an atomic way
    public SyncResponse syncInitial() {
        SyncData data = new SyncData();
        data.setInitial(true);
        data.setPosition(Long.toString(g.getStreamer().getPosition()));

        // FIXME this doesn't scale - we only care about channels where the user has ever been into
        // so we shouldn't even deal with those. Need to make storage smarter in this case
        // or use a caching mechanism to know what's the membership status of a given user
        g.overMatrix().roomMgr().list().forEach(dao -> {
            // FIXME we need to get the HEAD event of the timeline instead
            Room r = g.overMatrix().roomMgr().get(dao.getId());
            String headEventId = r.getView().getEventId();
            data.getEvents().add(g.getStore().getEvent(r.getId(), headEventId));
        });

        return buildSync(data);
    }

    public SyncResponse sync(SyncOptions options) {
        try {
            g.getBus().getMain().subscribe(this);
            g.getBus().forTopic(SignalTopic.Channel).subscribe(this);

            Instant end = Instant.now().plusMillis(options.getTimeout());

            SyncData data = new SyncData();
            data.setPosition(options.getToken());

            if (StringUtils.isEmpty(options.getToken())) {
                return syncInitial();
            }

            long sid = Long.parseLong(options.getToken());
            do {
                if (g.isStopping()) {
                    break;
                }

                List<ChannelEvent> events = g.overMatrix().getStreamer().next(sid);
                if (!events.isEmpty()) {
                    long position = events.stream()
                            .filter(ev -> ev.getMeta().isProcessed())
                            .max(Comparator.comparingLong(ChannelEvent::getSid))
                            .map(ChannelEvent::getSid)
                            .orElse(0L);
                    log.debug("Position after sync loop: {}", position);
                    data.setPosition(Long.toString(position));

                    events = events.stream()
                            .filter(ev -> ev.getMeta().isValid() && ev.getMeta().isAllowed())
                            .filter(ev -> {
                                // FIXME move this into channel/state algo to check if a user can see an event in the stream

                                // If we are the author
                                if (StringUtils.equalsAny(userId, ev.getBare().getSender(), ev.getBare().getScope())) {
                                    return true;
                                }

                                // if we are subscribed to the channel at that point in time
                                Room r = g.overMatrix().roomMgr().get(ev.getChannelId());
                                RoomState state = r.getState(ev.getId());
                                RoomMembership m = state.getMembership(userId);
                                log.info("Membership for Event LID {}: {}", ev.getLid(), m);
                                return m.isAny(RoomMembership.Join);
                            })
                            .collect(Collectors.toList());

                    data.getEvents().addAll(events);
                    break;
                }

                long waitTime = Math.max(options.getTimeout(), 0L);
                log.debug("Timeout: " + waitTime);
                if (waitTime > 0) {
                    try {
                        synchronized (this) {
                            wait(waitTime);
                        }
                    } catch (InterruptedException e) {
                        // We don't care. We log it in case of, but we'll just loop again
                        log.debug("Got interrupted while waiting on sync");
                    }
                }
            } while (end.isAfter(Instant.now()));

            return buildSync(data);
        } finally {
            g.getBus().getMain().unsubscribe(this);
            g.getBus().forTopic(SignalTopic.Channel).unsubscribe(this);
        }
    }

    public Room createRoom(JsonObject options) {
        return g.overMatrix().roomMgr().createRoom(vHost, userId, options);
    }

    public Room joinRoom(String roomIdOrAlias) {
        throw new NotImplementedException();
    }

    public Room getRoom(String roomId) {
        return g.overMatrix().roomMgr().get(roomId);
    }

    public void leaveRoom(String roomId) {
        BareMemberEvent bareEvent = BareMemberEvent.leave(userId);
        g.overMatrix().roomMgr().get(roomId).offer(vHost, bareEvent);
    }

    public void inviteToRoom(String roomId, String userId) {
        throw new NotImplementedException();
    }

    public String send(String roomId, String type, String txnId, JsonObject content) {
        // TODO support txnId
        BareGenericEvent event = new BareGenericEvent();
        event.setOrigin(vHost);
        event.setSender(userId);
        event.setType(type);
        event.getUnsigned().addProperty("transaction_id", txnId);
        event.setContent(content);
        ChannelEventAuthorization auth = g.overMatrix().roomMgr().get(roomId).offer(vHost, event);
        if (!auth.isAuthorized()) {
            throw new ForbiddenException(auth.getReason());
        }
        return auth.getEventId();
    }

    public TimelineChunk paginateTimeline(String roomId, String anchor, TimelineDirection direction, long maxEvents) {
        throw new NotImplementedException();
    }

    public void addRoomAlias(String roomAlias, String roomId) {
        throw new NotImplementedException();
    }

    public void removeRoomAlias(String roomAlias) {
        throw new NotImplementedException();
    }

    public Optional<RoomAliasLookup> lookupRoomAlias(String roomAlias) {
        throw new NotImplementedException();
    }

}
