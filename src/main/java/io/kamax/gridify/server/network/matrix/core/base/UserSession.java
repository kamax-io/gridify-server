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
import com.google.gson.JsonSyntaxException;
import io.kamax.gridify.server.core.SyncData;
import io.kamax.gridify.server.core.SyncOptions;
import io.kamax.gridify.server.core.channel.ChannelDao;
import io.kamax.gridify.server.core.channel.ChannelMembership;
import io.kamax.gridify.server.core.channel.TimelineChunk;
import io.kamax.gridify.server.core.channel.TimelineDirection;
import io.kamax.gridify.server.core.channel.event.ChannelEvent;
import io.kamax.gridify.server.core.channel.state.ChannelEventAuthorization;
import io.kamax.gridify.server.core.identity.User;
import io.kamax.gridify.server.core.signal.AppStopping;
import io.kamax.gridify.server.core.signal.ChannelMessageProcessed;
import io.kamax.gridify.server.core.signal.SignalTopic;
import io.kamax.gridify.server.core.signal.SyncRefreshSignal;
import io.kamax.gridify.server.exception.ForbiddenException;
import io.kamax.gridify.server.network.matrix.core.MatrixServer;
import io.kamax.gridify.server.network.matrix.core.UserID;
import io.kamax.gridify.server.network.matrix.core.domain.MatrixDomain;
import io.kamax.gridify.server.network.matrix.core.domain.MatrixDomainConfig;
import io.kamax.gridify.server.network.matrix.core.event.*;
import io.kamax.gridify.server.network.matrix.core.federation.HomeServerLink;
import io.kamax.gridify.server.network.matrix.core.room.*;
import io.kamax.gridify.server.network.matrix.http.json.RoomEvent;
import io.kamax.gridify.server.network.matrix.http.json.SyncResponse;
import io.kamax.gridify.server.util.GsonUtil;
import io.kamax.gridify.server.util.KxLog;
import net.engio.mbassy.listener.Handler;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class UserSession {

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    private static final String commandInLinePrefix = "~g";

    private final MatrixServer g;
    private final String domain;
    private final User u;
    private final String userId;
    private final String accessToken;

    public UserSession(MatrixServer g, String domain, User u, String userId, String accessToken) {
        this.g = g;
        this.domain = domain;
        this.u = u;
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

    @Handler
    private void signal(SyncRefreshSignal signal) {
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
        return domain;
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
        List<ChannelDao> rooms = g.roomMgr().listInvolved(userId);
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
            g.getBus().forTopic(SignalTopic.SyncRefresh).subscribe(this);

            SyncData data = new SyncData();
            data.setPosition(options.getToken());

            long sid = Long.parseLong(options.getToken());
            do {
                if (!g.getCommandResponseQueue(userId).isEmpty()) {
                    log.info("Emptying command response buffer");
                    Map<String, SyncResponse.Room> roomCache = new HashMap<>();
                    SyncResponse syncResponse = new SyncResponse();
                    syncResponse.nextBatch = options.getToken();
                    for (JsonObject response : g.getCommandResponseQueue(userId)) {
                        String rId = GsonUtil.getStringOrThrow(response, EventKey.RoomId);
                        SyncResponse.Room room = roomCache.computeIfAbsent(rId, id -> new SyncResponse.Room());
                        RoomEvent rEv = GsonUtil.fromJson(response, RoomEvent.class);
                        room.timeline.events.add(rEv);
                    }
                    log.info("Command response buffer cleared");
                    g.getCommandResponseQueue(userId).clear();

                    syncResponse.rooms.join.putAll(roomCache);
                    return syncResponse;
                }

                if (g.isStopping()) {
                    break;
                }

                List<ChannelEvent> events = g.getStreamer().next(sid);
                for (ChannelEvent event : events) {
                    if (!event.getMeta().isProcessed()) {
                        break;
                    }
                    if (event.getSid() <= 0L) {
                        break;
                    }

                    if (((Predicate<ChannelEvent>) ev -> {
                        // FIXME move this into channel/state algo to check if a user can see an event in the stream

                        // If we are the author
                        if (StringUtils.equalsAny(userId, ev.asMatrix().getSender(), ev.asMatrix().getStateKey())) {
                            return true;
                        }

                        // if we are subscribed to the channel at that point in time
                        Room r = g.roomMgr().get(ev.asMatrix().getRoomId());
                        RoomState state = r.getState(ev);
                        RoomMembership m = state.getMembership(userId);
                        log.info("Membership for Event LID {}: {}", ev.getLid(), m);
                        return m.isAny(RoomMembership.Join);
                    }).test(event)) {
                        data.getEvents().add(event);
                    }

                    data.setPosition(Long.toString(event.getSid()));
                }

                if (!data.getEvents().isEmpty()) {
                    break;
                }

                long waitTime = Math.max(options.getTimeout(), 0L);
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
            g.getBus().forTopic(SignalTopic.SyncRefresh).unsubscribe(this);
        }
    }

    public Room createRoom(JsonObject options) {
        return g.roomMgr().get(g.roomMgr().createRoom(g.crypto(), userId, options));
    }

    public Room createRoom() {
        return createRoom(new JsonObject());
    }

    public Room joinRoom(String roomIdOrAlias) {
        return g.roomMgr().join(userId, roomIdOrAlias, g.crypto());
    }

    public Room getRoom(String roomId) {
        return g.roomMgr().get(roomId);
    }

    public void leaveRoom(String roomId) {
        g.roomMgr().leave(userId, roomId, g.crypto());
    }

    public void inviteToRoom(String roomId, String inviteeId) {
        UserID invitee = UserID.parse(inviteeId);

        BareMemberEvent bareInviteEvent = BareMemberEvent.makeFor(inviteeId, RoomMembership.Invite);
        bareInviteEvent.setOrigin(domain);
        bareInviteEvent.setSender(userId);

        Room r = getRoom(roomId);
        // FIXME populate() and finalize() should be on RoomView, as Room HEAD could have mutated between the next two calls
        RoomView view = r.getView();
        JsonObject inviteEvent = r.finalize(g.crypto(), r.populate(bareInviteEvent.getJson()));

        // Check if the user is authorized to perform the invite before anything
        ChannelEventAuthorization auth = r.getAlgo().authorize(view.getState(), inviteEvent);
        if (!auth.isAuthorized()) {
            throw new ForbiddenException(auth.getReason());
        }

        // If the invited user is not from the same domain as the user inviting, the invite handshake is required
        // Else skip and directly offer the event to the room
        if (!getDomain().contentEquals(invitee.network())) {
            RoomInviteRequest request = new RoomInviteRequest()
                    .setRoomId(roomId)
                    .setRoomVersion(r.getVersion())
                    .setEventId(auth.getEventId())
                    .setStrippedState(Collections.emptyList()) // FIXME provide a proper state
                    .setDoc(inviteEvent);

            if (g.core().isLocal(invitee.network())) {
                // The user is managed by one of the virtual domains, perform the handshake locally
                inviteEvent = g.core().forDomain(invitee.network()).asServer(getDomain()).inviteUser(request);
            } else {
                // The user is managed by a remote server, perform the handshake remotely
                HomeServerLink remoteHs = g.core().hsMgr().getLink(domain, invitee.network());
                inviteEvent = remoteHs.inviteUser(request);
            }
        }

        r.offer(domain, domain, inviteEvent);
    }

    public String send(String roomId, BareEvent<?> event) {
        event.setSender(userId);

        Room r = g.roomMgr().get(roomId);
        ChannelEventAuthorization auth = r.offer(event, g.crypto());
        if (!auth.isAuthorized()) {
            throw new ForbiddenException(auth.getReason());
        }

        return auth.getEventId();
    }

    private boolean isCommand(String type, JsonObject content) {
        // FIXME do better!
        if (!RoomEventType.Message.match(type)) {
            return false;
        }

        String msgType = GsonUtil.getStringOrNull(content, "msgtype");
        if (!StringUtils.equals("m.text", msgType)) {
            return false;
        }

        String body = GsonUtil.getStringOrNull(content, "body");
        return StringUtils.startsWithIgnoreCase(body, commandInLinePrefix);
    }

    private String processCommand(String roomId, String txnId, JsonObject cmdMessage) {
        // FIXME do better!
        String uuid = StringUtils.replace(UUID.randomUUID().toString(), "-", "");

        // We add the echo back
        String requestEventId = "!" + commandInLinePrefix + "~command~" + uuid + "~request";
        RoomEvent requestEvent = new RoomEvent();
        requestEvent.setEventId(requestEventId);
        requestEvent.setType(RoomEventType.Message.getId());
        requestEvent.setRoomId(roomId);
        requestEvent.setSender(userId);
        requestEvent.setOriginServerTs(Instant.now().toEpochMilli());
        requestEvent.setContent(cmdMessage);
        requestEvent.getUnsigned().addProperty("transaction_id", txnId);
        g.getCommandResponseQueue(userId).add(requestEvent.toJson());

        String cmd = GsonUtil.getStringOrNull(cmdMessage, "body");
        String body = "Processing command...";
        try {
            if (!g.isAdmin(u)) {
                body = "ERROR: You are not admin of this server";
            } else {
                // We process the command
                if (StringUtils.equals(commandInLinePrefix, cmd)) {
                    body = "Available commands:\n" +
                            "\tmatrix domain HOST|this registration {enable,disable}\n" +
                            "\tmatrix federation {enable,disable}\n";
                } else {
                    String subCmb = StringUtils.substringAfter(cmd, commandInLinePrefix + " ");
                    if (StringUtils.isBlank(subCmb)) {
                        throw new IllegalArgumentException("Invalid command: " + cmd);
                    }

                    if (StringUtils.startsWith(subCmb, "matrix add room-alias")) {
                        subCmb = StringUtils.replace(subCmb, "matrix add room-alias ", "", 1);
                        String[] subCmbArgs = StringUtils.split(subCmb, " ");
                        if (subCmbArgs.length < 3) {
                            body = "ERROR: Missing args";
                        } else {
                            String aliasRoomAddr = subCmbArgs[0];
                            String aliasRoomId = subCmbArgs[1];
                            String aliaSsrv = subCmbArgs[2];

                        }
                        // TODO finish
                    } else if (StringUtils.startsWith(subCmb, "matrix add domain ")) {
                        subCmb = StringUtils.replace(subCmb, "matrix add domain ", "", 1);
                        String[] subCmbArgs = StringUtils.split(subCmb, " ");
                        if (subCmbArgs.length < 2) {
                            body = "ERROR: Missing args";
                        } else {
                            MatrixDomain dom = g.core().addDomain(subCmbArgs[0], subCmbArgs[1]);
                            body = "OK - Added new Matrix domain " + dom.getDomain() + " served over " + dom.getHost();
                        }
                    } else if (StringUtils.startsWith(subCmb, "matrix domain ")) {
                        subCmb = StringUtils.replace(subCmb, "matrix domain ", "", 1);
                        String[] subCmbArgs = StringUtils.split(subCmb, " ");
                        if (StringUtils.equals(subCmbArgs[0], "this")) {
                            subCmbArgs[0] = domain;
                        }
                        if (StringUtils.equals(subCmbArgs[1], "registration")) {
                            boolean enable = StringUtils.equals(subCmbArgs[2], "enable");
                            g.core().forDomain(subCmbArgs[0]).updateConfig(cfg -> {
                                cfg.getRegistration().setEnabled(enable);
                            });
                            body = "OK - registration " + (enable ? "enabled" : "disabled") + " on domain " + subCmbArgs[0];
                        } else if (StringUtils.equals(subCmbArgs[1], "config")) {
                            if (StringUtils.equals(subCmbArgs[2], "get")) {
                                body = GsonUtil.toJson(g.core().forDomain(subCmbArgs[0]).getConfig());
                            } else if (StringUtils.equals(subCmbArgs[2], "set")) {
                                if (subCmbArgs.length < 4 || StringUtils.isBlank(subCmbArgs[3])) {
                                    body = "WARN - No config provided, no change";
                                } else {
                                    try {
                                        StringBuilder json = new StringBuilder();
                                        for (int i = 3; i < subCmbArgs.length; i++) {
                                            json.append(subCmbArgs[i]);
                                        }
                                        MatrixDomainConfig cfg = GsonUtil.parse(json.toString(), MatrixDomainConfig.class);
                                        g.core().forDomain(subCmbArgs[0]).setConfig(cfg);
                                        body = "OK - Configuration for domain " + subCmbArgs[0] + " has been updated";
                                    } catch (JsonSyntaxException e) {
                                        body = "ERROR - Invalid JSON: " + e.getMessage();
                                    }
                                }
                            } else {
                                body = "ERROR - unsupported config operation: " + subCmbArgs[2];
                            }
                        } else {
                            body = "ERROR: invalid argument: " + subCmbArgs[1];
                        }
                    } else if (StringUtils.equals("matrix federation enable", subCmb)) {
                        g.getFedPusher().setEnabled(true);
                        body = "OK";
                    } else if (StringUtils.equals("matrix federation disable", subCmb)) {
                        g.getFedPusher().setEnabled(false);
                        body = "OK";
                    } else if (StringUtils.equals("state", subCmb)) {
                        RoomState state = g.roomMgr().get(roomId).getView().getState();
                        body = "";
                        for (ChannelEvent ev : state.getEvents()) {
                            body += GsonUtil.getPretty().toJson(ev.getData()) + "\n";
                        }
                    } else {
                        body = "Unknown command";
                    }
                }
            }
        } catch (RuntimeException e) {
            log.error("Could not process command [{}]", cmd, e);
            body = "Unexpected error when processing command. See server log for more info";
        }

        String responseEventId = "!" + commandInLinePrefix + "~command~" + uuid + "~response";
        JsonObject content = new JsonObject();
        content.addProperty("msgtype", "m.text");
        content.addProperty("body", "```\n" + body + "\n```");
        content.addProperty("format", "org.matrix.custom.html");
        content.addProperty("formatted_body", "<pre><code>" + body + "</code></pre>");
        RoomEvent rEv = new RoomEvent();
        rEv.setEventId(responseEventId);
        rEv.setType(RoomEventType.Message.getId());
        rEv.setRoomId(roomId);
        rEv.setSender("@:" + domain);
        rEv.setOriginServerTs(Instant.now().toEpochMilli());
        rEv.setContent(content);
        g.getCommandResponseQueue(userId).add(GsonUtil.makeObj(rEv));
        g.getBus().forTopic(SignalTopic.SyncRefresh).publish(SyncRefreshSignal.get());
        log.info("Processed command {}", uuid);
        return requestEventId;
    }

    public String send(String roomId, String type, String txnId, JsonObject content) {
        if (isCommand(type, content)) {
            return processCommand(roomId, txnId, content);
        }

        BareGenericEvent event = new BareGenericEvent();
        event.setType(type);
        event.getUnsigned().addProperty(EventKey.UnsignedKeys.TransactionId, txnId);
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
        if (StringUtils.isBlank(roomAlias)) {
            throw new IllegalArgumentException("Room alias cannot be blank");
        }

        if (StringUtils.isBlank(roomId)) {
            throw new IllegalArgumentException("Room ID cannot be blank");
        }

        // TODO check need to handle this?
        // Everything is done via subscription to the channel message event in the room directory
    }

    public void removeRoomAlias(String roomAlias) {
        if (StringUtils.isBlank(roomAlias)) {
            throw new IllegalArgumentException("Room alias cannot be blank");
        }

        // TODO check need to handle this?
        // Everything is done via subscription to the channel message event in the room directory
    }

    public Optional<RoomLookup> lookupRoomAlias(String roomAlias) {
        return g.roomDir().lookup(domain, RoomAlias.parse(roomAlias), true);
    }

}
