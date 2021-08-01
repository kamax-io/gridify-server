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

package io.kamax.grid.gridepo.network.matrix.core.room.algo;

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.codec.GridHash;
import io.kamax.grid.gridepo.codec.GridJson;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelEventAuthorization;
import io.kamax.grid.gridepo.core.crypto.Cryptopher;
import io.kamax.grid.gridepo.core.crypto.Signature;
import io.kamax.grid.gridepo.network.matrix.core.event.*;
import io.kamax.grid.gridepo.network.matrix.core.room.RoomJoinRule;
import io.kamax.grid.gridepo.network.matrix.core.room.RoomMembership;
import io.kamax.grid.gridepo.network.matrix.core.room.RoomState;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RoomAlgoV6 implements RoomAlgo {

    public static final String Version = "6";
    static final long minDepth = 0;

    private static final Map<String, Class<? extends BareEvent<?>>> bares = new HashMap<>();

    static {
        bares.put(RoomEventType.Create.getId(), BareCreateEvent.class);
        bares.put(RoomEventType.Member.getId(), BareMemberEvent.class);
        bares.put(RoomEventType.Power.getId(), BarePowerEvent.class);
        bares.put(RoomEventType.JoinRules.getId(), BareJoinRulesEvent.class);
    }

    private BareGenericEvent toProto(JsonObject ev) {
        return GsonUtil.fromJson(ev, BareGenericEvent.class);
    }

    public BarePowerEvent getDefaultPowersEvent(String creator) {
        BarePowerEvent ev = new DefaultPowerEvent();
        ev.getContent().getUsers().put(creator, 100L);
        return ev;
    }

    public BarePowerEvent.Content getDefaultPowers(String creator) {
        return getDefaultPowersEvent(creator).getContent();
    }

    private Comparator<JsonObject> getComparatorByParents() {
        return (ev1, ev2) -> {
            String ev1Id = getEventId(ev1);
            String ev2Id = getEventId(ev2);

            List<String> ev1PrevEvents = BareGenericEvent.getPrevEvents(ev1);
            List<String> ev1AuthEvents = BareGenericEvent.getAuthEvents(ev1);
            List<String> ev2PrevEvents = BareGenericEvent.getPrevEvents(ev2);
            List<String> ev2AuthEvents = BareGenericEvent.getAuthEvents(ev2);

            if (ev1PrevEvents.contains(ev2Id)) {
                return 1;
            }

            if (ev1AuthEvents.contains(ev2Id)) {
                return 1;
            }

            if (ev2PrevEvents.contains(ev1Id)) {
                return -1;
            }

            if (ev2AuthEvents.contains(ev1Id)) {
                return -1;
            }

            return 0;
        };
    }

    private Comparator<JsonObject> getTopologyComparator() {
        return getComparatorByParents()
                .thenComparingLong(BareGenericEvent::extractDepth)
                .thenComparingLong(BareGenericEvent::extractTimestampt)
                .thenComparing(this::getEventId);
    }

    @Override
    public List<JsonObject> orderTopologically(List<JsonObject> events) {
        events.sort(getTopologyComparator());
        return events;
    }

    private boolean canDoMembership(long senderPl, RoomMembership m, BarePowerEvent.Content pls) {
        Long actionPl = null;

        if (RoomMembership.Kick.equals(m)) {
            actionPl = pls.getKick();
        }
        if (RoomMembership.Ban.equals(m)) {
            actionPl = pls.getBan();
        }
        if (RoomMembership.Invite.equals(m)) {
            actionPl = pls.getInvite();
        }

        if (Objects.isNull(actionPl)) {
            throw new IllegalArgumentException();
        }

        return senderPl >= actionPl;
    }

    private boolean canEvent(BarePowerEvent.Content pls, long senderPl, BareEvent<?> ev) {
        Long defPl;

        if (StringUtils.isNotEmpty(ev.getStateKey())) {
            defPl = pls.getStateDefault();
        } else {
            defPl = pls.getEventsDefault();
        }

        return senderPl >= pls.getEvents().getOrDefault(ev.getType(), defPl);
    }

    private boolean canSetTo(long oldPl, long newPl, long withPl) {
        if (oldPl == newPl) {
            return true;
        }

        if (oldPl > withPl) {
            return false;
        }

        return withPl >= newPl;
    }

    private boolean willFail(long withPl, long defaultPl, Map<String, Long> oldPls, Map<String, Long> newPls) {
        return !Stream.concat(oldPls.keySet().stream(), newPls.keySet()
                .stream()).collect(Collectors.toSet()).stream()
                .allMatch(type -> {
                    long oldPl = oldPls.getOrDefault(type, defaultPl);
                    long newPl = newPls.getOrDefault(type, defaultPl);
                    return withPl >= oldPl && withPl >= newPl;
                });
    }

    private boolean canReplace(String sender, long withPl, BarePowerEvent.Content pls, BarePowerEvent.Content newPls) {
        boolean basic = canSetTo(pls.getEventsDefault(), newPls.getEventsDefault(), withPl) &&
                canSetTo(pls.getStateDefault(), newPls.getStateDefault(), withPl) &&
                canSetTo(pls.getUsersDefault(), newPls.getUsersDefault(), withPl);
        if (!basic) {
            return false;
        }

        boolean membership = canSetTo(pls.getBan(), newPls.getBan(), withPl) &&
                canSetTo(pls.getKick(), newPls.getKick(), withPl) &&
                canSetTo(pls.getInvite(), newPls.getInvite(), withPl);
        if (!membership) {
            return false;
        }

        if (willFail(withPl, pls.getEventsDefault(), pls.getEvents(), newPls.getEvents())) {
            return false;
        }

        if (willFail(withPl, pls.getUsersDefault(), pls.getUsers(), newPls.getUsers())) {
            return false;
        }

        return Stream.concat(pls.getUsers().keySet().stream(), newPls.getUsers().keySet().stream())
                .collect(Collectors.toSet()).stream()
                .allMatch(id -> {
                    if (StringUtils.equals(sender, id)) {
                        // We already know we are not giving a higher PL
                        // It is ok to give ourselves the same PL or lower
                        return true;
                    }

                    long oldTargetPl = pls.getUsers().getOrDefault(id, pls.getUsersDefault());
                    long newTargetPl = newPls.getUsers().getOrDefault(id, newPls.getUsersDefault());
                    if (oldTargetPl == newTargetPl) {
                        // The PL is not changing, so it's OK
                        return true;
                    }

                    // We already know we are not giving a higher PL than we can
                    // We check if we have more PL than the target
                    return withPl > oldTargetPl;
                });
    }

    @Override
    public String getVersion() {
        return Version;
    }

    @Override
    public long getBaseDepth() {
        return minDepth;
    }

    @Override
    public long getCreateDepth() {
        return getBaseDepth() + 1;
    }

    @Override
    public String validate(JsonObject evRaw) {
        String eventId = "!" + computeEventHash(evRaw);
        BareGenericEvent ev = toProto(evRaw);
        ev.setId(eventId);

        if (StringUtils.isEmpty(ev.getId())) {
            return "Invalid event, no ID";
        }

        if (StringUtils.isEmpty(ev.getType())) {
            return "Event " + ev.getId() + ": Invalid: Type is missing/empty";
        }

        if (Objects.isNull(ev.getTimestamp())) {
            return "Event " + ev.getId() + ": Invalid: Timestamp is missing";
        }

        if (StringUtils.isEmpty(ev.getOrigin())) {
            return "Event " + ev.getId() + ": Invalid: Origin is missing/empty";
        }

        if (StringUtils.isEmpty(ev.getSender())) {
            return "Event " + ev.getId() + ": Invalid: Sender is missing/empty";
        }

        if (Objects.isNull(ev.getPreviousEvents())) {
            return "Event " + ev.getId() + ": Invalid: Parents missing";
        }

        if (Objects.isNull(ev.getDepth())) {
            return "Event " + ev.getId() + ": Invalid: Depth is missing";
        }

        return "";
    }

    @Override
    public ChannelEventAuthorization authorizeCreate(JsonObject doc) {
        RoomState state = RoomState.empty();
        String evId = computeEventHash(doc);
        return authorize(state, evId, doc);
    }

    @Override
    public ChannelEventAuthorization authorize(RoomState state, String evId, JsonObject evRaw) {
        BareGenericEvent ev = toProto(evRaw);

        ChannelEventAuthorization.Builder auth = new ChannelEventAuthorization.Builder(evId);
        String validation = validate(evRaw);
        if (StringUtils.isNotEmpty(validation)) {
            return auth.invalid(validation);
        }

        String evType = ev.getType();

        Optional<BareCreateEvent> cOpt = state.find(RoomEventType.Create.getId(), BareCreateEvent.class);
        if (RoomEventType.Create.match(evType)) { //TODO check that the room version matches what we support
            if (cOpt.isPresent()) {
                return auth.deny("Room is already created");
            }

            if (ev.getDepth() != getCreateDepth()) { // FIXME Do this into some earlier event check
                return auth.deny("Invalid create event: Depth is not " + getCreateDepth());
            }

            if (!ev.getPreviousEvents().isEmpty()) { // FIXME Do this into some earlier event check
                return auth.deny("Invalid create event: has previous events");
            }

            return auth.allow();
        }

        if (!cOpt.isPresent()) {
            return auth.deny("Room does not exist as per state");
        }

        BareCreateEvent.Content cEv = cOpt.map(BareCreateEvent::getContent).get();

        BarePowerEvent.Content pls = DefaultPowerEvent.applyDefaults(state.getPowers().orElseGet(() -> getDefaultPowers(cEv.getCreator())));
        String sender = ev.getSender();
        RoomMembership senderMs = state.findMembership(sender).orElse(RoomMembership.Leave);
        long senderPl = pls.getUsers().getOrDefault(sender, pls.getUsersDefault());

        if (RoomEventType.Member.match(evType)) {
            BareMemberEvent mEv = GsonUtil.fromJson(evRaw, BareMemberEvent.class);
            String membership = mEv.getContent().getMembership();
            String target = mEv.getStateKey();
            RoomMembership targetMs = state.findMembership(target).orElse(RoomMembership.Leave);
            long targetPl = pls.getUsers().getOrDefault(target, pls.getUsersDefault());

            if (RoomMembership.Join.match(membership)) {
                if (!StringUtils.equals(sender, target)) {
                    return auth.deny("Sender and target are different");
                }

                if (ev.getDepth() == (getCreateDepth() + 1)) { // Initial join
                    if (ev.getPreviousEvents().size() != 1) {
                        return auth.deny("Initial join event can only have one parent");
                    }

                    String eCreateId = state.getCreationId();
                    String pCreateId = ev.getPreviousEvents().get(0);

                    if (!StringUtils.equals(eCreateId, pCreateId)) {
                        return auth.deny("Initial join does not refer to create event as parent");
                    }

                    if (!StringUtils.equals(cEv.getCreator(), target)) {
                        return auth.deny("Initial join does not match channel creator");
                    }

                    return auth.allow();
                } else { // Regular joins
                    if (RoomMembership.Join.equals(targetMs)) {
                        return auth.allow();
                    }

                    if (RoomMembership.Invite.equals(targetMs)) {
                        return auth.allow();
                    }

                    RoomJoinRule rule = state.getJoinRule().orElse(RoomJoinRule.Private);
                    if (RoomJoinRule.Public.equals(rule)) {
                        return auth.allow();
                    } else {
                        return auth.deny("Public join is not allowed");
                    }
                }
            } else if (RoomMembership.Invite.match(membership)) {
                if (!RoomMembership.Join.equals(senderMs)) {
                    return auth.deny("Invite sender is not joined to the channel");
                }

                if (RoomMembership.Ban.equals(targetMs)) {
                    return auth.deny("Invite target is banned");
                }

                if (RoomMembership.Join.equals(targetMs)) {
                    return auth.deny("Invite target is already joined");
                }

                if (!canDoMembership(senderPl, RoomMembership.Invite, pls)) {
                    return auth.deny("Sender does not have the required Power Level to invite");
                }

                return auth.allow();
            } else if (RoomMembership.Leave.match(membership)) {
                boolean isSame = StringUtils.equals(sender, target);
                if (isSame && senderMs.isAny(RoomMembership.Join, RoomMembership.Invite)) {
                    return auth.allow();
                }

                if (!senderMs.equals(RoomMembership.Join)) {
                    return auth.deny("Sender cannot send in a room they are not joined");
                }

                if (RoomMembership.Ban.equals(targetMs) && canDoMembership(senderPl, RoomMembership.Ban, pls)) {
                    return auth.deny("Sender does not have the required Power Level to remove a ban");
                }

                if (canDoMembership(senderPl, RoomMembership.Kick, pls)) {
                    return auth.deny("Sender does not have the required Power Level to kick");
                }

                if (senderPl <= targetPl) {
                    return auth.deny("Sender Power Level is not higher than the target Power Level");
                }

                return auth.allow();
            } else if (RoomMembership.Ban.match(membership)) {
                if (!senderMs.equals(RoomMembership.Join)) {
                    return auth.deny("Sender cannot send in a room they are not joined");
                }

                if (canDoMembership(senderPl, RoomMembership.Ban, pls)) {
                    return auth.deny("Sender does not have the required Power Level to ban");
                }

                if (senderPl <= targetPl) {
                    return auth.deny("Sender Power Level is not higher than the target Power Level");
                }

                return auth.allow();
            } else {
                return auth.deny("Unknown membership " + membership);
            }
        }

        if (!senderMs.equals(RoomMembership.Join)) {
            return auth.deny("Sender cannot send in a room they are not joined");
        }

        if (!canEvent(pls, senderPl, ev)) {
            return auth.deny("Sender does not have minimum PL for event type " + ev.getType());
        }

        if (RoomEventType.Power.match(evType)) {
            BarePowerEvent.Content newPls = DefaultPowerEvent.applyDefaults(GsonUtil.fromJson(evRaw, DefaultPowerEvent.class).getContent());
            if (pls.getEventsDefault() > senderPl || newPls.getEventsDefault() > senderPl) {
                return auth.deny("Sender is missing minimum Power Level to change Power Level settings");
            }

            if (pls.getStateDefault() > senderPl || newPls.getStateDefault() > senderPl) {
                return auth.deny("Sender is missing minimum Power Level to change Power Level settings");
            }

            if (!canReplace(sender, senderPl, pls, newPls)) {
                return auth.deny("Sender is missing minimum Power Level to change Power Level settings");
            }

            return auth.allow();
        }

        return auth.allow();
    }

    @Override
    public JsonObject buildJoinEvent(String origin, String userId, JsonObject template) {
        // We build a fresh event that we trust (no hidden keys or whatever)
        BareMemberEvent eventBare = BareMemberEvent.join(userId);
        eventBare.setOrigin(origin);
        eventBare.setSender(userId);
        eventBare.setTimestamp(Instant.now().toEpochMilli());

        // We only take the info we need from the template
        BareMemberEvent templateBare = GsonUtil.fromJson(template, BareMemberEvent.class);
        eventBare.setAuthEvents(templateBare.getAuthEvents());
        eventBare.setPreviousEvents(templateBare.getPreviousEvents());
        eventBare.setDepth(templateBare.getDepth());

        return eventBare.getJson();
    }

    @Override
    public Set<String> getAuthEvents(JsonObject eventDoc, RoomState state) {
        // https://matrix.org/docs/spec/server_server/r0.1.4#get-matrix-federation-v1-make-join-roomid-userid

        if (state.getEvents().isEmpty()) {
            return Collections.emptySet();
        }

        Set<ChannelEvent> authEvents = new HashSet<>();
        BareGenericEvent genericEvent = BareGenericEvent.fromJson(eventDoc);
        authEvents.add(state.getCreation());
        state.find(RoomEventType.Member, genericEvent.getSender()).ifPresent(authEvents::add);
        state.find(RoomEventType.Power, "").ifPresent(authEvents::add);

        if (RoomEventType.Member.match(genericEvent.getType())) {
            BareMemberEvent memberEvent = GsonUtil.fromJson(eventDoc, BareMemberEvent.class);
            state.find(RoomEventType.Member, memberEvent.getStateKey())
                    .ifPresent(authEvents::add);

            String membership = memberEvent.getContent().getMembership();
            if (RoomMembership.Invite.match(membership) || RoomMembership.Join.match(membership)) {
                state.find(RoomEventType.JoinRules).ifPresent(authEvents::add);
            }

            // TODO third party invites
        }

        return authEvents.stream().map(ChannelEvent::getId).collect(Collectors.toSet());
    }

    @Override
    public String getEventId(JsonObject event) {
        return "$" + computeEventHash(event);
    }

    @Override
    public List<BareEvent<?>> getCreationEvents(String domain, String creator, JsonObject options) {
        List<BareEvent<?>> events = new ArrayList<>();
        BareCreateEvent createEv = new BareCreateEvent();
        createEv.setTimestamp(Instant.now().toEpochMilli());
        createEv.setOrigin(domain);
        createEv.setSender(creator);
        createEv.getContent().setCreator(creator);
        createEv.getContent().setVersion(Version);
        events.add(createEv);

        BareMemberEvent cJoinEv = new BareMemberEvent();
        createEv.setTimestamp(Instant.now().toEpochMilli());
        cJoinEv.setOrigin(domain);
        cJoinEv.setSender(creator);
        cJoinEv.setStateKey(creator);
        cJoinEv.getContent().setMembership(RoomMembership.Join);
        events.add(cJoinEv);

        BarePowerEvent cPlEv = getDefaultPowersEvent(creator);
        createEv.setTimestamp(Instant.now().toEpochMilli());
        cPlEv.setOrigin(domain);
        cPlEv.setSender(creator);
        events.add(cPlEv);

        GsonUtil.findString(options, "name").ifPresent(name -> {
            BareNameEvent nameEvent = new BareNameEvent();
            nameEvent.setOrigin(domain);
            nameEvent.setSender(creator);
            nameEvent.getContent().setName(name);
            events.add(nameEvent);
        });

        GsonUtil.findString(options, "topic").ifPresent(topic -> {
            BareTopicEvent topicEvent = new BareTopicEvent();
            topicEvent.setOrigin(domain);
            topicEvent.setSender(creator);
            topicEvent.getContent().setTopic(topic);
            events.add(topicEvent);
        });

        // TODO support remaining options

        return events;
    }

    public String computeHash(JsonObject event) {
        String canonical = GridJson.encodeCanonical(event);
        return GridHash.get().hashFromUtf8(canonical);
    }

    @Override
    public String computeEventHash(JsonObject event) {
        event = redact(event);
        event.remove(EventKey.Age);
        event.remove(EventKey.Signatures);
        event.remove(EventKey.Unsigned);
        return computeHash(event);
    }

    @Override
    public String computeContentHash(JsonObject event) {
        event = event.deepCopy();
        event.remove(EventKey.Hashes);
        event.remove(EventKey.Signatures);
        event.remove(EventKey.Unsigned);
        return computeHash(event);
    }

    @Override
    public Signature computeSignature(JsonObject event, Cryptopher crypto) {
        // We redact the event to its minimal state
        JsonObject eventRedacted = redact(event);
        // We get the canonical version
        String eventCanonical = GridJson.encodeCanonical(eventRedacted);
        // We generate the signature for the event
        return crypto.sign(eventCanonical, crypto.getServerSigningKey().getId());
    }

    @Override
    public JsonObject sign(JsonObject event, Cryptopher crypto, String domain) {
        // We generate the signature for the event
        Signature sign = computeSignature(event, crypto);
        JsonObject signLocal = GsonUtil.makeObj(sign.getKey().getId(), sign.getSignature());

        // We retrieve the signatures dictionary or generate an empty one if none exists
        JsonObject signatures = GsonUtil.findObj(event, EventKey.Signatures).orElseGet(JsonObject::new);
        // We add the signature to the signatures dictionary
        signatures.add(domain, signLocal);
        // We replace the event signatures original dictionary with the new one
        event.add(EventKey.Signatures, signatures);

        return event;
    }

    @Override
    public JsonObject signOff(JsonObject doc, Cryptopher crypto, String domain) {
        String hash = computeContentHash(doc);
        JsonObject hashes = GsonUtil.makeObj("sha256", hash);
        doc.add(EventKey.Hashes, hashes);
        return sign(doc, crypto, domain);
    }

    @Override
    public JsonObject redact(JsonObject doc) {
        String type = GsonUtil.getStringOrThrow(doc, EventKey.Type);
        Class<? extends BareEvent<?>> evClass = bares.getOrDefault(type, BareGenericEvent.class);
        BareEvent<?> minEv = GsonUtil.get().fromJson(doc, evClass);
        return minEv.getJson();
    }

}
