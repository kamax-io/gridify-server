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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.kamax.gridify.server.core.crypto.Signature;
import io.kamax.gridify.server.exception.ForbiddenException;
import io.kamax.gridify.server.network.matrix.core.MatrixServer;
import io.kamax.gridify.server.network.matrix.core.RemoteServerException;
import io.kamax.gridify.server.network.matrix.core.crypto.CryptoJson;
import io.kamax.gridify.server.network.matrix.core.room.RoomInviteRequest;
import io.kamax.gridify.server.network.matrix.core.room.RoomJoinSeed;
import io.kamax.gridify.server.network.matrix.core.room.RoomLookup;
import io.kamax.gridify.server.util.GsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.*;

public class HomeServerLink {

    private final String origin;
    private final String domain;

    private final MatrixServer g;
    private final HomeServerClient client;

    public HomeServerLink(MatrixServer g, String domain, HomeServerClient client) {
        this.origin = g.getDomain();
        this.domain = domain;

        this.g = g;
        this.client = client;
    }

    public String getDomain() {
        return domain;
    }

    private URI build(URIPath path, List<String[]> params) {
        URIBuilder builder = new URIBuilder(URI.create(path.get()));
        for (String[] param : params) {
            builder.addParameter(param[0], param[1]);
        }
        try {
            return builder.build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private URI build(URIPath path, String[]... params) {
        return build(path, Arrays.asList(params));
    }

    public HomeServerRequest sign(HomeServerRequest request) {
        JsonObject requestDoc = GsonUtil.makeObj(request.getDoc());
        Signature signDoc = CryptoJson.computeSignature(requestDoc, g.asServer().getCrypto());
        request.getSign().setKeyId(signDoc.getKey().getId());
        request.getSign().setValue(signDoc.getSignature());
        return request;
    }

    private HomeServerRequest build(String destination, String method, URIBuilder uri, JsonElement content) {
        try {
            URI path = uri.build();
            String decodedUri = path.getRawPath();
            if (StringUtils.isNotBlank(path.getRawQuery())) {
                decodedUri += "?" + path.getRawQuery();
            }
            HomeServerRequest request = new HomeServerRequest();
            request.getDoc().setOrigin(origin);
            request.getDoc().setDestination(destination);
            request.getDoc().setMethod(method);
            request.getDoc().setUri(decodedUri);
            if (!Objects.isNull(content)) {
                request.getDoc().setContent(content);
            }
            return sign(request);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private HomeServerRequest build(String destination, String method, URIBuilder uri) {
        return build(destination, method, uri, null);
    }

    public Optional<RoomLookup> lookup(String roomAlias) {
        URI uri = build(URIPath.federation().v1().add("query", "directory"),
                new String[]{"room_alias", roomAlias});
        HomeServerRequest request = build(
                domain,
                "GET",
                new URIBuilder(uri)
        );

        HomeServerResponse res = client.doRequest(request);

        if (res.getCode() == 200) {
            String roomId = GsonUtil.getStringOrNull(res.getBody(), "room_id");
            List<String> servers = GsonUtil.tryArrayAsList(res.getBody(), "servers", String.class);
            return Optional.of(new RoomLookup(roomAlias, roomId, new HashSet<>(servers)));
        }

        if (res.getCode() == 404) {
            return Optional.empty();
        }

        String errCode = GsonUtil.getStringOrNull(res.getBody(), "errcode");
        String error = GsonUtil.getStringOrNull(res.getBody(), "error");
        throw new RemoteServerException(domain, errCode, error);
    }

    public RoomJoinTemplate getJoinTemplate(String roomId, String userId) {
        URI path = URI.create("/_matrix/federation/v1/make_join/" + roomId + "/" + userId);
        URIBuilder builder = new URIBuilder(path);

        Set<String> versions = g.getRoomVersions();
        for (String version : versions) {
            builder.addParameter("ver", version);
        }
        builder.addParameter("ver", "1");

        HomeServerRequest request = build(domain, "GET", builder);

        HomeServerResponse res = client.doRequest(request);
        if (res.getCode() == 200) {
            return GsonUtil.fromJson(res.getBody(), RoomJoinTemplate.class);
        }

        String errCode = GsonUtil.getStringOrNull(res.getBody(), "errcode");
        String error = GsonUtil.getStringOrNull(res.getBody(), "error");
        if (res.getCode() == 403) {
            throw new ForbiddenException(errCode + " - " + error);
        } else {
            throw new RemoteServerException(domain, errCode, error);
        }
    }

    public RoomJoinSeed sendJoin(String roomId, String userId, JsonObject event) {
        URI path = URI.create("/_matrix/federation/v2/send_join/" + roomId + "/" + userId);
        URIBuilder builder = new URIBuilder(path);
        HomeServerRequest request = build(domain, "PUT", builder, event);

        HomeServerResponse response = client.doRequest(request);
        if (response.getCode() != 200) {
            throw new RemoteServerException(domain, response.getBody());
        }

        return GsonUtil.fromJson(response.getBody(), RoomJoinSeed.class);
    }

    public List<JsonObject> getAuthChain(String roomId, String eventId) {
        URI path = URI.create("/_matrix/federation/v1/event_auth/" + roomId + "/" + eventId);
        HomeServerRequest request = build(domain, "GET", new URIBuilder(path));
        HomeServerResponse response = client.doRequest(request);
        if (response.getCode() != 200) {
            throw new RemoteServerException(domain, response.getBody());
        }

        return GsonUtil.asList(response.getBody(), "auth_chain", JsonObject.class);
    }

    public List<JsonObject> getPreviousEvents(String roomId, Collection<String> latestEvents, Collection<String> earliestEvents, long minDeph, long amount) {
        URI path = URI.create("/_matrix/federation/v1/get_missing_events/" + roomId);
        JsonObject body = new JsonObject();
        body.add("latest_events", GsonUtil.asArray(latestEvents));
        body.add("earliest_events", GsonUtil.asArray(earliestEvents));
        body.addProperty("min_depth", minDeph);
        body.addProperty("limit", amount);
        HomeServerRequest request = build(domain, "POST", new URIBuilder(path), body);
        HomeServerResponse response = client.doRequest(request);
        if (response.getCode() != 200) {
            throw new RemoteServerException(domain, response.getBody());
        }

        return GsonUtil.asList(response.getBody(), "events", JsonObject.class);
    }

    public List<JsonObject> getPreviousEvents(String roomId, Collection<String> latestEvents, Collection<String> earliestEvents, long minDeph) {
        return getPreviousEvents(roomId, latestEvents, earliestEvents, minDeph, 10);
    }

    public void push(JsonObject ev) {
        long timestamp = Instant.now().toEpochMilli();
        URI path = URI.create("/_matrix/federation/v1/send/" + timestamp);
        JsonObject body = new JsonObject();
        body.addProperty("origin", domain);
        body.addProperty("origin_server_ts", timestamp);
        body.add("pdus", GsonUtil.asArray(ev));
        HomeServerRequest req = build(domain, "PUT", new URIBuilder(path), body);
        HomeServerResponse response = client.doRequest(req);
        if (response.getCode() != 200) {
            throw new RemoteServerException(domain, response.getBody());
        }
    }

    public JsonObject inviteUser(RoomInviteRequest request) {
        URI path = URI.create("/_matrix/federation/v2/invite/" + request.getRoomId() + "/" + request.getEventId());
        JsonObject body = new JsonObject();
        body.add("event", request.getDoc());
        body.add("invite_room_state", GsonUtil.asArray(request.getStrippedState()));
        body.addProperty("room_version", request.getRoomVersion());
        HomeServerRequest req = build(domain, "PUT", new URIBuilder(path), body);
        HomeServerResponse response = client.doRequest(req);
        JsonObject resBody = response.getBody();
        if (response.getCode() == 403) {
            throw new ForbiddenException(
                    GsonUtil.findString(resBody, "error")
                            .orElseGet(() -> GsonUtil.getPrettyForLog(resBody))
            );
        }
        if (response.getCode() != 200) {
            throw new RemoteServerException(domain, response.getBody());
        }
        return GsonUtil.getObj(resBody, "event");
    }

}
