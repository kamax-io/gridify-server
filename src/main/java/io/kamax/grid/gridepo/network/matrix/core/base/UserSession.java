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
import io.kamax.grid.gridepo.core.channel.ChannelDao;
import io.kamax.grid.gridepo.core.channel.ChannelMembership;
import io.kamax.grid.gridepo.core.channel.TimelineChunk;
import io.kamax.grid.gridepo.core.channel.TimelineDirection;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelEventAuthorization;
import io.kamax.grid.gridepo.core.signal.AppStopping;
import io.kamax.grid.gridepo.core.signal.ChannelMessageProcessed;
import io.kamax.grid.gridepo.core.signal.SignalTopic;
import io.kamax.grid.gridepo.exception.ForbiddenException;
import io.kamax.grid.gridepo.exception.NotImplementedException;
import io.kamax.grid.gridepo.network.matrix.core.event.BareEvent;
import io.kamax.grid.gridepo.network.matrix.core.event.BareGenericEvent;
import io.kamax.grid.gridepo.network.matrix.core.event.BareMemberEvent;
import io.kamax.grid.gridepo.network.matrix.core.event.RoomEventType;
import io.kamax.grid.gridepo.network.matrix.core.room.*;
import io.kamax.grid.gridepo.network.matrix.http.json.RoomEvent;
import io.kamax.grid.gridepo.network.matrix.http.json.SyncResponse;
import io.kamax.grid.gridepo.util.GsonUtil;
import io.kamax.grid.gridepo.util.KxLog;
import net.engio.mbassy.listener.Handler;
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

    @Handler
    private void signal(AppStopping signal) {
        log.debug("Got {} signal, interrupting sync wait", signal.getClass().getSimpleName());
        synchronized (this) {
            notifyAll();
        }
    }

    @Handler
    private void signal(ChannelMessageProcessed signal) {
        log.debug("Got {} signal, interrupting sync wait", signal.getClass().getSimpleName());
        synchronized (this) {
            notifyAll();
        }
    }

    public String getUser() {
        return userId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getDomain() {
        return vHost;
    }

    public RoomEvent buildSyncEvent(ChannelEvent ev) {
        RoomEvent roomEvent = GsonUtil.map(ev.getData(), RoomEvent.class);
        if (StringUtils.isEmpty(roomEvent.getEventId())) {
            roomEvent.setEventId(ev.getId());
        }
        return roomEvent;
    }

    public SyncResponse buildSync(SyncData data) {
        Map<String, SyncResponse.Room> roomCache = new HashMap<>();
        SyncResponse r = new SyncResponse();
        r.nextBatch = data.getPosition();
        for (ChannelEvent ev : data.getEvents()) {
            try {
                RoomEvent rEv = buildSyncEvent(ev);
                SyncResponse.Room room = roomCache.computeIfAbsent(rEv.getRoomId(), id -> new SyncResponse.Room());
                room.getTimeline().getEvents().add(rEv);
                if (data.isInitial()) {
                    // This is the first event, so we set the previous batch info
                    room.getTimeline().setPrevBatch(rEv.getEventId());
                    RoomState state = getRoom(rEv.getRoomId()).getView().getState();
                    state.getEvents().stream()
                            // FIXME use Timeline ordering
                            .sorted(Comparator.comparingLong(o -> BareGenericEvent.extractDepth(o.getData())))
                            .forEach(stateEv -> room.getState().getEvents().add(buildSyncEvent(stateEv)));
                }
                r.rooms.join.put(rEv.getRoomId(), room);

                if (RoomEventType.Member.match(rEv.getType()) && userId.equals(rEv.getStateKey())) {
                    BareMemberEvent.findMembership(ev.getData()).ifPresent(m -> {
                        if (RoomMembership.Invite.match(m)) {
                            r.rooms.join.remove(rEv.getRoomId());
                            r.rooms.invite.put(rEv.getRoomId(), room);
                            r.rooms.leave.remove(rEv.getRoomId());

                            room.inviteState.events.addAll(room.timeline.events);
                            room.timeline.events.clear();
                            room.state.events.clear();
                        }

                        if (RoomMembership.Leave.match(m) || RoomMembership.Ban.match(m)) {
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
                            getRoom(rEv.getRoomId()).getState(ev).getEvents().forEach(stateEv -> {
                                if (stateEv.getLid() != ev.getLid()) {
                                    room.state.events.add(buildSyncEvent(stateEv));
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
        log.debug("Computing initial sync for {}", userId);
        SyncData data = new SyncData();
        data.setInitial(true);
        data.setPosition(Long.toString(g.getStreamer().getPosition()));
        List<ChannelDao> rooms = g.overMatrix().roomMgr().listInvolved(userId);
        log.debug("Found involvement with {} rooms", rooms.size());
        rooms.forEach(dao -> {
            Room r = getRoom(dao.getId());
            log.debug("Processing room {}", r.getId());
            RoomView roomView = r.getView();
            RoomState state = r.getView().getState();
            state.findMembership(userId).ifPresent(membership -> {
                log.debug("Membership is {}", membership);
                if (RoomMembership.Join.isAny(membership)) {
                    data.getEvents().add(r.getEvent(roomView.getEventId()));
                }
                if (RoomMembership.Invite.isAny(membership)) {
                    state.find(RoomEventType.Member.getId(), userId)
                            .ifPresent(ev -> data.getEvents().add(ev));
                }
            });
        });

        return buildSync(data);
    }

    public SyncResponse sync(SyncOptions options) {
        if (StringUtils.isEmpty(options.getToken())) {
            return syncInitial();
        }

        try {
            Instant end = Instant.now().plusMillis(options.getTimeout());

            g.getBus().getMain().subscribe(this);
            g.getBus().forTopic(SignalTopic.Room).subscribe(this);

            SyncData data = new SyncData();
            data.setPosition(options.getToken());

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
                                RoomState state = r.getState(ev);
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
            g.getBus().forTopic(SignalTopic.Room).unsubscribe(this);
        }
    }

    public Room createRoom(JsonObject options) {
        return g.overMatrix().roomMgr().createRoom(vHost, userId, options);
    }

    public Room joinRoom(String roomIdOrAlias) {
        return g.overMatrix().roomMgr().join(userId, roomIdOrAlias);
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

    public String send(String roomId, BareEvent<?> event) {
        event.setOrigin(vHost);
        event.setSender(userId);

        ChannelEventAuthorization auth = g.overMatrix().roomMgr().get(roomId).offer(vHost, event);
        if (!auth.isAuthorized()) {
            throw new ForbiddenException(auth.getReason());
        }
        return auth.getEventId();
    }

    public String send(String roomId, String type, String txnId, JsonObject content) {
        BareGenericEvent event = new BareGenericEvent();
        event.setType(type);
        event.getUnsigned().addProperty("transaction_id", txnId);
        event.setContent(content);
        return send(roomId, event);
    }

    public String sendState(String roomId, String type, String stateKey, JsonObject content) {
        BareGenericEvent event = new BareGenericEvent();
        event.setType(type);
        event.setStateKey(stateKey);
        event.setContent(content);
        return send(roomId, event);
    }

    public RoomTimelineChunck paginateTimeline(String roomId, String anchor, TimelineDirection direction, long maxEvents) {
        TimelineChunk chunck;
        if (TimelineDirection.Forward.equals(direction)) {
            chunck = getRoom(roomId).getTimeline().getNext(anchor, maxEvents);
        } else {
            chunck = getRoom(roomId).getTimeline().getPrevious(anchor, maxEvents);
        }

        List<RoomEvent> events = chunck.getEvents().stream().map(this::buildSyncEvent).collect(Collectors.toList());
        return new RoomTimelineChunck(chunck.getStart(), chunck.getEnd(), events);
    }

    public void addRoomAlias(String roomAlias, String roomId) {
        throw new NotImplementedException();
    }

    public void removeRoomAlias(String roomAlias) {
        throw new NotImplementedException();
    }

    public Optional<RoomLookup> lookupRoomAlias(String roomAlias) {
        return g.overMatrix().roomDir().lookup(vHost, RoomAlias.parse(roomAlias), true);
    }

}
