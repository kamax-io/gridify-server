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
import io.kamax.gridify.server.core.channel.event.ChannelEvent;
import io.kamax.gridify.server.core.channel.state.ChannelEventAuthorization;
import io.kamax.gridify.server.core.signal.ChannelMessageProcessed;
import io.kamax.gridify.server.core.signal.SignalTopic;
import io.kamax.gridify.server.core.store.ChannelStateDao;
import io.kamax.gridify.server.exception.ForbiddenException;
import io.kamax.gridify.server.exception.ObjectNotFoundException;
import io.kamax.gridify.server.network.matrix.core.event.BareEvent;
import io.kamax.gridify.server.network.matrix.core.event.BareGenericEvent;
import io.kamax.gridify.server.network.matrix.core.event.BareMemberEvent;
import io.kamax.gridify.server.network.matrix.core.event.EventKey;
import io.kamax.gridify.server.network.matrix.core.federation.HomeServerLink;
import io.kamax.gridify.server.network.matrix.core.federation.RemoteForbiddenException;
import io.kamax.gridify.server.network.matrix.core.room.algo.RoomAlgo;
import io.kamax.gridify.server.util.GsonUtil;
import io.kamax.gridify.server.util.KxLog;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class Room {

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    private final GridifyServer g;
    private final long sid; // Store ID
    private final String id;
    private final RoomAlgo algo;

    private RoomView view;

    public Room(GridifyServer g, long sid, String id, RoomAlgo algo) {
        this.g = g;
        this.sid = sid;
        this.id = id;
        this.algo = algo;

        init();
    }

    public Room(GridifyServer g, ChannelDao dao, RoomAlgo algo) {
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
        log.info("Room {}: Loaded saved state SID {}", getId(), view.getState().getSid());
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
        if (event.hasLid()) {
            try {
                ChannelStateDao stateDao = g.getStore().getStateForEvent(event.getLid());
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
                .filter(ev -> ev.getMeta().isAllowed())
                .collect(Collectors.toList());
        if (authEvents.isEmpty()) {
            throw new IllegalStateException("Cannot build room event state");
        }

        return new RoomState(authEvents);
    }

    public RoomState getFullState(String eventId) {
        // FIXME could optimise and directly get the data from the storage in a single call
        long eventLid = g.getStore().findEventLid(id, eventId)
                .orElseThrow(() -> new ObjectNotFoundException("Event state", eventId));
        ChannelStateDao dao = g.getStore().getStateForEvent(eventLid);
        return new RoomState(dao);
    }

    public List<ChannelEvent> getAuthChain(RoomState state) {
        // FIXME incomplete!
        return state.getEvents();
    }

    public List<JsonObject> getAuthChainJson(RoomState state) {
        // FIXME incomplete!
        List<JsonObject> authChain = getAuthChain(state).stream().map(ChannelEvent::getData).collect(Collectors.toList());
        return algo.orderTopologically(authChain);
    }

    public List<String> findMissingEvents(List<String> eventIds) {
        List<String> missingEvents = new ArrayList<>();
        for (String eventId : new HashSet<>(eventIds)) {
            if (!g.getStore().findEventLid(id, eventId).isPresent()) {
                missingEvents.add(eventId);
            }
        }
        return missingEvents;
    }

    public boolean hasAll(List<String> eventIds) {
        return findMissingEvents(eventIds).isEmpty();
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
        log.debug("Processing Event {} || {} || {}", event.asMatrix().getRoomId(), event.getId(), event.asMatrix().getType());
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
            if (!auth.isAuthorized()) {
                return auth;
            }
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
        seed.getMeta().setProcessed(true);
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
        long streamId = g.getStore().addToStream(seed.getLid());
        seed.setSid(streamId);

        // Publish onto the signal bus
        ChannelMessageProcessed busEvent = new ChannelMessageProcessed(seed, auth);
        g.getBus().forTopic(SignalTopic.Room).publish(busEvent);

        return auth;
    }

    // Add an event to the room
    public ChannelEventAuthorization offer(String remoteDomain, String localDomain, ChannelEvent event) {
        if (!event.hasLid()) {
            throw new IllegalStateException("Event must be saved before being offered");
        }

        if (event.getMeta().isProcessed()) {
            return ChannelEventAuthorization.from(event);
        }

        // Backfill if needed
        // Not applicable for local events
        // TODO check cluster
        if (!StringUtils.equals(remoteDomain, localDomain)) {
            // Backfill the auth chain first
            if (!hasAll(event.asMatrix().getAuthEvents())) {
                try {
                    HomeServerLink originHs = g.overMatrix().hsMgr().getLink(localDomain, remoteDomain);
                    List<JsonObject> authChain = originHs.getAuthChain(getId(), event.getId());
                    algo.orderTopologically(authChain);
                    for (JsonObject authDoc : authChain) {
                        add(authDoc);
                    }
                } catch (RemoteForbiddenException e) {
                    // The remote HS refused to send the auth chain, possibly being malicious
                    // Refusing the event offer
                    throw new ForbiddenException("Refused to provide auth chain for Event " + event.getId() + " in room " + getId());
                }
            }


            List<String> missingParents = findMissingEvents(event.asMatrix().getPreviousEvents());
            // Backfill the timeline
            if (!missingParents.isEmpty()) {
                // Populate the missing events mapping
                Map<String, String> missingEvents = new HashMap<>();
                for (String missingParent : missingParents) {
                    missingEvents.put(missingParent, event.getId());
                }

                // Compute how far backfill must go
                List<ChannelEvent> earliestEvents = getExtremities();
                long minDepth = earliestEvents.stream()
                        .min(Comparator.comparingLong(o -> o.asMatrix().getDepth()))
                        .map(o -> o.asMatrix().getDepth())
                        .orElse(algo.getBaseDepth());
                List<String> earliestEventIds = getExtremityIds(earliestEvents);

                // Compute which servers we will talk to
                Set<String> remoteServers = new LinkedHashSet<>(); // To have unique entries while preserving entry order
                remoteServers.add(remoteDomain);
                remoteServers.addAll(getView().getJoinedServers());
                List<HomeServerLink> servers = g.overMatrix().hsMgr().getLink(localDomain, remoteServers, false);

                Stack<ChannelEvent> eventsToOffer = new Stack<>();
                for (HomeServerLink remoteHs : servers) {
                    if (g.isOrigin(remoteHs.getDomain())) {
                        continue;
                    }

                    while (!missingEvents.isEmpty()) {
                        // Compute the list of latest events not found yet
                        Set<String> latestEvents = new HashSet<>(missingEvents.values());
                        List<JsonObject> backfillEventDocs = remoteHs.getPreviousEvents(getId(), latestEvents, earliestEventIds, minDepth);
                        if (backfillEventDocs.isEmpty()) {
                            // Did not get all the info needed, we stop with this server
                            break;
                        }
                        algo.orderTopologically(backfillEventDocs);

                        for (JsonObject backfillEventDoc : backfillEventDocs) {
                            ChannelEvent backfillEvent = inject(backfillEventDoc);
                            eventsToOffer.push(backfillEvent);
                            missingEvents.remove(backfillEvent.getId());
                            eventsToOffer.push(backfillEvent);
                            missingParents = findMissingEvents(backfillEvent.asMatrix().getPreviousEvents());
                            if (backfillEvent.asMatrix().getDepth() > minDepth && !missingParents.isEmpty()) {
                                for (String parent : missingParents) {
                                    missingEvents.put(parent, backfillEvent.getId());
                                }
                            }
                        }
                    }

                    if (missingEvents.isEmpty()) {
                        // All events were found, exiting the loop
                        break;
                    }
                }

                // Offer all that has been found
                for (ChannelEvent eventToOffer : eventsToOffer) {
                    offer(remoteDomain, localDomain, eventToOffer);
                }
            }
        }

        RoomState eventState = getState(event);
        ChannelEventAuthorization eventStateAuth = algo.authorize(eventState, event.getId(), event.getData());
        if (!eventStateAuth.isAuthorized()) {
            return eventStateAuth;
        }

        RoomState state = getView().getState();
        ChannelEventAuthorization currentStateAuth = algo.authorize(state, event.getId(), event.getData());

        event.processed(currentStateAuth);
        g.getStore().saveEvent(event);
        if (!currentStateAuth.isAuthorized()) {
            return currentStateAuth;
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
        long streamId = g.getStore().addToStream(event.getLid());
        event.setSid(streamId);

        // Publish onto the signal bus
        ChannelMessageProcessed busEvent = new ChannelMessageProcessed(event, currentStateAuth);
        g.getBus().forTopic(SignalTopic.Room).publish(busEvent);

        return currentStateAuth;
    }

    public ChannelEventAuthorization offer(String remoteDomain, String localDomain, JsonObject eventDoc) {
        String eventId = algo.getEventId(eventDoc);
        ChannelEvent event = findEvent(eventId).orElseGet(() -> ChannelEvent.forNotFound(sid, eventId));
        if (event.getMeta().isProcessed()) {
            return ChannelEventAuthorization.from(event);
        }

        if (!event.getMeta().isPresent()) {
            event.getMeta().setProcessed(false);
            event.setData(eventDoc);
            event.getMeta().setReceivedFrom(remoteDomain);
            event.getMeta().setReceivedAt(Instant.now());
        }

        event = g.getStore().saveEvent(event);
        return offer(remoteDomain, localDomain, event);
    }

    public List<ChannelEventAuthorization> offer(String remoteDomain, String localDomain, List<JsonObject> eventDocs) {
        algo.orderTopologically(eventDocs);

        List<ChannelEventAuthorization> auths = new ArrayList<>();
        for (JsonObject doc : eventDocs) {
            auths.add(offer(remoteDomain, localDomain, doc));
        }

        return auths;
    }

    public ChannelEventAuthorization offer(String origin, BareEvent<?> event) {
        event.setOrigin(origin);
        event.setTimestamp(Instant.now().toEpochMilli());
        return offer(origin, origin, finalize(origin, populate(event.getJson())));
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
