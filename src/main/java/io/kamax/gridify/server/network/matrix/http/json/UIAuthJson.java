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

package io.kamax.gridify.server.network.matrix.http.json;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.core.auth.UIAuthSession;
import io.kamax.gridify.server.core.auth.UIAuthStage;

import java.util.*;
import java.util.stream.Collectors;

public class UIAuthJson {

    public static UIAuthJson from(UIAuthSession session) {
        UIAuthJson json = new UIAuthJson();

        json.setSession(session.getId());
        session.getFlows().forEach(flowG -> {
            Set<String> stagesG = flowG.getStages().stream().map(UIAuthStage::getId).collect(Collectors.toSet());
            UIAuthJson.Flow flowM = json.addFlow();
            for (String stageG : stagesG) {
                flowM.addStage(stageG);
            }
        });

        return json;
    }

    public static class Flow {

        private List<String> stages = new ArrayList<>();

        public List<String> getStages() {
            return stages;
        }

        public void setStages(List<String> stages) {
            this.stages = stages;
        }

        public void addStage(String stageId) {
            getStages().add(stageId);
        }

    }

    private List<Flow> flows = new ArrayList<>();
    private Map<String, JsonObject> params = new HashMap<>();
    private String session;

    public List<Flow> getFlows() {
        return flows;
    }

    public void setFlows(List<Flow> flows) {
        this.flows = flows;
    }

    public Flow addFlow() {
        Flow f = new Flow();
        getFlows().add(f);
        return f;
    }

    public Map<String, JsonObject> getParams() {
        return params;
    }

    public void setParams(Map<String, JsonObject> params) {
        this.params = params;
    }

    public String getSession() {
        return session;
    }

    public void setSession(String session) {
        this.session = session;
    }

}
