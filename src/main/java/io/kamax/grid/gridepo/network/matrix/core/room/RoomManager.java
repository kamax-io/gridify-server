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

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.core.channel.ChannelDao;
import io.kamax.grid.gridepo.core.channel.state.ChannelEventAuthorization;
import io.kamax.grid.gridepo.exception.EntityUnreachableException;
import io.kamax.grid.gridepo.exception.ForbiddenException;
import io.kamax.grid.gridepo.exception.ObjectNotFoundException;
import io.kamax.grid.gridepo.network.matrix.core.UserID;
import io.kamax.grid.gridepo.network.matrix.core.event.BareCreateEvent;
import io.kamax.grid.gridepo.network.matrix.core.event.BareEvent;
import io.kamax.grid.gridepo.network.matrix.core.event.BareMemberEvent;
import io.kamax.grid.gridepo.network.matrix.core.event.RoomEventType;
import io.kamax.grid.gridepo.network.matrix.core.federation.HomeServerLink;
import io.kamax.grid.gridepo.network.matrix.core.federation.RoomJoinTemplate;
import io.kamax.grid.gridepo.network.matrix.core.room.algo.RoomAlgo;
import io.kamax.grid.gridepo.network.matrix.core.room.algo.RoomAlgos;
import io.kamax.grid.gridepo.util.GsonUtil;
import io.kamax.grid.gridepo.util.KxLog;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RoomManager {

    private static final Logger log = KxLog.make(RoomManager.class);

    private final Gridepo g;
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public RoomManager(Gridepo g) {
        this.g = g;
    }

    private Room fromDao(ChannelDao dao) {
        return new Room(g, dao.getSid(), dao.getId(), RoomAlgos.get(dao.getVersion()));
    }

    public Set<String> getVersions() {
        return RoomAlgos.getVersions();
    }

    public Room createRoom(String domain, String creator, JsonObject options) {
        String algoVersion = GsonUtil.getStringOrNull(options, "room_version");
        if (StringUtils.isBlank(algoVersion)) {
            algoVersion = g.getConfig().getRoom().getCreation().getVersion();
        }
        RoomAlgo algo = RoomAlgos.get(algoVersion);

        ChannelDao dao = new ChannelDao("matrix", algo.generateRoomId(domain), algo.getVersion());
        dao = g.getStore().saveChannel(dao);

        Room r = new Room(g, dao.getSid(), dao.getId(), algo);
        rooms.put(r.getId(), r);

        List<BareEvent<?>> createEvents = algo.getCreationEvents(domain, creator, options);
        createEvents.stream()
                .map(ev -> r.offer(domain, ev))
                .filter(auth -> !auth.isAuthorized())
                .findAny().ifPresent(auth -> {
                    throw new RuntimeException("Room creation failed because of initial event " + auth.getEventId() + " being rejected: " + auth.getReason());
                });
        return r;
    }

    private Room create(JsonObject createDoc) {
        BareCreateEvent createEv = GsonUtil.fromJson(createDoc, BareCreateEvent.class);

        // We retrieve the room version and its corresponding algo
        String roomVersion = StringUtils.defaultIfBlank(createEv.getContent().getVersion(), "1");
        RoomAlgo algo = RoomAlgos.get(roomVersion);

        // We ensure the create event is valid for the given algo
        ChannelEventAuthorization createAuth = algo.authorizeCreate(createDoc);
        if (!createAuth.isAuthorized()) {
            throw new ForbiddenException("Room Creation denied with Event " + createAuth.getEventId() + ": " + createAuth.getReason());
        }

        // We create the room internally
        ChannelDao dao = new ChannelDao("matrix", createEv.getRoomId(), roomVersion);
        dao = g.getStore().saveChannel(dao);
        Room r = new Room(g, dao, algo);

        // We inject the auth chain
        ChannelEventAuthorization auth = r.addSeed(createDoc, Collections.emptyList());
        if (!auth.isAuthorized()) {
            throw new ForbiddenException("Room Creation denied with Event " + createAuth.getEventId() + ": " + createAuth.getReason());
        }

        rooms.put(r.getId(), r);
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
            g.getStore().findChannel("matrix", rId).ifPresent(dao -> rooms.put(rId, fromDao(dao)));
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
                RoomAlgo algo = RoomAlgos.get(joinTemplate.getRoomVersion());
                JsonObject joinEvent = algo.buildJoinEvent(joinTemplate);
                JsonObject joinEventSignedOff = algo.signEvent(joinEvent, g.overMatrix().crypto(), user.network());

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
                    log.info("{} did not sent valid state events to perform join: Event ID {} - {}", resident, auth.getEventId(), auth.getReason());
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

    public Room joinLocal(Room r, String userIdRaw) {
        UserID uId = UserID.parse(userIdRaw);
        BareMemberEvent bEv = new BareMemberEvent();
        bEv.setOrigin(uId.network());
        bEv.setRoomId(r.getId());
        bEv.setSender(userIdRaw);
        bEv.setStateKey(userIdRaw);
        bEv.getContent().setMembership(RoomMembership.Join);
        ChannelEventAuthorization auth = r.offer(uId.network(), bEv);
        if (!auth.isAuthorized()) {
            throw new ForbiddenException(auth.getReason());
        }

        return r;
    }

    public Room joinLocal(String userIdRaw, String roomId) {
        return joinLocal(get(roomId), userIdRaw);
    }

    public Room join(String userIdRaw, String roomIdOrAlias) {
        log.debug("Performing join of user {} in room address {}", userIdRaw, roomIdOrAlias);

        UserID uId = UserID.parse(userIdRaw);
        if (!RoomAlias.sigillMatch(roomIdOrAlias)) {
            log.debug("Room address is an ID, no resolution needed");
            return joinLocal(userIdRaw, roomIdOrAlias);
        }

        log.debug("Room address is an alias, resolving");
        RoomAlias rAlias = RoomAlias.parse(roomIdOrAlias);
        RoomLookup data = g.overMatrix().roomDir().lookup(uId.network(), rAlias, true)
                .orElseThrow(() -> new ObjectNotFoundException("Room alias", rAlias.full()));
        log.debug("Room alias {} resolved to {}", data.getAlias(), data.getId());


        Optional<Room> cOpt = find(data.getId());
        if (cOpt.isPresent()) {
            Room r = cOpt.get();
            if (r.getView().isServerJoined(uId.network())) {
                log.debug("Server is already in the room, performing self-join");
                return joinLocal(r, userIdRaw);
            } else {
                return joinRemote(uId, data);
            }
        } else {
            return joinRemote(uId, data);
        }
    }

    public void queueForDiscovery(List<JsonObject> events) {
        // TODO add room discovery
    }

}
