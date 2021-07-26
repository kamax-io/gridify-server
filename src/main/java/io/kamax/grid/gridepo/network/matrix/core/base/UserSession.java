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
import io.kamax.grid.gridepo.core.channel.Channel;
import io.kamax.grid.gridepo.core.channel.ChannelMembership;
import io.kamax.grid.gridepo.core.channel.TimelineChunk;
import io.kamax.grid.gridepo.core.channel.TimelineDirection;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelState;
import io.kamax.grid.gridepo.core.signal.SignalTopic;
import io.kamax.grid.gridepo.exception.NotImplementedException;
import io.kamax.grid.gridepo.network.grid.core.EventID;
import io.kamax.grid.gridepo.network.matrix.core.room.Room;
import io.kamax.grid.gridepo.network.matrix.core.room.RoomAliasLookup;
import io.kamax.grid.gridepo.util.KxLog;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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

    // FIXME evaluate if we should compute the exact state at the stream position in an atomic way
    public SyncData syncInitial() {
        SyncData data = new SyncData();
        data.setInitial(true);
        data.setPosition(Long.toString(g.getStreamer().getPosition()));

        // FIXME this doesn't scale - we only care about channels where the user has ever been into
        // so we shouldn't even deal with those. Need to make storage smarter in this case
        // or use a caching mechanism to know what's the membership status of a given user
        g.getChannelManager().list().forEach(cId -> {
            // FIXME we need to get the HEAD event of the timeline instead
            Channel c = g.getChannelManager().get(cId);
            EventID evID = c.getView().getHead();
            data.getEvents().add(g.getStore().getEvent(cId.full(), evID));
        });

        return data;
    }

    public SyncData sync(SyncOptions options) {
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
                                Channel c = g.getChannelManager().get(ev.getChannelId());
                                ChannelState state = c.getState(ev);
                                ChannelMembership m = state.getMembership(userId);
                                log.info("Membership for Event LID {}: {}", ev.getLid(), m);
                                return m.isAny(ChannelMembership.Join);
                            })
                            .collect(Collectors.toList());

                    data.getEvents().addAll(events);
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

            return data;
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
        throw new NotImplementedException();
    }

    public void inviteToRoom(String roomId, String userId) {
        throw new NotImplementedException();
    }

    public String send(String roomId, JsonObject doc) {
        throw new NotImplementedException();
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
