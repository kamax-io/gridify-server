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
import io.kamax.gridify.server.config.IdentityConfig;
import io.kamax.gridify.server.core.GridType;
import io.kamax.gridify.server.core.auth.Credentials;
import io.kamax.gridify.server.core.auth.SecureCredentials;
import io.kamax.gridify.server.core.channel.ChannelDao;
import io.kamax.gridify.server.core.channel.event.ChannelEvent;
import io.kamax.gridify.server.core.identity.*;
import io.kamax.gridify.server.core.identity.store.local.LocalAuthIdentityStore;
import io.kamax.gridify.server.exception.ObjectNotFoundException;
import io.kamax.gridify.server.util.GsonUtil;
import io.kamax.gridify.server.util.KxLog;
import org.apache.commons.lang3.StringUtils;
import org.apache.mina.util.ConcurrentHashSet;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class MemoryStore implements DataStore, IdentityStore {

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    private final static Map<String, MemoryStore> singleton = new ConcurrentHashMap<>();

    public static synchronized MemoryStore getNew() {
        return get(UUID.randomUUID().toString());
    }

    public static synchronized MemoryStore get(String o) {
        if (Objects.isNull(o)) {
            o = "";
        }

        return singleton.computeIfAbsent(o, k -> {
            log.debug("Creating new memory store for namespace {}", k);
            return new MemoryStore();
        });
    }

    public static IdentityConfig.Store getMinimalConfig(String id) {
        IdentityConfig.Store cfg = new IdentityConfig.Store();
        cfg.setEnabled(true);
        cfg.setType("memory.internal");
        cfg.setConfig(GsonUtil.makeObj("connection", id));
        return cfg;
    }

    private final AtomicLong dLid = new AtomicLong(0);
    private final AtomicLong uLid = new AtomicLong(0);
    private final AtomicLong chSid = new AtomicLong(0);
    private final AtomicLong evLid = new AtomicLong(0);
    private final AtomicLong evSid = new AtomicLong(0);
    private final AtomicLong sSid = new AtomicLong(0);

    private final Map<String, JsonElement> config = new ConcurrentHashMap<>();
    private final Map<Long, DomainDao> domains = new ConcurrentHashMap<>();
    private final Map<Long, UserDao> users = new ConcurrentHashMap<>();
    private final Map<Long, Set<ThreePid>> userStoreIds = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, SecureCredentials>> userCreds = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> uTokens = new ConcurrentHashMap<>();
    private final Map<Long, Set<ThreePid>> userThreepids = new ConcurrentHashMap<>();
    private final Map<String, Long> uNameToLid = new ConcurrentHashMap<>();

    private final Map<Long, ChannelDao> channels = new ConcurrentHashMap<>();
    private final Map<String, ChannelDao> chIdToDao = new ConcurrentHashMap<>();
    private final Map<Long, ChannelEvent> chEvents = new ConcurrentHashMap<>();
    private final Map<Long, ChannelStateDao> chStates = new ConcurrentHashMap<>();
    private final Map<Long, List<Long>> chFrontExtremities = new ConcurrentHashMap<>();
    private final Map<Long, List<Long>> chBackExtremities = new ConcurrentHashMap<>();
    private final Map<String, String> chAliasToNetId = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Set<String>>> chNetIdToAlias = new ConcurrentHashMap<>();

    private final Map<Long, Long> evStates = new ConcurrentHashMap<>();
    private final Map<String, Long> evRefToLid = new ConcurrentHashMap<>();
    private final Map<Long, Long> evSidToLid = new ConcurrentHashMap<>();
    private final Map<Long, Long> evLidToSid = new ConcurrentHashMap<>();

    private final Map<String, Long> destsStreamId = new ConcurrentHashMap<>();

    private MemoryStore() {
        // only via static
    }

    private String makeRef(ChannelEvent ev) {
        return makeRef(channels.get(ev.getChannelSid()).getId(), ev.getId());
    }

    private String makeRef(String cId, String eId) {
        return cId + "/" + eId;
    }

    @Override
    public void setConfig(String id, JsonElement value) {
        config.put(id, value);
    }

    @Override
    public JsonElement getConfig(String id) {
        JsonElement el = config.get(id);
        if (Objects.isNull(el)) {
            throw new ObjectNotFoundException("Config", id);
        }
        return el;
    }

    @Override
    public DomainDao saveDomain(DomainDao dao) {
        long lid = dLid.incrementAndGet();
        dao.setLocalId(lid);
        domains.put(lid, dao);
        return dao;
    }

    @Override
    public void deleteDomain(DomainDao dao) {
        domains.remove(dao.getLocalId());
    }

    @Override
    public List<DomainDao> listDomains(String network) {
        return domains.values().stream()
                .filter(dao -> StringUtils.equals(network, dao.getNetwork()))
                .collect(Collectors.toList());
    }

    @Override
    public List<ChannelDao> listChannels() {
        return new ArrayList<>(channels.values());
    }

    @Override
    public List<ChannelDao> listChannels(String network) {
        return listChannels().stream()
                .filter(dao -> StringUtils.equals(network, dao.getNetwork()))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ChannelDao> findChannel(long cSid) {
        return Optional.ofNullable(channels.get(cSid));
    }

    @Override
    public Optional<ChannelDao> findChannel(String network, String cId) {
        ChannelDao dao = chIdToDao.get(cId);
        if (Objects.isNull(dao)) {
            return Optional.empty();
        }

        if (!StringUtils.equals(network, dao.getNetwork())) {
            return Optional.empty();
        }

        return Optional.of(dao);
    }

    @Override
    public long addToStream(long eLid) {
        long sid = evSid.incrementAndGet();
        evSidToLid.put(sid, eLid);
        evLidToSid.put(eLid, sid);
        log.debug("Added Event LID {} to stream with SID {}", eLid, sid);
        return sid;
    }

    @Override
    public long getStreamPosition() {
        return evSid.get();
    }

    @Override
    public ChannelDao saveChannel(ChannelDao ch) {
        long sid = chSid.incrementAndGet();
        ch = new ChannelDao(sid, ch.getNetwork(), ch.getId(), ch.getVersion());
        channels.put(sid, ch);
        chIdToDao.put(ch.getId(), ch);
        return ch;
    }

    @Override
    public synchronized ChannelEvent saveEvent(ChannelEvent ev) {
        if (!ev.hasLid()) {
            ev.setLid(evLid.incrementAndGet());
        }

        chEvents.put(ev.getLid(), ev);
        evRefToLid.put(makeRef(ev), ev.getLid());

        log.info("Added new channel event with SID {}", ev.getLid());

        return ev;
    }

    @Override
    public synchronized ChannelEvent getEvent(String cId, String eId) throws ObjectNotFoundException {
        log.debug("Getting Event {}/{}", cId, eId);
        return findEvent(cId, eId).orElseThrow(() -> new ObjectNotFoundException("Event", cId + "/" + eId));
    }

    @Override
    public ChannelEvent getEvent(long eSid) {
        return findEvent(eSid).orElseThrow(() -> new ObjectNotFoundException("Event", Long.toString(eSid)));
    }

    @Override
    public String getEventId(long eSid) {
        return getEvent(eSid).getId();
    }

    @Override
    public long getEventTid(long cLid, String eId) {
        return getEvent(getChannel(cLid).getId(), eId).getSid();
    }

    @Override
    public Optional<Long> findEventLid(String cId, String eId) {
        return Optional.ofNullable(evRefToLid.get(makeRef(cId, eId)));
    }

    @Override
    public List<ChannelDao> searchForRoomsInUserEvents(String network, String type, String stateKey) {
        return null;
    }

    @Override
    public List<ChannelEvent> getNext(long lastSid, long amount) {
        List<ChannelEvent> events = new ArrayList<>();
        while (events.size() < amount) {
            lastSid++;

            log.info("Checking for next event SID {}", lastSid);
            if (!evSidToLid.containsKey(lastSid)) {
                log.info("No such event, end of stream");
                return events;
            }

            log.info("Found next event SID {}, adding", lastSid);
            events.add(getEvent(evSidToLid.get(lastSid)));
            log.info("Incrementing SID");
        }

        log.info("Reached max amount of stream events, returning data");
        return events;
    }

    @Override
    public List<ChannelEvent> getTimelineNext(long cLid, long lastTid, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }

        List<ChannelEvent> events = new ArrayList<>();
        while (events.size() < amount) {
            lastTid++;

            log.info("Checking for next event TID {}", lastTid);
            if (!evSidToLid.containsKey(lastTid)) {
                log.info("No such event, end of timeline");
                return events;
            }

            log.info("Found next event TID {}, adding", lastTid);
            events.add(getEvent(evSidToLid.get(lastTid)));
            log.info("Incrementing SID");
        }

        log.info("Reached max amount of stream events, returning data");

        return events;
    }

    @Override
    public List<ChannelEvent> getTimelinePrevious(long cLid, long lastTid, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }

        List<ChannelEvent> events = new ArrayList<>();
        while (events.size() < amount) {
            lastTid--;

            log.info("Checking for next event TID {}", lastTid);
            if (lastTid == 0 || !evSidToLid.containsKey(lastTid)) {
                log.info("No such event, end of timeline");
                return events;
            }

            log.info("Found next event TID {}, adding", lastTid);
            events.add(getEvent(evSidToLid.get(lastTid)));
            log.info("Incrementing SID");
        }

        log.info("Reached max amount of stream events, returning data");

        return events;
    }

    @Override
    public synchronized Optional<ChannelEvent> findEvent(String cId, String eId) {
        return Optional.ofNullable(evRefToLid.get(makeRef(cId, eId)))
                .flatMap(this::findEvent);
    }

    @Override
    public List<ChannelEvent> findEvents(String network, String eventId) {
        return chEvents.values().stream()
                .filter(ev -> StringUtils.equals(ev.getId(), eventId))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ChannelEvent> findEvent(long eSid) {
        return Optional.ofNullable(chEvents.get(eSid)).map(ev -> {
            ev.setSid(evLidToSid.get(ev.getLid()));
            return ev;
        });
    }

    private List<Long> getOrComputeBackwardExts(long cSid) {
        return new ArrayList<>(chBackExtremities.computeIfAbsent(cSid, k -> new ArrayList<>()));
    }

    @Override
    public void updateBackwardExtremities(long cLid, List<Long> toRemove, List<Long> toAdd) {
        List<Long> exts = getOrComputeBackwardExts(cLid);
        exts.removeAll(toRemove);
        exts.addAll(toAdd);
        chBackExtremities.put(cLid, exts);
    }

    @Override
    public List<Long> getBackwardExtremities(long cLid) {
        return getOrComputeBackwardExts(cLid);
    }

    private List<Long> getOrComputeForwardExts(long cLid) {
        return new ArrayList<>(chFrontExtremities.computeIfAbsent(cLid, k -> new ArrayList<>()));
    }

    @Override
    public synchronized void updateForwardExtremities(long cLid, List<Long> toRemove, List<Long> toAdd) {
        List<Long> exts = getOrComputeForwardExts(cLid);
        exts.removeAll(toRemove);
        exts.addAll(toAdd);
        chFrontExtremities.put(cLid, exts);
    }

    @Override
    public synchronized List<Long> getForwardExtremities(long cSid) {
        return getOrComputeForwardExts(cSid);
    }

    @Override
    public long insertIfNew(long cSid, ChannelStateDao state) {
        if (Objects.nonNull(state.getSid()) && state.getSid() > 0) {
            return state.getSid();
        }

        long sid = sSid.incrementAndGet();
        chStates.put(sid, new ChannelStateDao(sid, state.getEvents()));
        return sid;
    }

    @Override
    public ChannelStateDao getState(long stateSid) throws IllegalStateException {
        return Optional.ofNullable(chStates.get(stateSid)).orElseThrow(IllegalStateException::new);
    }

    @Override
    public void map(long evSid, long stateSid) {
        evStates.put(evSid, stateSid);
    }

    @Override
    public ChannelStateDao getStateForEvent(long evSid) {
        Long sSid = evStates.get(evSid);
        if (Objects.isNull(sSid)) {
            throw new ObjectNotFoundException("State for Event SID", Long.toString(evSid));
        }

        ChannelStateDao state = chStates.get(sSid);
        if (Objects.isNull(state)) {
            throw new ObjectNotFoundException("State SID", Long.toString(sSid));
        }

        return state;
    }

    // FIXME must rename, no longer a username
    @Override
    public boolean hasUsername(String username) {
        return uNameToLid.containsKey(username);
    }

    @Override
    public long getUserCount() {
        return users.size();
    }

    @Override
    public long addUser(String id) {
        if (hasUsername(id)) {
            throw new IllegalStateException(id + " already exists");
        }

        long lid = uLid.incrementAndGet();
        UserDao dao = new UserDao();
        dao.setLid(lid);
        dao.setId(id);
        users.put(lid, dao);
        userCreds.put(lid, new ConcurrentHashMap<>());
        uNameToLid.put(id, lid);

        return lid;
    }

    @Override
    public void addCredentials(long userLid, Credentials credentials) {
        Map<String, SecureCredentials> creds = userCreds.get(userLid);
        if (Objects.isNull(creds)) {
            throw new IllegalStateException("No user with LID " + userLid);
        }

        creds.put(credentials.getType(), SecureCredentials.from(credentials));
    }

    @Override
    public SecureCredentials getCredentials(long userLid, String type) {
        return userCreds.getOrDefault(userLid, new HashMap<>()).get(type);
    }

    @Override
    public Optional<UserDao> findUser(long lid) {
        return Optional.ofNullable(users.get(lid));
    }

    @Override
    public Optional<UserDao> findUser(String username) {
        return Optional.ofNullable(uNameToLid.get(username))
                .flatMap(lid -> Optional.ofNullable(users.get(lid)));
    }

    @Override
    public Optional<UserDao> findUserByStoreLink(ThreePid storeId) {
        for (Map.Entry<Long, Set<ThreePid>> entity : userStoreIds.entrySet()) {
            if (entity.getValue().contains(storeId)) {
                return findUser(entity.getKey());
            }
        }

        return Optional.empty();
    }

    @Override
    public Optional<UserDao> findUserByTreePid(ThreePid tpid) {
        if (GridType.id().local("store.memory.id").equals(tpid.getMedium())) {
            return findUser(tpid.getAddress());
        }

        for (Map.Entry<Long, Set<ThreePid>> entity : userThreepids.entrySet()) {
            if (entity.getValue().contains(tpid)) {
                return findUser(entity.getKey());
            }
        }

        return Optional.empty();
    }

    @Override
    public boolean hasUserAccessToken(String token) {
        for (List<String> tokens : uTokens.values()) {
            for (String v : tokens) {
                if (StringUtils.equals(v, token)) {
                    return true;
                }
            }
        }

        return false;
    }

    private List<String> getTokens(long uLid) {
        return uTokens.computeIfAbsent(uLid, k -> new CopyOnWriteArrayList<>());
    }

    @Override
    public void insertUserAccessToken(long uLid, String token) {
        getTokens(uLid).add(token);
    }

    @Override
    public void deleteUserAccessToken(String token) {
        if (!hasUserAccessToken(token)) {
            throw new ObjectNotFoundException("Access token", "<REDACTED>");
        }

        uTokens.values().forEach(tokens -> tokens.remove(token));
    }

    @Override
    public Optional<String> lookupChannelAlias(String network, String alias) {
        return Optional.ofNullable(chAliasToNetId.get(network + ":" + alias));
    }

    @Override
    public Set<String> findChannelAlias(String network, String networkId, String origin) {
        return chNetIdToAlias.getOrDefault(network + ":" + networkId, new HashMap<>())
                .getOrDefault(origin, new HashSet<>());
    }

    @Override
    public synchronized void setAliases(String network, String networkId, String origin, Set<String> aliases) {
        Map<String, Set<String>> dbAliases = chNetIdToAlias.computeIfAbsent(network + ":" + networkId, k -> new HashMap<>());
        dbAliases.put(origin, aliases);
        aliases.forEach(alias -> chAliasToNetId.put(network + ":" + alias, networkId));
    }

    @Override
    public synchronized void unmap(String network, String chAd) {
        String gAlias = network + ":" + chAd;
        if (!chAliasToNetId.containsKey(gAlias)) {
            throw new ObjectNotFoundException("Channel Address", chAd);
        }
        String gId = network + ":" + chAliasToNetId.remove(gAlias);
        chNetIdToAlias.remove(gId);
    }

    @Override
    public void linkUserToStore(long userLid, ThreePid storeId) {
        Set<ThreePid> storeIds = userStoreIds.computeIfAbsent(userLid, lid -> new ConcurrentHashSet<>());
        storeIds.add(storeId);
    }

    private Set<ThreePid> getThreepids(long userLid) {
        return userThreepids.computeIfAbsent(userLid, k -> new ConcurrentHashSet<>());
    }

    @Override
    public Set<ThreePid> listThreePid(long userLid) {
        findUser(userLid).orElseThrow(() -> new ObjectNotFoundException("User LID " + userLid));

        return new HashSet<>(getThreepids(userLid));
    }

    @Override
    public Set<ThreePid> listThreePid(long userLid, String medium) {
        return listThreePid(userLid).stream()
                .filter(v -> v.getMedium().equals(medium))
                .collect(Collectors.toSet());
    }

    @Override
    public void addThreePid(long userLid, ThreePid tpid) {
        Set<ThreePid> tpids = getThreepids(userLid);
        if (!tpids.add(tpid)) {
            throw new IllegalStateException("Already added");
        }
    }

    @Override
    public void removeThreePid(long userLid, ThreePid tpid) {
        GenericThreePid tpidIn = new GenericThreePid(tpid);
        Set<ThreePid> tpidList = getThreepids(userLid);
        if (!tpidList.remove(tpidIn)) {
            throw new IllegalArgumentException("3PID not found");
        }
    }

    @Override
    public void setStreamIdForDestination(String destinationType, String destination, String scope, long streamId) {
        destsStreamId.put(destinationType + ":" + destination, streamId);
    }

    @Override
    public String getType() {
        return "memory";
    }

    @Override
    public AuthIdentityStore forAuth() {
        return new LocalAuthIdentityStore(this);
    }

    @Override
    public ProfileIdentityStore forProfile() {
        return uid -> Optional.empty();
    }

}
