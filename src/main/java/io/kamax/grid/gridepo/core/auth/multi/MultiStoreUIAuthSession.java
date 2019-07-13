/*
 * Gridepo - Grid Data Server
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

package io.kamax.grid.gridepo.core.auth.multi;

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.config.UIAuthConfig;
import io.kamax.grid.gridepo.core.auth.*;
import io.kamax.grid.gridepo.core.identity.AuthIdentityStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class MultiStoreUIAuthSession implements UIAuthSession {

    private static Logger log = LoggerFactory.getLogger(MultiStoreAuthService.class);

    private String id;
    private Instant createTs;
    private List<UIAuthFlow> flows = new ArrayList<>();
    private Map<String, JsonObject> parameters = new HashMap<>();
    private Map<String, UIAuthStage> stages = new HashMap<>();
    private Map<String, List<AuthIdentityStore>> stageHandlers = new HashMap<>();

    public MultiStoreUIAuthSession(String id, Set<AuthIdentityStore> stores, UIAuthConfig cfg) {
        this.id = id;
        this.createTs = Instant.now();

        stores.forEach(store -> {
            store.getSupportedTypes().forEach(type -> {
                stageHandlers.computeIfAbsent(type, t -> new ArrayList<>()).add(store);
            });
        });

        parameters.putAll(cfg.getParameters());

        cfg.getFlows().forEach(flowCfg -> {
            List<UIAuthStage> flowStages = new ArrayList<>();
            flowCfg.getStages().forEach(stageCfg -> {
                flowStages.add(stages.computeIfAbsent(stageCfg, BasicUIAuthStage::new));
            });
            UIAuthFlow flow = new BasicUIAuthFlow(flowStages);
            flows.add(flow);
        });
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Instant createdAt() {
        return createTs;
    }

    @Override
    public Set<String> getCompletedStages() {
        return stages.values().stream()
                .filter(UIAuthStage::isCompleted)
                .map(UIAuthStage::getId)
                .collect(Collectors.toSet());
    }

    @Override
    public List<UIAuthFlow> getFlows() {
        return flows;
    }

    @Override
    public Optional<JsonObject> findParameters(String stage) {
        return Optional.ofNullable(parameters.get(stage));
    }

    @Override
    public boolean complete(String stageId, JsonObject data) {
        List<AuthIdentityStore> handlers = stageHandlers.get(stageId);
        UIAuthStage stage = stages.get(stageId);
        if (Objects.isNull(handlers)) {
            throw new IllegalArgumentException("Stage " + stageId + " is not available");
        }

        for (AuthIdentityStore handler : handlers) {
            AuthResult result = handler.authenticate(stageId, data);
            if (result.isSuccess()) {
                stage.completeWith(result);
            }
        }

        return isAuthenticated();
    }

    @Override
    public UIAuthStage getStage(String id) {
        return stages.get(id);
    }

    @Override
    public boolean isAuthenticated() {
        for (UIAuthFlow flow : flows) {
            if (flow.getStages().stream().allMatch(UIAuthStage::isCompleted)) {
                return true;
            }
        }

        return false;
    }

}
