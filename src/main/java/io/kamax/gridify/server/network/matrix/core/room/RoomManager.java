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

import com.google.gson.JsonObject;
import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.core.channel.ChannelDao;
import io.kamax.gridify.server.core.channel.state.ChannelEventAuthorization;
import io.kamax.gridify.server.exception.EntityUnreachableException;
import io.kamax.gridify.server.exception.ForbiddenException;
import io.kamax.gridify.server.exception.ObjectNotFoundException;
import io.kamax.gridify.server.network.matrix.core.UserID;
import io.kamax.gridify.server.network.matrix.core.crypto.MatrixDomainCryptopher;
import io.kamax.gridify.server.network.matrix.core.event.*;
import io.kamax.gridify.server.network.matrix.core.federation.HomeServerLink;
import io.kamax.gridify.server.network.matrix.core.federation.RoomJoinTemplate;
import io.kamax.gridify.server.network.matrix.core.room.algo.RoomAlgo;
import io.kamax.gridify.server.network.matrix.core.room.algo.RoomAlgos;
import io.kamax.gridify.server.util.GsonUtil;
import io.kamax.gridify.server.util.KxLog;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RoomManager {

    private static final Logger log = KxLog.make(RoomManager.class);

    private final GridifyServer g;
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public RoomManager(GridifyServer g) {
        this.g = g;
    }

    private Room fromDao(ChannelDao dao) {
        return new Room(g, dao);
    }

    public Set<String> getVersions() {
        return RoomAlgos.getVersions();
    }

    public Room register(String roomId, String roomVersion) {
        RoomAlgos.get(roomVersion); // We check if valid
        ChannelDao dao = new ChannelDao("matrix", roomId, roomVersion);
        dao = g.getStore().saveChannel(dao);
        Room r = new Room(g, dao);
        rooms.put(r.getId(), r);
        return r;
    }

    public String createRoom(MatrixDomainCryptopher crypto, String creator, JsonObject options) {
        String algoVersionDefault = RoomAlgos.defaultVersion();
        String algoVersionCfg = g.getConfig().getRoom().getCreation().getVersion();
        String algoVersionOption = GsonUtil.getStringOrNull(options, "room_version");
        String algoVersion = StringUtils.defaultIfBlank(algoVersionOption, StringUtils.defaultIfBlank(algoVersionCfg, algoVersionDefault));
        RoomAlgo algo = RoomAlgos.get(algoVersion);

        Room r = register(algo.generateRoomId(crypto.getDomain()), algoVersion);
        List<BareEvent<?>> createEvents = algo.getCreationEvents(crypto.getDomain(), creator, options);
        createEvents.stream()
                .map(ev -> r.offer(ev, crypto))
                .filter(auth -> !auth.isAuthorized())
                .findAny().ifPresent(auth -> {
                    throw new RuntimeException("Room creation failed because of initial event " + auth.getEventId() + " being rejected: " + auth.getReason());
                });
        return r.getId();
    }

    private Room create(JsonObject createDoc) {
        BareCreateEvent createEv = GsonUtil.fromJson(createDoc, BareCreateEvent.class);

        // We retrieve the room version and its corresponding algo
        String roomVersion = StringUtils.defaultIfBlank(createEv.getContent().getVersion(), RoomAlgos.blankVersion());
        RoomAlgo algo = RoomAlgos.get(roomVersion);

        // We ensure the create event is valid for the given algo
        ChannelEventAuthorization createAuth = algo.authorizeCreate(createDoc);
        if (!createAuth.isAuthorized()) {
            throw new ForbiddenException("Room Creation denied with Event " + createAuth.getEventId() + ": " + createAuth.getReason());
        }

        // We create the room internally
        Room r = register(createEv.getRoomId(), roomVersion);

        // We inject the auth chain
        ChannelEventAuthorization auth = r.addSeed(createDoc, Collections.emptyList());
        if (!auth.isAuthorized()) {
            throw new ForbiddenException("Room Creation denied with Event " + createAuth.getEventId() + ": " + createAuth.getReason());
        }

        return r;
    }

    public List<ChannelDao> list() {
        return g.getStore().listChannels("matrix");
    }

    public List<ChannelDao> listInvolved(String userId) {
        return g.getStore().searchForRoomsInUserEvents("matrix", RoomEventType.Member.getId(), userId);
    }

    public synchronized Optional<Room> find(String rId) {
        if (!rooms.containsKey(rId)) {
            g.getStore().findChannel("matrix", rId)
                    .ifPresent(dao -> rooms.put(rId, fromDao(dao)));
        }

        return Optional.ofNullable(rooms.get(rId));
    }

    public Room get(String rId) {
        return find(rId).orElseThrow(() -> new ObjectNotFoundException("Room", rId));
    }

    public Room joinRemote(UserID user, RoomLookup lookup) {
        // Couldn't join locally, let's try remotely
        if (lookup.getServers().isEmpty()) {
            log.debug("No resident server found, cannot perform join");
            // We have no peer we can use to join
            throw new EntityUnreachableException();
        }

        for (HomeServerLink srv : g.overMatrix().hsMgr().getLink(user.network(), lookup.getServers(), true)) {
            String resident = srv.getDomain();
            log.debug("Attempting remote join of user {} in room {} using resident server {}", user.full(), lookup.getId(), resident);
            try {
                // We fetch a join template from the resident server
                RoomJoinTemplate joinTemplate = srv.getJoinTemplate(lookup.getId(), user.full());
                joinTemplate.setOrigin(user.network());
                joinTemplate.setRoomId(lookup.getId());
                joinTemplate.setUserId(user.full());

                // We use the correct algo to build a complete join event
                String roomVersion = StringUtils.defaultIfBlank(joinTemplate.getRoomVersion(), RoomAlgos.blankVersion());
                RoomAlgo algo = RoomAlgos.get(roomVersion);
                JsonObject joinEvent = algo.buildJoinEvent(joinTemplate);
                JsonObject joinEventSignedOff = algo.signEvent(joinEvent, g.overMatrix().vHost(user.network()).asServer().getCrypto());

                // We offer the signed off event to the resident server
                RoomJoinSeed response = srv.sendJoin(lookup.getId(), user.full(), joinEventSignedOff);
                if (response.getAuthChain().isEmpty()) {
                    log.info("Remote server {} did not provide a valid auth chain, skipping", resident);
                    continue;
                }
                algo.orderTopologically(response.getAuthChain());

                // Get the room, create it if needed
                Room r = find(lookup.getId()).orElseGet(() -> {
                    // List was ordered topologically, so create event must be first
                    return create(response.getAuthChain().get(0));
                });

                // Add the auth chain
                ChannelEventAuthorization auth = r.add(response.getAuthChain());
                if (!auth.isAuthorized()) {
                    log.info("{} did not sent valid state events to perform join: Event ID {} - {}", resident, auth.getEventId(), auth.getReason());
                    continue;
                }

                // Add the join event as seed
                auth = r.addSeed(joinEventSignedOff, response.getState());
                if (!auth.isAuthorized()) {
                    log.info("{} did not sent valid seed event to perform join: Event ID {} - {}", resident, auth.getEventId(), auth.getReason());
                    continue;
                }

                // Join successful, return room object
                return r;
            } catch (ForbiddenException e) {
                log.warn("{} refused to sign our join request to {} because: {}", resident, lookup.getId(), e.getReason());
            }
        }

        throw new ForbiddenException("Could not find a resident server to perform join with");
    }

    public Room joinLocal(Room r, String userIdRaw, MatrixDomainCryptopher crypto) {
        UserID uId = UserID.parse(userIdRaw);
        BareMemberEvent bEv = new BareMemberEvent();
        bEv.setOrigin(uId.network());
        bEv.setRoomId(r.getId());
        bEv.setSender(userIdRaw);
        bEv.setStateKey(userIdRaw);
        bEv.getContent().setMembership(RoomMembership.Join);
        ChannelEventAuthorization auth = r.offer(bEv, crypto);
        if (!auth.isAuthorized()) {
            throw new ForbiddenException(auth.getReason());
        }

        return r;
    }

    public Room joinLocal(String userIdRaw, String roomId, MatrixDomainCryptopher crypto) {
        return joinLocal(get(roomId), userIdRaw, crypto);
    }

    public Room join(String userIdRaw, String roomIdOrAlias, MatrixDomainCryptopher crypto) {
        log.debug("Performing join of user {} in room address {}", userIdRaw, roomIdOrAlias);

        UserID uId = UserID.parse(userIdRaw);
        String roomId = roomIdOrAlias;
        Set<String> servers = new HashSet<>();
        if (RoomAlias.sigillMatch(roomIdOrAlias)) {
            log.debug("Room address is an alias, resolving");
            RoomAlias rAlias = RoomAlias.parse(roomIdOrAlias);
            RoomLookup data = g.overMatrix().roomDir().lookup(uId.network(), rAlias, true)
                    .orElseThrow(() -> new ObjectNotFoundException("Room alias", rAlias.full()));
            log.debug("Room alias {} resolved to {}", data.getAlias(), data.getId());
            roomId = data.getId();
            servers.addAll(data.getServers());
        } else {
            log.debug("Room address is an ID, using as is");
        }

        Optional<Room> cOpt = find(roomId);
        if (!cOpt.isPresent()) {
            log.debug("Room is not known locally, performing remote join");
            return joinRemote(uId, new RoomLookup(roomIdOrAlias, roomId, servers));
        }

        Room r = cOpt.get();
        if (r.getView().isServerJoined(uId.network())) {
            log.debug("Server is already in the room, performing self-join");
            return joinLocal(r, userIdRaw, crypto);
        } else {
            log.debug("Server is not in room");
            servers.removeIf(g.overMatrix().predicates().isLocalDomain());
            if (!servers.isEmpty()) {
                log.debug("Attempting join with lookup candidates");
                return joinRemote(uId, new RoomLookup(roomIdOrAlias, roomId, servers));
            } else {
                log.debug("No lookup candidates, attempting to find resident server candidates");

                // Try to find a membership event for the user
                r.getView().getState().find(RoomEventType.Member, userIdRaw)
                        .ifPresent(channelEvent -> {
                            String origin = channelEvent.asMatrix().getOrigin();
                            servers.add(origin);
                            log.debug("Added origin {} from found membership event", origin);
                        });

                // Use the room HEAD as a potential candidate
                r.findEvent(r.getView().getEventId()).ifPresent(ev -> {
                    String origin = ev.asMatrix().getOrigin();
                    servers.add(origin);
                    log.debug("Added origin {} from HEAD", origin);
                });

                servers.removeIf(g.overMatrix().predicates().isLocalDomain());
                log.debug("Found {} remote server candidates", servers.size());

                return joinRemote(uId, new RoomLookup(roomIdOrAlias, roomId, servers));
            }
        }
    }

    public void queueForDiscovery(List<JsonObject> events) {
        log.info("Rooms queued for discovery: {}", events.stream()
                .map(e -> GsonUtil.findString(e, EventKey.RoomId))
                .filter(Optional::isPresent)
                .map(Optional::get).collect(Collectors.toSet())
        );
        // TODO add room discovery
    }

}
