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

import io.kamax.gridify.server.core.channel.event.ChannelEvent;
import io.kamax.gridify.server.core.store.ChannelStateDao;
import io.kamax.gridify.server.network.matrix.core.event.*;
import io.kamax.gridify.server.util.GsonUtil;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RoomState {

    private static Function<String, Optional<RoomMembership>> membershipMapper() {
        return id -> {
            for (RoomMembership m : RoomMembership.values()) {
                if (m.match(id)) {
                    return Optional.of(m);
                }
            }

            return Optional.empty();
        };
    }

    private static Function<String, Optional<RoomJoinRule>> joinRuleMapper() {
        return id -> {
            for (RoomJoinRule m : RoomJoinRule.values()) {
                if (m.match(id)) {
                    return Optional.of(m);
                }
            }

            return Optional.empty();
        };
    }

    public static RoomState empty() {
        return new RoomState();
    }

    private long sid = 0L;
    private boolean trusted = false;
    private boolean complete = false;
    private boolean finall = false; // because final is a reserved keyword
    private Map<String, ChannelEvent> data = new HashMap<>();

    private RoomState() {

    }

    public RoomState(long sid, RoomState state) {
        this.sid = sid;
        this.trusted = state.trusted;
        this.complete = state.complete;
        this.finall = state.finall;
        this.data = new HashMap<>(state.data);
    }

    public RoomState(boolean trusted, boolean complete, boolean finall, List<ChannelEvent> events) {
        this.trusted = trusted;
        this.complete = complete;
        this.finall = finall;
        events.forEach(this::addEvent);
    }

    public RoomState(long sid, boolean trusted, boolean complete, boolean finall, List<ChannelEvent> events) {
        this(trusted, complete, finall, events);
        this.sid = sid;
    }

    public RoomState(ChannelStateDao dao) {
        this(dao.getSid(), dao.isTrusted(), dao.isComplete(), dao.isFinal(), dao.getEvents());
    }

    public RoomState(List<ChannelEvent> events) {
        this(false, false, false, events);
    }

    private RoomState copy() {
        RoomState r = new RoomState();
        r.sid = sid;
        r.trusted = trusted;
        r.complete = complete;
        r.finall = finall;
        r.data = new HashMap<>(data);
        return r;
    }

    private void addEvent(ChannelEvent ev) {
        String type = GsonUtil.getStringOrThrow(ev.getData(), EventKey.Type);
        String scope = GsonUtil.getStringOrThrow(ev.getData(), EventKey.StateKey);
        String key = type + scope;
        data.put(key, ev);
    }

    public long getSid() {
        return sid;
    }

    public Optional<ChannelEvent> find(String type, String scope) {
        return Optional.ofNullable(data.get(type + scope));
    }

    public Optional<ChannelEvent> find(RoomEventType type) {
        return find(type.getId(), "");
    }

    public Optional<ChannelEvent> find(RoomEventType type, String scope) {
        return find(type.getId(), scope);
    }

    public <T> Optional<T> find(String type, Class<T> c) {
        return find(type, "", c);
    }

    public <T> Optional<T> find(String type, String scope, Class<T> c) {
        return find(type, scope).map(j -> GsonUtil.fromJson(j.getData(), c));
    }

    public <T> Optional<T> find(RoomEventType type, Class<T> c) {
        return find(type.getId(), c);
    }

    public <T> Optional<T> find(RoomEventType type, String scope, Class<T> c) {
        return find(type.getId(), scope, c);
    }

    public List<ChannelEvent> getEvents() {
        return new ArrayList<>(data.values());
    }

    public ChannelEvent getCreation() {
        return find(RoomEventType.Create).orElseThrow(IllegalStateException::new);
    }

    public String getCreationId() throws IllegalStateException {
        return getCreation().getId();
    }

    public Optional<BarePowerEvent.Content> getPowers() {
        return find(RoomEventType.Power, BarePowerEvent.class)
                .map(BarePowerEvent::getContent);
    }

    public Optional<RoomMembership> findMembership(String userId) {
        return find(RoomEventType.Member, userId, BareMemberEvent.class)
                .map(ev -> ev.getContent().getMembership())
                .flatMap(membershipMapper());
    }

    public RoomMembership getMembership(String userId) {
        return findMembership(userId).orElse(RoomMembership.Leave);
    }

    public List<ChannelEvent> getMembers() {
        return data.values().stream()
                .filter(ev -> RoomEventType.Member.match(ev.getData()))
                //.filter(ev -> RoomMembership.Join.match(BareMemberEvent.computeMembership(ev.getData())))
                .collect(Collectors.toList());
    }

    public Optional<RoomJoinRule> getJoinRule() {
        return find(RoomEventType.JoinRules, BareJoinRulesEvent.class)
                .map(ev -> ev.getContent().getRule())
                .flatMap(joinRuleMapper());
    }

    public RoomState apply(ChannelEvent ev) {
        if (!ev.getMeta().isPresent()) {
            return this;
        }

        String scope = GsonUtil.getStringOrNull(ev.getData(), EventKey.StateKey);
        if (Objects.isNull(scope)) {
            return this;
        }

        if (!ev.getMeta().isAllowed()) {
            return this;
        }

        RoomState state = new RoomState();
        state.data = new HashMap<>(data);
        state.addEvent(ev);

        return state;
    }

    public boolean isTrusted() {
        return trusted;
    }

    public boolean isComplete() {
        return complete;
    }

    public boolean isFinal() {
        return finall;
    }

    public RoomState setTrusted(boolean trusted) {
        if (this.trusted == trusted) {
            return this;
        }

        RoomState state = copy();
        state.trusted = trusted;
        return state;
    }

    public RoomState setComplete(boolean complete) {
        if (this.complete == complete) {
            return this;
        }

        RoomState state = copy();
        state.complete = complete;
        return state;
    }

    public RoomState setFinal(boolean finall) {
        if (this.finall == finall) {
            return this;
        }

        RoomState state = copy();
        state.finall = finall;
        return state;
    }

    public RoomState fromPreviousEvents() {
        return setTrusted(true).setComplete(true).setFinal(true);
    }

    public RoomState fromAuthEvents() {
        return setTrusted(true).setComplete(true).setFinal(false);
    }

}
