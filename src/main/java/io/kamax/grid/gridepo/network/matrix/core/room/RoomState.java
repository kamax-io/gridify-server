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

import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelState;
import io.kamax.grid.gridepo.core.store.ChannelStateDao;
import io.kamax.grid.gridepo.network.matrix.core.event.*;
import io.kamax.grid.gridepo.util.GsonUtil;

import java.util.*;
import java.util.function.Function;

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

    private Long sid;
    private Map<String, ChannelEvent> data = new HashMap<>();

    private RoomState() {
    }

    private RoomState(List<ChannelEvent> events) {
        this(null, events);
    }

    public RoomState(ChannelStateDao dao) {
        this(dao.getSid(), dao.getEvents());
    }

    public RoomState(ChannelState state) {
        this(state.getSid(), state.getEvents());
    }

    public RoomState(Long sid, List<ChannelEvent> events) {
        this.sid = sid;
        events.forEach(this::addEvent);
    }

    public RoomState(Long sid, RoomState state) {
        this.sid = sid;
        this.data = new HashMap<>(state.data);
    }

    private void addEvent(ChannelEvent ev) {
        String type = GsonUtil.getStringOrThrow(ev.getData(), EventKey.Type);
        String scope = GsonUtil.getStringOrThrow(ev.getData(), EventKey.Scope);
        String key = type + scope;
        data.put(key, ev);
    }

    public Long getSid() {
        return sid;
    }

    public Optional<ChannelEvent> find(String type, String scope) {
        return Optional.ofNullable(data.get(type + scope));
    }

    public Optional<ChannelEvent> find(RoomEventType type) {
        return find(type.getId(), "");
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
                .map(ev -> ev.getContent().getAction())
                .flatMap(membershipMapper());
    }

    public RoomMembership getMembership(String userId) {
        return findMembership(userId).orElse(RoomMembership.Leave);
    }

    public Optional<RoomJoinRule> getJoinRule() {
        return find(RoomEventType.JoinRules, BareJoinRulesEvent.class)
                .map(ev -> ev.getContent().getRule())
                .flatMap(joinRuleMapper());
    }

    public RoomState apply(ChannelEvent ev) {
        String scope = GsonUtil.getStringOrNull(ev.getData(), EventKey.Scope);
        if (Objects.isNull(scope)) {
            return this;
        }

        RoomState state = new RoomState();
        state.data = new HashMap<>(data);
        state.addEvent(ev);

        return state;
    }

}
