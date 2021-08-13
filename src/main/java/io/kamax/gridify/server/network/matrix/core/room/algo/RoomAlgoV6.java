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

package io.kamax.gridify.server.network.matrix.core.room.algo;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.codec.CanonicalJson;
import io.kamax.gridify.server.codec.GridHash;
import io.kamax.gridify.server.core.channel.event.ChannelEvent;
import io.kamax.gridify.server.core.channel.state.ChannelEventAuthorization;
import io.kamax.gridify.server.core.crypto.Cryptopher;
import io.kamax.gridify.server.network.matrix.core.crypto.CryptoJson;
import io.kamax.gridify.server.network.matrix.core.event.*;
import io.kamax.gridify.server.network.matrix.core.federation.RoomJoinTemplate;
import io.kamax.gridify.server.network.matrix.core.room.RoomJoinRule;
import io.kamax.gridify.server.network.matrix.core.room.RoomMembership;
import io.kamax.gridify.server.network.matrix.core.room.RoomState;
import io.kamax.gridify.server.util.GsonUtil;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RoomAlgoV6 implements RoomAlgo {

    public static final String Version = "6";
    static final long minDepth = 0;

    private static final List<String> essentialTopKeys;
    private static final Map<String, List<String>> essentialContentKeys = new HashMap<>();

    private static final Map<String, Class<? extends BareEvent<?>>> bares = new HashMap<>();

    static {
        bares.put(RoomEventType.Create.getId(), BareCreateEvent.class);
        bares.put(RoomEventType.Member.getId(), BareMemberEvent.class);
        bares.put(RoomEventType.Power.getId(), BarePowerEvent.class);
        bares.put(RoomEventType.JoinRules.getId(), BareJoinRulesEvent.class);

        essentialTopKeys = Arrays.asList(
                EventKey.AuthEvents,
                EventKey.Content,
                EventKey.Depth,
                EventKey.EventId,
                EventKey.Hashes,
                EventKey.Origin,
                EventKey.Timestamp,
                EventKey.PrevEvents,
                EventKey.PrevState,
                EventKey.RoomId,
                EventKey.Sender,
                EventKey.Signatures,
                EventKey.StateKey,
                EventKey.Type
        );

        essentialContentKeys.put(RoomEventType.Alias.getId(), Collections.singletonList("aliases"));
        essentialContentKeys.put(RoomEventType.Create.getId(), Collections.singletonList("creator"));
        essentialContentKeys.put(RoomEventType.HistoryVisibility.getId(), Collections.singletonList("history_visiblity"));
        essentialContentKeys.put(RoomEventType.JoinRules.getId(), Collections.singletonList("join_rule"));
        essentialContentKeys.put(RoomEventType.Member.getId(), Collections.singletonList("membership"));
        essentialContentKeys.put(RoomEventType.Power.getId(), Arrays.asList(
                "ban",
                "events",
                "events_default",
                "kick",
                "redact",
                "state_default",
                "users",
                "users_default"
        ));
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

    public Comparator<JsonObject> getTopologyComparator() {
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
    public JsonObject buildJoinEvent(RoomJoinTemplate template) {
        // We build a fresh event that we trust (no hidden keys or whatever)
        BareMemberEvent eventBare = BareMemberEvent.join(template.getUserId());
        eventBare.setRoomId(template.getRoomId());
        eventBare.setOrigin(template.getOrigin());
        eventBare.setSender(template.getUserId());
        eventBare.setTimestamp(Instant.now().toEpochMilli());

        // We only take the info we need from the template
        BareMemberEvent templateBare = GsonUtil.fromJson(template.getEvent(), BareMemberEvent.class);
        eventBare.setAuthEvents(templateBare.getAuthEvents());
        eventBare.setPreviousEvents(templateBare.getPreviousEvents());
        eventBare.setDepth(templateBare.getDepth());

        return eventBare.getJson();
    }

    @Override
    public Set<String> getAuthEvents(JsonObject eventDoc, RoomState state) {
        // https://matrix.org/docs/spec/server_server/r0.1.4#get-matrix-federation-v1-make-join-roomid-userid
        // https://matrix.org/docs/spec/server_server/r0.1.4#auth-events-selection

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

    public String computeEventHashUnsafe(JsonObject doc) {
        doc = redact(doc);
        doc.remove(EventKey.Age); // Never seen it into a doc?
        doc.remove(EventKey.Signatures);
        doc.remove(EventKey.Unsigned);
        String canonical = CanonicalJson.encode(doc);
        return GridHash.get().hashFromUtf8(canonical);
    }

    @Override
    public String computeEventHash(JsonObject doc) {
        return computeEventHashUnsafe(doc.deepCopy());
    }

    public String computeContentHashUnsafe(JsonObject doc) {
        doc.remove(EventKey.Hashes);
        doc.remove(EventKey.Signatures);
        doc.remove(EventKey.Unsigned);
        String canonical = CanonicalJson.encode(doc);
        return GridHash.get().hashRawFromUtf8(canonical);
    }

    private JsonObject computeContentHashObject(JsonObject doc) {
        doc = doc.deepCopy();
        String hash = computeContentHashUnsafe(doc);
        return GsonUtil.makeObj("sha256", hash);
    }

    @Override
    public JsonObject signEvent(JsonObject event, Cryptopher crypto, String domain) {
        // We compute the content hash
        JsonObject hashesDoc = computeContentHashObject(event);
        // We redact the event to its minimal state
        JsonObject docMinimal = redact(event);
        // We add the content hash
        docMinimal.add(EventKey.Hashes, hashesDoc);
        JsonObject docMinimalSigned = CryptoJson.signUnsafe(docMinimal, crypto, domain);

        // We copy the signatures from the minimal signed event onto the full event
        event.add(EventKey.Hashes, hashesDoc);
        event.add(EventKey.Signatures, docMinimalSigned.remove(EventKey.Signatures));

        return event;
    }

    @Override
    public JsonObject redact(JsonObject doc) {
        JsonObject toRedact = doc.deepCopy();

        // OLD-CODE TODO Why use a new HashSet? Concurrent error?
        new HashSet<>(toRedact.keySet()).forEach(key -> {
            if (!essentialTopKeys.contains(key)) toRedact.remove(key);
        });

        JsonObject content = GsonUtil.popOrCreateObj(toRedact, EventKey.Content);
        String type = GsonUtil.getStringOrNull(toRedact, EventKey.Type);
        List<String> essentials = essentialContentKeys.getOrDefault(type, Collections.emptyList());
        new HashSet<>(content.keySet()).forEach(key -> {
            if (!essentials.contains(key)) content.remove(key);
        });
        toRedact.add(EventKey.Content, content);

        return toRedact;
    }

}
