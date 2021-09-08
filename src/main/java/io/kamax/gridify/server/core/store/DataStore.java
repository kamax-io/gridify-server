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

package io.kamax.gridify.server.core.store;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.kamax.gridify.server.core.auth.Credentials;
import io.kamax.gridify.server.core.auth.SecureCredentials;
import io.kamax.gridify.server.core.channel.ChannelDao;
import io.kamax.gridify.server.core.channel.event.ChannelEvent;
import io.kamax.gridify.server.core.channel.state.ChannelState;
import io.kamax.gridify.server.core.event.EventStreamID;
import io.kamax.gridify.server.core.identity.ThreePid;
import io.kamax.gridify.server.exception.ObjectNotFoundException;
import io.kamax.gridify.server.network.grid.core.ChannelID;
import io.kamax.gridify.server.network.grid.core.ServerID;
import io.kamax.gridify.server.network.matrix.core.room.RoomState;
import io.kamax.gridify.server.util.GsonUtil;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface DataStore {

    default boolean hasConfig(String id) {
        try {
            getConfig(id);
            return true;
        } catch (ObjectNotFoundException e) {
            return false;
        }
    }

    void setConfig(String id, JsonElement value);

    default void setConfig(String id, Object o) {
        setConfig(id, GsonUtil.get().toJsonTree(o));
    }

    JsonElement getConfig(String id);

    default String getConfigString(String id) {
        return getConfig(id).getAsString();
    }

    default JsonObject getConfigObj(String id) {
        return getConfig(id).getAsJsonObject();
    }

    default <T> T getConfig(String id, Class<T> type) {
        return GsonUtil.fromJson(getConfigObj(id), type);
    }

    List<DomainDao> listDomains(String network);

    DomainDao saveDomain(DomainDao dao);

    void deleteDomain(DomainDao dao);

    List<ChannelDao> listChannels();

    List<ChannelDao> listChannels(String network);

    Optional<ChannelDao> findChannel(long cLid);

    Optional<ChannelDao> findChannel(String network, String type, String cId);

    default Optional<ChannelDao> findChannel(ChannelID cId) {
        return findChannel("grid", "c", cId.full());
    }

    default ChannelDao getChannel(long cLid) {
        return findChannel(cLid).orElseThrow(() -> new ObjectNotFoundException("Channel", Long.toString(cLid)));
    }

    long addToStream(EventStreamID streamId, long eLid);

    long getStreamPosition(EventStreamID streamId);

    ChannelDao saveChannel(ChannelDao ch);

    ChannelEvent saveEvent(ChannelEvent ev);

    ChannelEvent getEvent(String cId, String eId) throws ObjectNotFoundException;

    ChannelEvent getEvent(long eLid);

    String getEventId(long eLid);

    long getEventTid(long cLid, String eId);

    Optional<Long> findEventLid(String cId, String eId);

    List<ChannelDao> searchForRoomsInUserEvents(String network, String type, String stateKey);

    // Get the N next events. next = Higher SID. last SID is not included.
    List<ChannelEvent> getNext(EventStreamID stream, long lastSid, long amount);

    List<ChannelEvent> getTimelineNext(long cLid, long lastTid, long amount);

    List<ChannelEvent> getTimelinePrevious(long cLid, long lastTid, long amount);

    Optional<ChannelEvent> findEvent(String cId, String eId);

    List<ChannelEvent> findEvents(String network, String eventId);

    Optional<ChannelEvent> findEvent(long eSid);

    void updateBackwardExtremities(long cLid, List<Long> toRemove, List<Long> toAdd);

    List<Long> getBackwardExtremities(long cLid);

    void updateForwardExtremities(long cLid, List<Long> toRemove, List<Long> toAdd);

    List<Long> getForwardExtremities(long cLid);

    long insertIfNew(long cLid, ChannelStateDao state);

    default long insertIfNew(long channelLocalId, ChannelState state) {
        return insertIfNew(channelLocalId, new ChannelStateDao(state.getSid(), state.getEvents()));
    }

    default long insertIfNew(long cLid, RoomState state) {
        return insertIfNew(cLid, new ChannelStateDao(state.getSid(), state.getEvents()));
    }

    ChannelStateDao getState(long sLid);

    void map(long evSid, long sLid);

    ChannelStateDao getStateForEvent(long evLid);

    boolean hasUsername(String username);

    long getUserCount();

    long addUser(String id);

    void addCredentials(long userLid, Credentials credentials);

    SecureCredentials getCredentials(long userLid, String type);

    Optional<UserDao> findUser(long lid);

    Optional<UserDao> findUser(String id);

    Optional<UserDao> findUserByStoreLink(ThreePid storeId);

    Optional<UserDao> findUserByTreePid(ThreePid tpid);

    boolean hasUserAccessToken(String token);

    void insertUserAccessToken(long uLid, String token);

    void deleteUserAccessToken(String token);

    Optional<String> lookupChannelAlias(String network, String alias);

    Set<String> findChannelAlias(String network, String networkId, String origin);

    default Set<String> findChannelAlias(ServerID origin, String cId) {
        return findChannelAlias("grid", cId, origin.full());
    }

    void setAliases(String network, String networkId, String origin, Set<String> aliases);

    @Deprecated
    default void setAliases(ServerID origin, ChannelID cId, Set<String> chAliases) {
        setAliases("grid", cId.full(), origin.full(), chAliases);
    }

    void unmap(String network, String cAlias);

    void linkUserToStore(long userLid, ThreePid storeId);

    Set<ThreePid> listThreePid(long userLid); // TODO consider making this a Set

    Set<ThreePid> listThreePid(long userLid, String medium); // TODO consider making this a Set

    void addThreePid(long userLid, ThreePid tpid);

    void removeThreePid(long userLid, ThreePid tpid);

    void setStreamIdForDestination(String destinationType, String destination, String scope, long streamId);

}
