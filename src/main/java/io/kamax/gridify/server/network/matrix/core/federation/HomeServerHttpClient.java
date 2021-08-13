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

import com.google.gson.JsonObject;
import io.kamax.gridify.server.core.federation.Address;
import io.kamax.gridify.server.network.matrix.core.RemoteServerException;
import io.kamax.gridify.server.util.GsonUtil;
import io.kamax.gridify.server.util.KxLog;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
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

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    // FIXME pure hack, switch to config - maybe only for testing?
    public static boolean useHttps = true;

    private final CloseableHttpClient client;

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
                    .setUserAgent("gridify" + "/" + "0.0.0") // FIXME use build properties
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
        if (i > -1 && i > j) {
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

    private HomeServerResponse sendGet(URI target, HomeServerRequest request) {
        HttpGet req = new HttpGet(target);
        applyAuthHeaders(req, request);

        log.info("Calling [{}] {}", request.getDoc().getDestination(), req);
        try (CloseableHttpResponse res = client.execute(req)) {
            int resStatus = res.getStatusLine().getStatusCode();
            JsonObject body = parse(res, JsonObject.class);
            if (resStatus == 200) {
                log.debug("Got answer");
                return HomeServerResponse.make(resStatus, body);
            } else {
                log.debug("Unexpected response - SC: {} - Body: {}", resStatus, body);
                throw new RemoteServerException(request.getDoc().getDestination(), body);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private HomeServerResponse sendPut(URI target, HomeServerRequest request) {
        HttpPut req = new HttpPut(target);
        applyAuthHeaders(req, request);
        req.setEntity(getJsonEntity(request.getDoc().getContent()));

        log.info("Calling [{}] {}", request.getDoc().getDestination(), req);
        try (CloseableHttpResponse res = client.execute(req)) {
            int resStatus = res.getStatusLine().getStatusCode();
            JsonObject body = parse(res, JsonObject.class);
            if (resStatus == 200) {
                log.debug("Got answer");
                return HomeServerResponse.make(resStatus, body);
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
        }

        if ("PUT".equals(request.getDoc().getMethod())) {
            return sendPut(target, request);
        }

        throw new IllegalArgumentException("Method " + request.getDoc().getMethod() + " is not supported");
    }

}
