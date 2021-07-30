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

package io.kamax.grid.gridepo.network.matrix.core.federation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.codec.GridJson;
import io.kamax.grid.gridepo.core.crypto.Key;
import io.kamax.grid.gridepo.core.crypto.Signature;
import io.kamax.grid.gridepo.exception.NotImplementedException;
import io.kamax.grid.gridepo.network.matrix.core.MatrixServer;
import io.kamax.grid.gridepo.network.matrix.core.room.RoomLookup;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.apache.http.client.utils.URIBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.Optional;

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

    private URI build(URIPath path, String[]... params) {
        URIBuilder builder = new URIBuilder();
        builder.setPath(path.get());
        for (String[] param : params) {
            builder.addParameter(param[0], param[1]);
        }
        try {
            return builder.build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public HomeServerRequest sign(HomeServerRequest request) {
        Key signingKey = g.asServer().getCrypto().getServerSigningKey();
        String toSign = GridJson.encodeCanonical(GsonUtil.makeObj(request.getDoc()));
        Signature signDoc = g.asServer().getCrypto().sign(toSign, signingKey.getId());
        request.getSign().setKeyId(signDoc.getKey().getId());
        request.getSign().setValue(signDoc.getSignature());
        return request;
    }

    private HomeServerRequest build(String destination, String method, URI uri, JsonElement content) {
        HomeServerRequest request = new HomeServerRequest();
        request.getDoc().setOrigin(origin);
        request.getDoc().setDestination(destination);
        request.getDoc().setMethod(method);
        request.getDoc().setUri(uri.toString());
        if (!Objects.isNull(content)) {
            request.getDoc().setContent(content);
        }
        return sign(request);
    }

    private HomeServerRequest build(String destination, String method, URI uri) {
        return build(destination, method, uri, null);
    }

    public Optional<RoomLookup> lookup(String source, String roomAlias) {
        URI uri = build(URIPath.federation().v1().add("query").add("directory"),
                new String[]{"room_alias", roomAlias});
        HomeServerRequest request = build(
                domain,
                "GET",
                uri
        );

        HomeServerResponse res = client.doRequest(request);
        return Optional.empty();
    }

    public RoomJoinTemplate getJoinTemplate(String roomId, String userId) {
        throw new NotImplementedException();
    }

    public JsonObject sendJoin(JsonObject event) {
        throw new NotImplementedException();
    }

}
