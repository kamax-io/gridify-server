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

package io.kamax.gridify.server.network.matrix.core.federation;

import io.kamax.gridify.server.core.signal.ChannelMessageProcessed;
import io.kamax.gridify.server.core.signal.SignalTopic;
import io.kamax.gridify.server.network.matrix.core.MatrixCore;
import io.kamax.gridify.server.network.matrix.core.event.BareMemberEvent;
import io.kamax.gridify.server.network.matrix.core.event.RoomEventType;
import io.kamax.gridify.server.network.matrix.core.room.RoomMembership;
import io.kamax.gridify.server.network.matrix.core.room.RoomState;
import net.engio.mbassy.listener.Handler;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.stream.Collectors;

public class FederationPusher {

    private final MatrixCore core;
    private boolean enabled;
    private boolean async;

    public FederationPusher(MatrixCore core) {
        this.core = core;
        setEnabled(true);
        setAsync(true);
    }

    @Handler
    private void handle(ChannelMessageProcessed signal) {
        if (!isEnabled()) {
            return;
        }

        if (!core.isLocal(signal.getEvent().asMatrix().getOrigin())) {
            // TODO send over cluster if needed
            return;
        }

        ForkJoinTask<Void> task = ForkJoinPool.commonPool().submit(new RecursiveAction() {
            @Override
            protected void compute() {
                RoomState state = core.roomMgr().get(signal.getEvent().asMatrix().getRoomId()).getState(signal.getEvent());
                Set<String> remotes = state.getEvents().stream()
                        .filter(ev -> RoomEventType.Member.match(ev.asMatrix().getType()))
                        .filter(ev -> RoomMembership.Join.match(BareMemberEvent.computeMembership(ev.getData())))
                        .map(ev -> ev.asMatrix().getOrigin())
                        .filter(origin -> !core.isLocal(origin))
                        .collect(Collectors.toSet());

                invokeAll(remotes.stream().map(remote -> new RecursiveAction() {
                    @Override
                    protected void compute() {
                        String vHost = signal.getEvent().asMatrix().getOrigin();
                        String scope = signal.getEvent().getChannelId();
                        HomeServerLink link = core.hsMgr().getLink(vHost, remote);
                        link.push(signal.getEvent().getData());
                        core.store().setStreamIdForDestination("mx-fed", remote, scope, signal.getEvent().getSid());
                    }
                }).collect(Collectors.toList()));
            }
        });

        if (!isAsync()) {
            try {
                task.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            core.bus().forTopic(SignalTopic.Room).subscribe(this);
        } else {
            core.bus().forTopic(SignalTopic.Room).unsubscribe(this);
        }
    }

    public boolean isAsync() {
        return async;
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

}
