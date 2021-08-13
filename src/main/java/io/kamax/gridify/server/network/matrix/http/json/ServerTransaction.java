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

package io.kamax.gridify.server.network.matrix.http.json;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ServerTransaction {

    @SerializedName("origin")
    private String origin;
    @SerializedName("origin_server_ts")
    private long timestamp;
    @SerializedName("pdus")
    private List<JsonObject> pdus;
    @SerializedName("edus")
    private List<JsonObject> edus;

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long originalServerTs) {
        this.timestamp = originalServerTs;
    }

    public List<JsonObject> getPdus() {
        return pdus;
    }

    public void setPdus(List<JsonObject> pdus) {
        this.pdus = pdus;
    }

    public List<JsonObject> getEdus() {
        return edus;
    }

    public void setEdus(List<JsonObject> edus) {
        this.edus = edus;
    }

}
