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
import io.kamax.grid.gridepo.exception.NotImplementedException;
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

    private final String blankRoomVersion = "1";

    private final Gridepo g;
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public RoomManager(Gridepo g) {
        this.g = g;
    }

    private Room fromDao(ChannelDao dao) {
        return new Room(g, dao.getSid(), dao.getId(), RoomAlgos.get(dao.getVersion()));
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
            throw new RuntimeException("Room creation failed because of initial event(s) being rejected: " + auth.getReason());
        });
        return r;
    }

    public Room create(List<JsonObject> stateJson) {
        throw new NotImplementedException();
    }

    public Room create(String from, JsonObject seedJson, List<JsonObject> stateJson) {
        // We expect the list of state event to be in the correct order to pass validation
        // This means the create event must be the first
        BareCreateEvent createEv = GsonUtil.fromJson(stateJson.get(0), BareCreateEvent.class);

        // SPEC-UNCLEAR If the version key is defined but is an empty string, assuming default spec version
        String roomVersion = StringUtils.defaultIfBlank(createEv.getContent().getVersion(), blankRoomVersion);

        ChannelDao dao = new ChannelDao("matrix", createEv.getRoomId(), roomVersion);
        dao = g.getStore().saveChannel(dao);
        Room r = new Room(g, dao.getSid(), dao.getId(), RoomAlgos.get(dao.getVersion()));

        ChannelEventAuthorization auth = r.inject(from, seedJson, stateJson);
        if (!auth.isAuthorized()) {
            throw new ForbiddenException("Seed is not allowed as per state: " + auth.getReason());
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

    public Room join(String userIdRaw, String roomIdOrAlias) {
        UserID uId = UserID.parse(userIdRaw);
        RoomLookup data;
        if (RoomAlias.sigillMatch(roomIdOrAlias)) {
            RoomAlias rAlias = RoomAlias.parse(roomIdOrAlias);
            data = g.overMatrix().roomDir().lookup(uId.network(), rAlias, true)
                    .orElseThrow(() -> new ObjectNotFoundException("Room alias", rAlias.full()));
        } else {
            data = new RoomLookup("", roomIdOrAlias, Collections.emptySet());
        }

        Optional<Room> cOpt = find(data.getId());
        if (cOpt.isPresent()) {
            Room r = cOpt.get();
            if (r.getView().getAllServers().stream().anyMatch(s -> StringUtils.equals(s, uId.network()))) {
                // We are joined, so we can make our own event
                BareMemberEvent bEv = new BareMemberEvent();
                bEv.setOrigin(uId.network());
                bEv.setRoomId(data.getId());
                bEv.setSender(userIdRaw);
                bEv.setStateKey(userIdRaw);
                bEv.getContent().setMembership(RoomMembership.Join);
                ChannelEventAuthorization auth = r.offer(uId.network(), bEv);
                if (!auth.isAuthorized()) {
                    throw new ForbiddenException(auth.getReason());
                }

                return r;
            }
        }

        // Couldn't join locally, let's try remotely
        if (data.getServers().isEmpty()) {
            // We have no peer we can use to join
            throw new EntityUnreachableException();
        }

        for (HomeServerLink srv : g.overMatrix().hsMgr().getLink(uId.network(), data.getServers(), true)) {
            String origin = srv.getDomain();
            try {
                RoomJoinTemplate joinTemplate = srv.getJoinTemplate(data.getId(), userIdRaw);
                RoomAlgo algo = RoomAlgos.get(joinTemplate.getRoomVersion());
                JsonObject joinEvent = algo.buildJoinEvent(uId.network(), joinTemplate.getEvent());
                JsonObject response = srv.sendJoin(joinEvent);
                Room r = cOpt.orElseGet(() -> create(new ArrayList<>()));
                r.offer(joinEvent);
                return r;
            } catch (ForbiddenException e) {
                log.warn("{} refused to sign our join request to {} because: {}", origin, data.getId(), e.getReason());
            }
        }

        throw new ForbiddenException("No resident server approved the join request");
    }

}
