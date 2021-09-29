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

package io.kamax.gridify.server.network.matrix.core.event;

import com.google.gson.annotations.SerializedName;

public class BareMessageEvent extends BareEvent<BareMessageEvent.Content> {

    public static BareMessageEvent makeText(String body) {
        BareMessageEvent ev = new BareMessageEvent();
        ev.getContent().setType("m.text");
        ev.getContent().setBody(body);
        return ev;
    }

    public static BareMessageEvent makeHtml(String rawBody, String htmlBody) {
        BareMessageEvent ev = makeText(rawBody);
        ev.getContent().setFormat("org.matrix.custom.html");
        ev.getContent().setFormattedBody(htmlBody);
        return ev;
    }

    public static class Content {

        @SerializedName("msgtype")
        private String type;
        private String body;
        private String format;
        private String formattedBody;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public String getFormattedBody() {
            return formattedBody;
        }

        public void setFormattedBody(String formattedBody) {
            this.formattedBody = formattedBody;
        }

    }

    public BareMessageEvent() {
        setType(RoomEventType.Message);
        setContent(new Content());
    }

}
