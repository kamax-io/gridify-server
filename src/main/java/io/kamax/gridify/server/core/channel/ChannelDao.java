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

package io.kamax.gridify.server.core.channel;

import io.kamax.gridify.server.core.EntityID;

public class ChannelDao {

    private long sid;
    private String network;
    private String id;
    private String type;
    private String version;

    public ChannelDao(String net, String type, String id, String version) {
        this.network = net;
        this.type = type;
        this.id = id;
        this.version = version;
    }

    public ChannelDao(long sid, String network, String type, String id, String version) {
        this(network, type, id, version);
        setSid(sid);
    }

    public long getSid() {
        return sid;
    }

    public void setSid(long sid) {
        this.sid = sid;
    }

    public String getNetwork() {
        return network;
    }

    public void setNetwork(String network) {
        this.network = network;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setId(EntityID id) {
        setId(id.full());
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

}
