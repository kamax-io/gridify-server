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

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.core.federation.Address;
import io.kamax.grid.gridepo.network.matrix.core.RemoteServerException;
import io.kamax.grid.gridepo.util.GsonUtil;
import io.kamax.grid.gridepo.util.KxLog;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HomeServerHttpClient implements HomeServerClient {

    // FIXME pure hack, switch to config - maybe only for testing?
    public static boolean useHttps = true;

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    private CloseableHttpClient client;

    public HomeServerHttpClient() {
        try {
            // FIXME properly handle SSL context by validating certificate hostname
            SSLContext sslContext = SSLContextBuilder.create().loadTrustMaterial(TrustAllStrategy.INSTANCE).build();
            HostnameVerifier hostnameVerifier = NoopHostnameVerifier.INSTANCE;
            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
            this.client = HttpClientBuilder.create()
                    .disableAuthCaching()
                    .disableAutomaticRetries()
                    .disableCookieManagement()
                    .disableRedirectHandling()
                    .setSSLSocketFactory(sslSocketFactory)
                    .setDefaultRequestConfig(RequestConfig.custom()
                            .setConnectTimeout(30 * 1000) // FIXME make configurable
                            .setConnectionRequestTimeout(5 * 60 * 1000) // FIXME make configurable
                            .setSocketTimeout(5 * 60 * 1000) // FIXME make configurable
                            .build())
                    .setMaxConnPerRoute(Integer.MAX_VALUE) // FIXME make configurable
                    .setMaxConnTotal(Integer.MAX_VALUE) // FIXME make configurable
                    .setUserAgent("gridepo" + "/" + "0.0.0")
                    .build();
        } catch (KeyStoreException | NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    private HttpEntity getJsonEntity(Object o) {
        return EntityBuilder.create()
                .setText(GsonUtil.get().toJson(o))
                .setContentType(ContentType.APPLICATION_JSON)
                .build();
    }

    private <T> T parse(CloseableHttpResponse res, Class<T> c) throws IOException {
        return GsonUtil.parse(EntityUtils.toString(res.getEntity(), StandardCharsets.UTF_8), c);
    }

    private void applyAuthHeaders(HttpRequest httpRequest, HomeServerRequest mxRequest) {
        httpRequest.setHeader("Host", mxRequest.getDoc().getDestination());
        httpRequest.setHeader("Authorization",
                "X-Matrix origin=" + mxRequest.getDoc().getOrigin() + "," +
                        "key=\"" + mxRequest.getSign().getKeyId() + "\"," +
                        "sig=\"" + mxRequest.getSign().getValue() + "\""
        );
    }

    private URI lookupSrv(String domain) {
        List<Address> addrs = new ArrayList<>();

        // Check if we have a port in it
        int i = domain.lastIndexOf(":");
        // Check if this is an IPv6 address
        int j = domain.lastIndexOf("]");

        // We want to be sure there is a port separator AFTER the end of an IPv6 ending with ]
        if (i > -1 && j <= i) {
            // This is a domain with a port already declared in it
            addrs.add(new Address(domain.substring(0, i), Integer.parseInt(domain.substring(i + 1))));
        } else {
            // This is a domain without port
            addrs.add(new Address(domain, useHttps ? 443 : 80));
        }

        String protocol = useHttps ? "https://" : "http://";
        String uriPrefix = addrs.stream().flatMap(addr -> {
            try {
                URI uri = new URI(protocol + addr.getHost() + ":" + addr.getPort() + "/.well-known/matrix/server");
                HttpGet req = new HttpGet(uri);
                try (CloseableHttpResponse res = client.execute(req)) {
                    int sc = res.getStatusLine().getStatusCode();
                    if (sc == 404) {
                        return Stream.of(new URIBuilder(uri).setPath("").build().toURL());
                    } else if (sc == 200) {
                        try {
                            JsonObject obj = GsonUtil.parseObj(EntityUtils.toString(res.getEntity(), StandardCharsets.UTF_8));
                            String urlRaw = GsonUtil.findString(obj, "m.server")
                                    .orElseGet(() -> addr.getHost() + ":" + addr.getPort());
                            return Stream.of(new URL(protocol + urlRaw));
                        } catch (IllegalArgumentException e) {
                            log.warn("Malformed well-known object, ignoring");
                            return Stream.empty();
                        }
                    } else {
                        log.warn("Status code {} from well-known discovery", sc);
                        return Stream.empty();
                    }
                } catch (IOException e) {
                    log.warn("Unable to connect/read to/from {}, ignoring from auto-discovery", uri, e);
                    return Stream.of(new URIBuilder(uri).setPath("").build().toURL());
                }
            } catch (URISyntaxException | MalformedURLException e) {
                return Stream.empty();
            }
        }).collect(Collectors.toList()).get(0).toString();
        return URI.create(uriPrefix);
    }

    /*
    public boolean ping(String origin, String target) {
        HttpGet req = new HttpGet();
        req.setHeader("X-Grid-Remote-ID", origin);

        for (URL url : lookupSrv(target)) {
            String srvUriRaw = url + URIPath.dataSrv().add("version").get();
            try {
                URI srvUri = new URI(srvUriRaw);
                req.setURI(srvUri);
                try (CloseableHttpResponse res = client.execute(req)) {
                    return res.getStatusLine().getStatusCode() == 200;
                } catch (IOException e) {
                    log.warn("", e);
                }
            } catch (URISyntaxException e) {
                log.warn("Unable to create URI for server: Invalid URI for {}: {}", srvUriRaw, e.getMessage());
            }
        }

        throw new RemoteServerException(target, "G_FEDERATION_ERROR", "Could not find a working server for " + target);
    }

    public JsonObject push(String as, String target, List<ChannelEvent> chEvents) {
        JsonArray events = new JsonArray();
        chEvents.forEach(chEv -> events.add(chEv.getData()));

        HttpPost req = new HttpPost();
        req.setHeader("X-Grid-Remote-ID", as);
        req.setEntity(getJsonEntity(GsonUtil.makeObj("events", events)));

        for (URL url : lookupSrv(target)) {
            String srvUriRaw = url + URIPath.dataSrv().add("v0", "do", "push").get();
            try {
                URI srvUri = new URI(srvUriRaw);
                try {
                    req.setURI(srvUri);
                    try (CloseableHttpResponse res = client.execute(req)) {
                        int sc = res.getStatusLine().getStatusCode();
                        if (sc != 200) {
                            JsonObject b;
                            try {
                                b = GsonUtil.parseObj(EntityUtils.toString(res.getEntity(), StandardCharsets.UTF_8));
                            } catch (IllegalArgumentException e) {
                                b = new JsonObject();
                            }

                            if (sc == 403) {
                                throw new ForbiddenException(GsonUtil.findString(b, "error").orElse("Server did not give a reason"));
                            }

                            throw new RemoteServerException(target, b);
                        }

                        try {
                            // FIXME check if the server signed the event before returning it
                            return GsonUtil.parseObj(EntityUtils.toByteArray(res.getEntity()));
                        } catch (IllegalArgumentException e) {
                            throw new RemoteServerException(target, "G_REMOTE_ERROR", "Server did not send us back JSON");
                        }
                    }
                } catch (IOException e) {
                    log.warn("", e);
                }
            } catch (URISyntaxException e) {
                log.warn("Unable to create URI for server: Invalid URI for {}: {}", srvUriRaw, e.getMessage());
            }
        }

        throw new RemoteServerException(target, "G_FEDERATION_ERROR", "Could not find a working server for " + target);
    }

    public JsonObject approveInvite(String as, String target, InviteApprovalRequest data) {
        HttpPost req = new HttpPost();
        req.setHeader("X-Grid-Remote-ID", as);
        req.setEntity(getJsonEntity(data));

        for (URL url : lookupSrv(target)) {
            String srvUriRaw = url + URIPath.dataSrv().add("v0", "do", "approve", "invite").get();
            try {
                URI srvUri = new URI(srvUriRaw);
                try {
                    req.setURI(srvUri);
                    try (CloseableHttpResponse res = client.execute(req)) {
                        int sc = res.getStatusLine().getStatusCode();
                        if (sc != 200) {
                            JsonObject b;
                            try {
                                b = GsonUtil.parseObj(EntityUtils.toString(res.getEntity(), StandardCharsets.UTF_8));
                            } catch (IllegalArgumentException e) {
                                b = new JsonObject();
                            }

                            if (sc == 403) {
                                log.warn("Remote server refused to sign our invite");
                                throw new ForbiddenException(GsonUtil.findString(b, "error").orElse("Server did not give a reason"));
                            }

                            throw new RemoteServerException(target, b);
                        }

                        try {
                            // FIXME check if the server signed the event before returning it
                            return GsonUtil.parseObj(EntityUtils.toByteArray(res.getEntity()));
                        } catch (IllegalArgumentException e) {
                            throw new RemoteServerException(target, "G_REMOTE_ERROR", "Server did not send us back JSON");
                        }
                    }
                } catch (IOException e) {
                    log.warn("", e);
                }
            } catch (URISyntaxException e) {
                log.warn("Unable to create URI for server: Invalid URI for {}: {}", srvUriRaw, e.getMessage());
            }
        }

        throw new RemoteServerException(target, "G_FEDERATION_ERROR", "Could not find a working server for " + target);
    }

    public JsonObject approveJoin(String as, String target, BareMemberEvent ev) {
        HttpPost req = new HttpPost();
        req.setHeader("X-Grid-Remote-ID", as);
        req.setEntity(getJsonEntity(ev.getJson()));

        for (URL url : lookupSrv(target)) {
            String srvUriRaw = url + URIPath.dataSrv().add("v0", "do", "approve", "join").get();
            try {
                URI srvUri = new URI(srvUriRaw);
                try {
                    req.setURI(srvUri);
                    try (CloseableHttpResponse res = client.execute(req)) {
                        int sc = res.getStatusLine().getStatusCode();
                        if (sc != 200) {
                            JsonObject b;
                            try {
                                b = GsonUtil.parseObj(EntityUtils.toString(res.getEntity(), StandardCharsets.UTF_8));
                            } catch (IllegalArgumentException e) {
                                b = new JsonObject();
                            }

                            if (sc == 403) {
                                log.warn("Remote server refused to sign our join");
                                throw new ForbiddenException(GsonUtil.findString(b, "error").orElse("Server did not give a reason"));
                            }

                            throw new RemoteServerException(target, b);
                        }

                        try {
                            // FIXME check if the server signed the event before returning it
                            return GsonUtil.parseObj(EntityUtils.toByteArray(res.getEntity()));
                        } catch (IllegalArgumentException e) {
                            throw new RemoteServerException(target, "G_REMOTE_ERROR", "Server did not send us back JSON");
                        }
                    }
                } catch (IOException e) {
                    log.warn("", e);
                }
            } catch (URISyntaxException e) {
                log.warn("Unable to create URI for server: Invalid URI for {}: {}", srvUriRaw, e.getMessage());
            }
        }

        throw new RemoteServerException(target, "G_FEDERATION_ERROR", "Could not find a working server for " + target);
    }

    public Optional<ChannelLookup> lookup(String as, String target, ChannelAlias alias) {
        HttpPost req = new HttpPost();
        req.setHeader("X-Grid-Remote-ID", as);
        req.setEntity(getJsonEntity(GsonUtil.makeObj("alias", alias.full())));

        for (URL url : lookupSrv(target)) {
            String srvUriRaw = url + URIPath.dataSrv().add("v0", "do", "lookup", "channel", "alias").get();
            try {
                URI srvUri = new URI(srvUriRaw);
                try {
                    req.setURI(srvUri);
                    try (CloseableHttpResponse res = client.execute(req)) {
                        int sc = res.getStatusLine().getStatusCode();
                        if (sc != 200) {
                            JsonObject b;
                            try {
                                b = GsonUtil.parseObj(EntityUtils.toString(res.getEntity(), StandardCharsets.UTF_8));
                            } catch (IllegalArgumentException e) {
                                b = new JsonObject();
                            }

                            if (sc == 404 && StringUtils.equals("G_NOT_FOUND", GsonUtil.getStringOrNull(b, "errcode"))) {
                                return Optional.empty();
                            }

                            if (sc == 403) {
                                throw new ForbiddenException(GsonUtil.findString(b, "error").orElse("Server did not give a reason"));
                            }

                            throw new RemoteServerException(target, b);
                        }

                        try {
                            ChannelLookupResponse data = parse(res, ChannelLookupResponse.class);
                            if (Objects.isNull(data.getId())) {
                                log.warn("Server located at {} does not follow the specification, ignoring response", target);
                                return Optional.empty();
                            }

                            if (Objects.isNull(data.getServers())) {
                                log.warn("Server located at {} does not follow the specification, ignoring response", target);
                                return Optional.empty();
                            }

                            ChannelID cId = ChannelID.parse(data.getId());
                            Set<ServerID> srvIds = data.getServers().stream().map(ServerID::parse).collect(Collectors.toSet());
                            return Optional.of(new ChannelLookup(alias, cId, srvIds));
                        } catch (IllegalArgumentException e) {
                            throw new RemoteServerException(target, "G_REMOTE_ERROR", "Server did not send us back JSON");
                        }
                    }
                } catch (IOException e) {
                    log.warn("", e);
                }
            } catch (URISyntaxException e) {
                log.warn("Unable to create URI for server: Invalid URI for {}: {}", srvUriRaw, e.getMessage());
            }
        }

        throw new RemoteServerException(target, "G_FEDERATION_ERROR", "Could not find a working server for " + target);
    }

    public Optional<JsonObject> getEvent(String as, String target, ChannelID cId, EventID eId) {
        HttpGet req = new HttpGet();
        req.setHeader("X-Grid-Remote-ID", as);

        for (URL url : lookupSrv(target)) {
            String srvUriRaw = url + URIPath.dataSrv().add("v0", "channels", cId.full(), "events", eId.full()).get();
            URI srvUri;
            try {
                srvUri = new URI(srvUriRaw);
            } catch (URISyntaxException e) {
                log.warn("Unable to create URI for server: Invalid URI for {}: {}", srvUriRaw, e.getMessage());
                continue;
            }

            req.setURI(srvUri);
            try (CloseableHttpResponse res = client.execute(req)) {
                int sc = res.getStatusLine().getStatusCode();
                if (sc != 200) {
                    JsonObject b;
                    try {
                        b = GsonUtil.parseObj(EntityUtils.toString(res.getEntity(), StandardCharsets.UTF_8));
                    } catch (IllegalArgumentException e) {
                        b = new JsonObject();
                    }

                    if (sc == 404 && StringUtils.equals("G_NOT_FOUND", GsonUtil.getStringOrNull(b, "errcode"))) {
                        return Optional.empty();
                    }

                    if (sc == 403) {
                        throw new ForbiddenException(GsonUtil.findString(b, "error").orElse("Server did not give a reason"));
                    }

                    throw new RemoteServerException(target, b);
                }

                try {
                    JsonObject body = parse(res, JsonObject.class);
                    return GsonUtil.findObj(body, "event");
                } catch (IllegalArgumentException e) {
                    throw new RemoteServerException(target, "G_REMOTE_ERROR", "Server did not send us back JSON");
                }
            } catch (IOException e) {
                log.warn("", e);
            }
        }

        return Optional.empty();
    }

    public Optional<UserID> lookupUser(String as, String target, ThreePid tpid) {
        JsonObject identifier = new JsonObject();
        identifier.addProperty("type", tpid.getMedium());
        identifier.addProperty("value", tpid.getAddress());

        HttpPost req = new HttpPost();
        req.setHeader("X-Grid-Remote-ID", as);
        req.setEntity(getJsonEntity(GsonUtil.makeObj("identifier", identifier)));

        for (URL url : lookupSrv(target)) {
            String srvUriRaw = url + URIPath.idSrv().add("v0", "do", "lookup", "user", "threepid").get();
            URI srvUri;
            try {
                srvUri = new URI(srvUriRaw);
            } catch (URISyntaxException e) {
                log.warn("Unable to create URI for server: Invalid URI for {}: {}", srvUriRaw, e.getMessage());
                continue;
            }

            req.setURI(srvUri);
            try (CloseableHttpResponse res = client.execute(req)) {
                int sc = res.getStatusLine().getStatusCode();
                if (sc != 200) {
                    JsonObject b;
                    try {
                        b = GsonUtil.parseObj(EntityUtils.toString(res.getEntity(), StandardCharsets.UTF_8));
                    } catch (IllegalArgumentException e) {
                        b = new JsonObject();
                    }

                    if (sc == 404 && StringUtils.equals("G_NOT_FOUND", GsonUtil.getStringOrNull(b, "errcode"))) {
                        return Optional.empty();
                    }

                    if (sc == 403) {
                        throw new ForbiddenException(GsonUtil.findString(b, "error").orElse("Server did not give a reason"));
                    }

                    throw new RemoteServerException(target, b);
                }

                try {
                    JsonObject body = parse(res, JsonObject.class);
                    return GsonUtil.findString(body, "id").map(UserID::parse);
                } catch (IllegalArgumentException e) {
                    throw new RemoteServerException(target, "G_REMOTE_ERROR", "Server did not send us back JSON");
                }
            } catch (IOException e) {
                log.warn("", e);
            }
        }

        return Optional.empty();
    }

    */

    private HomeServerResponse sendGet(URI target, HomeServerRequest request) {
        HttpGet req = new HttpGet(target);
        applyAuthHeaders(req, request);

        log.info("Calling [{}] {}", request.getDoc().getDestination(), req);
        try (CloseableHttpResponse res = client.execute(req)) {
            int resStatus = res.getStatusLine().getStatusCode();
            JsonObject body = parse(res, JsonObject.class);
            if (resStatus == 200) {
                log.debug("Got answer");
                return HomeServerResponse.make(body);
            } else {
                log.debug("Unexpected response - SC: {} - Body: {}", resStatus, body);
                throw new RemoteServerException(request.getDoc().getDestination(), body);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public HomeServerResponse doRequest(HomeServerRequest request) {
        String remoteDomain = request.getDoc().getDestination();
        URI target = lookupSrv(remoteDomain)
                .resolve(request.getDoc().getUri());
        if ("GET".equals(request.getDoc().getMethod())) {
            return sendGet(target, request);
        } else {
            throw new IllegalArgumentException("Method " + request.getDoc().getMethod() + " is not supported");
        }
    }

}
