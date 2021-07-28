/*
 * Gridepo - Grid Data Server
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

package io.kamax.grid.gridepo.core.store;

import io.kamax.grid.gridepo.core.auth.Credentials;
import io.kamax.grid.gridepo.core.auth.SecureCredentials;
import io.kamax.grid.gridepo.core.channel.ChannelDao;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelState;
import io.kamax.grid.gridepo.core.identity.ThreePid;
import io.kamax.grid.gridepo.exception.ObjectNotFoundException;
import io.kamax.grid.gridepo.network.grid.core.ChannelID;
import io.kamax.grid.gridepo.network.grid.core.ServerID;
import io.kamax.grid.gridepo.network.matrix.core.room.RoomState;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface DataStore {

    List<ChannelDao> listChannels();

    List<ChannelDao> listChannels(String network);

    Optional<ChannelDao> findChannel(long cLid);

    Optional<ChannelDao> findChannel(String cId);

    default Optional<ChannelDao> findChannel(ChannelID cId) {
        return findChannel(cId.full());
    }

    default ChannelDao getChannel(long cLid) {
        return findChannel(cLid).orElseThrow(() -> new ObjectNotFoundException("Channel", Long.toString(cLid)));
    }

    long addToStream(long eLid);

    long getStreamPosition();

    ChannelDao saveChannel(ChannelDao ch);

    ChannelEvent saveEvent(ChannelEvent ev);

    ChannelEvent getEvent(String cId, String eId) throws ObjectNotFoundException;

    ChannelEvent getEvent(long eLid);

    String getEventId(long eLid);

    long getEventTid(long cLid, String eId);

    Optional<Long> findEventLid(String cId, String eId);

    // Get the N next events. next = Higher SID. last SID is not included.
    List<ChannelEvent> getNext(long lastSid, long amount);

    List<ChannelEvent> getTimelineNext(long cLid, long lastTid, long amount);

    List<ChannelEvent> getTimelinePrevious(long cLid, long lastTid, long amount);

    Optional<ChannelEvent> findEvent(String cId, String eId);

    Optional<ChannelEvent> findEvent(long eSid);

    void updateBackwardExtremities(long cLid, List<Long> toRemove, List<Long> toAdd);

    List<Long> getBackwardExtremities(long cLid);

    void updateForwardExtremities(long cLid, List<Long> toRemove, List<Long> toAdd);

    List<Long> getForwardExtremities(long cLid);

    long insertIfNew(long cLid, ChannelState state);

    long insertIfNew(long cLid, RoomState state);

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

    Optional<ChannelID> lookupChannelAlias(String chAlias);

    Set<String> findChannelAlias(ServerID origin, String cId);

    void setAliases(ServerID origin, ChannelID cId, Set<String> chAliases);

    void unmap(String cAlias);

    void linkUserToStore(long userLid, ThreePid storeId);

    Set<ThreePid> listThreePid(long userLid); // TODO consider making this a Set

    Set<ThreePid> listThreePid(long userLid, String medium); // TODO consider making this a Set

    void addThreePid(long userLid, ThreePid tpid);

    void removeThreePid(long userLid, ThreePid tpid);

}
