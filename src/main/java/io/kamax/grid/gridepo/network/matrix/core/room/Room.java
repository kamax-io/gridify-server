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
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelEventAuthorization;
import io.kamax.grid.gridepo.core.signal.ChannelMessageProcessed;
import io.kamax.grid.gridepo.core.signal.SignalTopic;
import io.kamax.grid.gridepo.core.store.ChannelStateDao;
import io.kamax.grid.gridepo.exception.ForbiddenException;
import io.kamax.grid.gridepo.exception.ObjectNotFoundException;
import io.kamax.grid.gridepo.network.matrix.core.event.BareEvent;
import io.kamax.grid.gridepo.network.matrix.core.event.BareGenericEvent;
import io.kamax.grid.gridepo.network.matrix.core.event.BareMemberEvent;
import io.kamax.grid.gridepo.network.matrix.core.event.EventKey;
import io.kamax.grid.gridepo.network.matrix.core.room.algo.RoomAlgo;
import io.kamax.grid.gridepo.util.GsonUtil;
import io.kamax.grid.gridepo.util.KxLog;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class Room {

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    private final Gridepo g;
    private final long sid; // Store ID
    private final String id;
    private final RoomAlgo algo;

    private RoomView view;

    public Room(Gridepo g, long sid, String id, RoomAlgo algo) {
        this.g = g;
        this.sid = sid;
        this.id = id;
        this.algo = algo;

        init();
    }

    public Room(Gridepo g, ChannelDao dao, RoomAlgo algo) {
        this(g, dao.getSid(), dao.getId(), algo);
    }

    private void init() {
        // FIXME we need to resolve the extremities as a timeline, get the HEAD and its state
        List<ChannelEvent> extremities = g.getStore().getForwardExtremities(sid).stream()
                .map(sid -> g.getStore().getEvent(sid))
                .collect(Collectors.toList());

        String head = extremities.stream()
                .max(Comparator.comparingLong(ChannelEvent::getSid))
                .map(ChannelEvent::getId)
                .orElse(null);

        RoomState state = extremities.stream()
                .max(Comparator.comparingLong(ev -> BareGenericEvent.extractDepth(ev.getData())))
                .map(ev -> g.getStore().getStateForEvent(ev.getLid()))
                .map(RoomState::new)
                .orElseGet(RoomState::empty);

        view = new RoomView(head, state);
        log.info("Channel {}: Loaded saved state SID {}", sid, view.getState().getSid());
    }

    public String getId() {
        return id;
    }

    public String getVersion() {
        return algo.getVersion();
    }

    public List<ChannelEvent> getExtremities() {
        //FIXME highly inefficient, should be done in DB
        return g.getStore().getForwardExtremities(sid).stream()
                .map(id -> g.getStore().getEvent(id))
                .collect(Collectors.toList());
    }

    public Optional<ChannelEvent> findEvent(String eventId) {
        return g.getStore().findEvent(id, eventId);
    }

    public ChannelEvent getEvent(String eventId) {
        return findEvent(eventId).orElseThrow(() -> new ObjectNotFoundException("Event ID", eventId));
    }

    public List<String> getExtremityIds(List<ChannelEvent> exts) {
        return exts.stream().map(ChannelEvent::getId).collect(Collectors.toList());
    }

    public JsonObject populate(JsonObject event) {
        Set<String> authEvents = algo.getAuthEvents(event, getView().getState());
        List<ChannelEvent> exts = getExtremities();
        List<String> extIds = getExtremityIds(exts);
        long depth = exts.stream()
                .map(ChannelEvent::getData)
                .mapToLong(data -> GsonUtil.getLong(data, EventKey.Depth))
                .max()
                .orElse(algo.getBaseDepth()) + 1;


        event.addProperty(EventKey.RoomId, id);
        event.add(EventKey.AuthEvents, GsonUtil.asArray(authEvents));
        event.add(EventKey.PrevEvents, GsonUtil.asArray(extIds));
        // SYNAPSE - seems mandatory?
        if (!event.has(EventKey.PrevState)) {
            event.add(EventKey.PrevState, new JsonObject());
        }
        event.addProperty(EventKey.Depth, depth);
        log.debug("Build event at depth {}", depth);
        return event;
    }

    public JsonObject finalize(String origin, JsonObject event) {
        JsonObject signedOff = algo.signEvent(event, g.getCrypto(), origin);
        String eventId = algo.getEventId(signedOff);
        log.debug("Signed off Event {}: {}", eventId, GsonUtil.getPrettyForLog(signedOff));
        return signedOff;
    }

    public RoomState getState(ChannelEvent event) {
        Optional<Long> evLid = g.getStore().findEventLid(id, event.getId());
        if (evLid.isPresent()) {
            // We already know this event, we fetch its local state
            try {
                ChannelStateDao stateDao = g.getStore().getStateForEvent(evLid.get());
                return new RoomState(stateDao);
            } catch (ObjectNotFoundException e) {
                // We don't have a local state for this
            }
        }

        // FIXME try to build from previous events

        BareGenericEvent bEv = BareGenericEvent.fromJson(event.getData());
        if (bEv.getAuthEvents().isEmpty()) {
            return RoomState.empty();
        }

        List<ChannelEvent> authEvents = bEv.getAuthEvents().stream()
                .map(evId -> g.getStore().findEvent(id, evId))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        if (authEvents.size() == bEv.getAuthEvents().size()) {
            return new RoomState(authEvents);
        }

        throw new IllegalStateException("Cannot build room event state");
    }

    public RoomState getFullState(String eventId) {
        // FIXME could optimise and directly get the data from the storage in a single call
        long eventLid = g.getStore().findEventLid(id, eventId)
                .orElseThrow(() -> new ObjectNotFoundException("Event state", eventId));
        ChannelStateDao dao = g.getStore().getStateForEvent(eventLid);
        return new RoomState(dao);
    }

    public List<JsonObject> getAuthChain(RoomState state) {
        // FIXME incomplete!
        List<JsonObject> authChain = state.getEvents().stream().map(ChannelEvent::getData).collect(Collectors.toList());
        return algo.orderTopologically(authChain);
    }

    // Add an event to the room without processing it or return the local copy
    public ChannelEvent inject(JsonObject eventDoc) {
        String eventId = algo.getEventId(eventDoc);
        Optional<ChannelEvent> eventOpt = findEvent(eventId);
        if (eventOpt.isPresent()) {
            ChannelEvent event = eventOpt.get();
            if (!event.getMeta().isPresent()) {
                event.setData(eventDoc);
                event = g.getStore().saveEvent(event);
            }
            return event;
        }

        ChannelEvent event = ChannelEvent.from(sid, eventId, eventDoc);
        return g.getStore().saveEvent(event);
    }

    public ChannelEventAuthorization process(ChannelEvent event) {
        log.debug("Processing Event {} || {}", event.getChannelId(), event.getId());
        RoomState state = getState(event);
        ChannelEventAuthorization auth = algo.authorize(state, event.getId(), event.getData());
        auth.setEvent(event);
        event.processed(auth);
        g.getStore().saveEvent(event);
        return auth;
    }

    public ChannelEventAuthorization add(JsonObject eventDoc) {
        return process(inject(eventDoc));
    }

    public ChannelEventAuthorization add(List<JsonObject> eventDocs) {
        ChannelEventAuthorization auth = null;
        for (JsonObject eventDoc : eventDocs) {
            auth = add(eventDoc);
        }
        return auth;
    }

    public ChannelEventAuthorization addSeed(JsonObject seedDoc, List<JsonObject> desiredState) {
        List<ChannelEventAuthorization> auths = desiredState.stream()
                .map(this::add)
                .collect(Collectors.toList());

        if (auths.stream().anyMatch(auth -> !auth.isAuthorized())) {
            throw new ForbiddenException("Seed add: unauthorized state");
        }

        RoomState state = new RoomState(auths.stream()
                .map(ChannelEventAuthorization::getEvent)
                .collect(Collectors.toList()));

        ChannelEventAuthorization auth = algo.authorize(state, seedDoc);
        if (!auth.isAuthorized()) {
            throw new ForbiddenException("Seed add: unauthorized event");
        }

        // Save the seed and its meta info
        ChannelEvent seed = inject(seedDoc);
        seed.getMeta().setValid(auth.isValid());
        seed.getMeta().setAllowed(auth.isAuthorized());
        seed.getMeta().setSeed(true);
        g.getStore().saveEvent(seed);

        // Save the state and map to seed
        long stateStoreId = g.getStore().insertIfNew(sid, state);
        state = new RoomState(stateStoreId, state);
        g.getStore().map(seed.getLid(), state.getSid());

        // Update forward extremities
        // Remove any possible old ones and replace with the seed event reference
        List<Long> toRemove = g.getStore().getForwardExtremities(sid);
        List<Long> toAdd = Collections.singletonList(seed.getLid());
        g.getStore().updateForwardExtremities(sid, toRemove, toAdd);

        // Update the cached view
        view = new RoomView(seed.getId(), state);

        // Insert the seed event into the stream
        g.getStore().addToStream(seed.getLid());
        ChannelMessageProcessed busEvent = new ChannelMessageProcessed(seed, auth);
        g.getBus().forTopic(SignalTopic.Room).publish(busEvent);

        return auth;
    }

    // Add an event to the room
    public synchronized ChannelEventAuthorization offer(ChannelEvent event) {
        RoomState eventState = getState(event);
        ChannelEventAuthorization eventStateAuth = algo.authorize(eventState, event.getId(), event.getData());
        if (!eventStateAuth.isAuthorized()) {
            throw new ForbiddenException("Event is not authorized: " + eventStateAuth.getReason());
        }

        RoomState state = getView().getState();
        ChannelEventAuthorization currentStateAuth = algo.authorize(state, event.getId(), event.getData());

        event.processed(currentStateAuth);
        g.getStore().saveEvent(event);
        if (!currentStateAuth.isAuthorized()) {
            throw new ForbiddenException("Event is not authorized");
        }

        List<Long> toRemove = event.getPreviousEvents().stream()
                .map(id -> g.getStore().findEventLid(getId(), id))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        List<Long> toAdd = Collections.singletonList(event.getLid());
        g.getStore().updateForwardExtremities(sid, toRemove, toAdd);

        state = state.apply(event);
        long stateSid = g.getStore().insertIfNew(sid, state);
        state = new RoomState(stateSid, state);
        g.getStore().map(event.getLid(), state.getSid());
        view = new RoomView(event.getId(), state);

        // Insert the seed event into the stream
        g.getStore().addToStream(event.getLid());
        ChannelMessageProcessed busEvent = new ChannelMessageProcessed(event, currentStateAuth);
        g.getBus().forTopic(SignalTopic.Room).publish(busEvent);

        return currentStateAuth;
    }

    public ChannelEventAuthorization offer(String origin, JsonObject eventDoc) {
        ChannelEvent event = ChannelEvent.from(sid, algo.getEventId(eventDoc), eventDoc);
        event.getMeta().setReceivedFrom(origin);
        event.getMeta().setReceivedAt(Instant.now());
        return offer(event);
    }

    public ChannelEventAuthorization offer(String origin, BareEvent<?> event) {
        event.setOrigin(origin);
        event.setTimestamp(Instant.now().toEpochMilli());
        return offer(origin, finalize(origin, populate(event.getJson())));
    }

    public RoomView getView() {
        return view;
    }

    public RoomTimeline getTimeline() {
        return new RoomTimeline(sid, g.getStore());
    }

    public JsonObject makeJoinTemplate(String userId) {
        return populate(BareMemberEvent.join(userId).getJson());
    }

}
