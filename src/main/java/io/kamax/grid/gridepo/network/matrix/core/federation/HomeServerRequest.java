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

public class HomeServerRequest {

    public static class Doc {

        private String origin;
        private String destination;
        private String method;
        private String uri;
        private transient String uriEncoded;
        private JsonElement content;

        public String getOrigin() {
            return origin;
        }

        public void setOrigin(String origin) {
            this.origin = origin;
        }

        public String getDestination() {
            return destination;
        }

        public void setDestination(String destination) {
            this.destination = destination;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getUriEncoded() {
            return uriEncoded;
        }

        public void setUriEncoded(String uriEncoded) {
            this.uriEncoded = uriEncoded;
        }

        public JsonElement getContent() {
            return content;
        }

        public void setContent(JsonElement content) {
            this.content = content;
        }

    }

    public static class Signature {

        private String keyId;
        private String value;

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

    }

    private Doc doc = new Doc();
    private Signature sign = new Signature();

    public Doc getDoc() {
        return doc;
    }

    public void setDoc(Doc doc) {
        this.doc = doc;
    }

    public Signature getSign() {
        return sign;
    }

    public void setSign(Signature sign) {
        this.sign = sign;
    }

}
