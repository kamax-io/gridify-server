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
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelEventAuthorization;
import io.kamax.grid.gridepo.core.signal.ChannelMessageProcessed;
import io.kamax.grid.gridepo.core.signal.SignalTopic;
import io.kamax.grid.gridepo.exception.ObjectNotFoundException;
import io.kamax.grid.gridepo.network.matrix.core.event.BareEvent;
import io.kamax.grid.gridepo.network.matrix.core.event.BareGenericEvent;
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

    private long extractDepth(JsonObject doc) {
        return GsonUtil.findLong(doc, EventKey.Depth).orElse(0L);
    }

    private long extractDepth(ChannelEvent event) {
        return extractDepth(event.getData());
    }

    public String getId() {
        return id;
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

    public List<String> getExtremityIds() {
        return getExtremities().stream().map(ChannelEvent::getId).collect(Collectors.toList());
    }

    public JsonObject populate(JsonObject event) {
        List<ChannelEvent> exts = getExtremities();
        List<String> extIds = exts.stream()
                .map(ChannelEvent::getId)
                .collect(Collectors.toList());
        long depth = exts.stream()
                .map(ChannelEvent::getData)
                .mapToLong(data -> GsonUtil.getLong(data, EventKey.Depth))
                .max()
                .orElse(algo.getBaseDepth()) + 1;

        event.addProperty(EventKey.ChannelId, id);
        event.add(EventKey.PrevEvents, GsonUtil.asArray(extIds));
        event.addProperty(EventKey.Depth, depth);
        log.debug("Build event at depth {}", depth);
        return event;
    }

    public JsonObject finalize(String origin, JsonObject event) {
        JsonObject signedOff = algo.signOff(event, g.getCrypto(), origin);
        log.debug("Signed off event: {}", GsonUtil.getPrettyForLog(signedOff));
        return signedOff;
    }

    public ChannelEventAuthorization offer(JsonObject event) {
        ChannelEvent cEv = ChannelEvent.from(sid, algo.getEventId(event), event);
        cEv.getMeta().setReceivedAt(Instant.now());

        return offer(Collections.singletonList(cEv), false).get(0);
    }

    public ChannelEventAuthorization offer(String domain, BareEvent<?> event) {
        event.setOrigin(domain);
        event.setTimestamp(Instant.now().toEpochMilli());
        return offer(finalize(domain, populate(event.getJson())));
    }

    public synchronized List<ChannelEventAuthorization> offer(List<ChannelEvent> events, boolean isSeed) {
        RoomState state = getView().getState();

        List<ChannelEventAuthorization> auths = new ArrayList<>();
        events.stream().sorted(Comparator.comparingLong(this::extractDepth)).forEach(event -> {
            log.info("Room {} - Processing injection of Event {}", getId(), event.getId());
            Optional<ChannelEvent> evStore = g.getStore().findEvent(getId(), event.getId());

            if (evStore.isPresent() && evStore.get().getMeta().isPresent()) {
                log.info("Event {} is known, skipping", event.getId());
                // We already have the event, we skip
                return;
            }

            event.getMeta().setPresent(Objects.nonNull(event.getData()));

            // We check if the event is valid and allowed for the current state before considering processing it
            ChannelEventAuthorization auth = algo.authorize(state, event.getId(), event.getData());
            event.getMeta().setValid(auth.isValid());
            event.getMeta().setAllowed(auth.isAuthorized());

            if (!auth.isAuthorized()) {
                // TODO switch to debug later
                log.info("Event {} not authorized: {}", auth.getEventId(), auth.getReason());
            }

            // Still need to process
            event.getMeta().setProcessed(false);
            if (!isSeed) {
                long minDepth = getExtremities().stream()
                        .min(Comparator.comparingLong(this::extractDepth))
                        .map(this::extractDepth)
                        .orElse(0L);

                backfill(event, getExtremityIds(), minDepth);
            } else {
                log.info("Skipping backfill on seed event {}", event.getId());
            }

            auth = process(event, true, isSeed);

            auths.add(auth);
        });

        return auths;

    }

    public void backfill(ChannelEvent event, List<String> extremities, long minDepth) {
        // FIXME needed for federation
    }

    public boolean isUsable(ChannelEvent ev) {
        return ev.getMeta().isPresent() && ev.getMeta().isProcessed();
    }

    private synchronized ChannelEvent processIfNotAlready(String evId) {
        process(evId, true, false);
        return g.getStore().getEvent(getId(), evId);
    }

    private synchronized ChannelEventAuthorization process(String evId, boolean recursive, boolean force) {
        ChannelEvent ev = g.getStore().getEvent(getId(), evId);
        if (!ev.getMeta().isPresent() || (ev.getMeta().isProcessed() && !force)) {
            return new ChannelEventAuthorization.Builder(evId)
                    .authorize(ev.getMeta().isPresent() && ev.getMeta().isValid() && ev.getMeta().isAllowed(), "From previous computation");
        }

        return process(ev, recursive);
    }

    public synchronized ChannelEventAuthorization process(ChannelEvent ev, boolean recursive) {
        return process(ev, recursive, false);
    }

    public ChannelEventAuthorization process(ChannelEvent event, boolean recursive, boolean isSeed) {
        log.info("Processing event {} in channel {}", event.getId(), event.getChannelId());
        ChannelEventAuthorization.Builder b = new ChannelEventAuthorization.Builder(event.getId());
        BareGenericEvent bEv = GsonUtil.fromJson(event.getData(), BareGenericEvent.class);
        event.getMeta().setPresent(true);

        RoomState state = getView().getState();
        long maxParentDepth = bEv.getDepth() - 1;
        if (!isSeed) {
            maxParentDepth = bEv.getPreviousEvents().stream()
                    .map(pEvId -> {
                        if (recursive) {
                            return processIfNotAlready(pEvId);
                        } else {
                            return g.getStore().findEvent(getId(), pEvId).orElseGet(() -> ChannelEvent.forNotFound(sid, pEvId));
                        }
                    })
                    .filter(this::isUsable)
                    .max(Comparator.comparingLong(pEv -> pEv.getBare().getDepth()))
                    .map(pEv -> pEv.getBare().getDepth())
                    .orElse(Long.MIN_VALUE);
            if (bEv.getPreviousEvents().isEmpty()) {
                maxParentDepth = algo.getBaseDepth();
            }
        }

        if (maxParentDepth == Long.MIN_VALUE) {
            b.deny("No parent event is found or valid, marking event as unauthorized");
        } else {
            long expectedDepth = maxParentDepth + 1;
            if (expectedDepth > bEv.getDepth()) {
                b.invalid("Depth is " + bEv.getDepth() + " but was expected to be at least " + expectedDepth);
            } else {
                ChannelEventAuthorization auth = algo.authorize(state, event.getId(), event.getData()); // FIXME do it on the parents
                b.authorize(auth.isAuthorized(), auth.getReason());
                if (auth.isAuthorized()) {
                    state = state.apply(event);
                }
            }
        }

        ChannelEventAuthorization auth = b.get();
        event.getMeta().setSeed(isSeed);
        event.getMeta().setValid(auth.isValid());
        event.getMeta().setAllowed(isSeed || auth.isAuthorized());
        event.getMeta().setProcessed(true);

        log.info("Event {} is allowed? {}", event.getId(), event.getMeta().isAllowed());
        if (!event.getMeta().isAllowed()) {
            log.info("Because: {}", auth.getReason());
        }

        event = g.getStore().saveEvent(event);
        long stateStoreId = g.getStore().insertIfNew(sid, state);
        state = new RoomState(stateStoreId, state);
        g.getStore().map(event.getLid(), state.getSid());
        g.getStore().addToStream(event.getLid());

        if (event.getMeta().isAllowed()) {
            List<Long> toRemove = event.getPreviousEvents().stream()
                    .map(id -> g.getStore().findEventLid(getId(), id))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
            List<Long> toAdd = Collections.singletonList(event.getLid());
            g.getStore().updateForwardExtremities(sid, toRemove, toAdd);
            view = new RoomView(event.getId(), state);
        }

        ChannelMessageProcessed busEvent = new ChannelMessageProcessed(g.getStore().getEvent(event.getLid()), auth);
        g.getBus().forTopic(SignalTopic.Channel).publish(busEvent);

        return auth;
    }

    public ChannelEventAuthorization inject(String sender, JsonObject seedDoc, List<JsonObject> stateDocs) {
        return new ChannelEventAuthorization.Builder("unknown").deny("Not implemented");
    }

    public RoomView getView() {
        return view;
    }

    public RoomState getState(String eventId) {
        // FIXME could optimise and directly get the data from the storage in a single call
        return new RoomState(g.getStore().getStateForEvent(g.getStore().getEvent(id, eventId).getLid()));
    }

    public RoomTimeline getTimeline() {
        return new RoomTimeline(sid, g.getStore());
    }

}
