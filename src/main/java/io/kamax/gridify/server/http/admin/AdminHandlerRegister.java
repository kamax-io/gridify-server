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

package io.kamax.gridify.server.http.admin;

import com.google.gson.JsonObject;
import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.exception.UnauthenticatedException;
import io.kamax.gridify.server.http.Exchange;
import io.kamax.gridify.server.http.RootLogoHandler;
import io.kamax.gridify.server.http.admin.handler.AdminHandler;
import io.kamax.gridify.server.http.admin.handler.HomepageHandler;
import io.kamax.gridify.server.http.admin.handler.InstallSetupApiHandler;
import io.kamax.gridify.server.http.admin.handler.InstallSetupWizardHandler;
import io.kamax.gridify.server.network.matrix.http.json.UIAuthJson;
import io.kamax.gridify.server.util.GsonUtil;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.RedirectHandler;

public class AdminHandlerRegister {

    public static void register(GridifyServer g, RoutingHandler r) {
        r.add("OPTIONS", "/_gridify/admin/**", new AdminHandler(g) {
            @Override
            protected void handle(Exchange ex) {
                // no-op
            }
        });
        r.get("/_gridify/admin/v0/status", new AdminHandler(g) {
            @Override
            protected void handle(Exchange ex) {
                JsonObject body = new JsonObject();
                body.addProperty("isSetup", g.isSetup());
                ex.respond(body);
            }
        });
        r.post("/_gridify/admin/v0/login", new AdminHandler(g) {
            @Override
            protected void handle(Exchange ex) {
                JsonObject reqBody = ex.parseJsonObject();
                String username = GsonUtil.getStringOrNull(reqBody, "username");
                String password = GsonUtil.getStringOrNull(reqBody, "password");
                try {
                    String token = g.overAdmin().login(username, password);
                    ex.respondJsonObject("token", token);
                } catch (UnauthenticatedException e) {
                    UIAuthJson session = UIAuthJson.from(e.getSession());
                    JsonObject body = GsonUtil.makeObj(session);
                    body.addProperty("errcode", "G_UNAUTHORIZED");
                    body.addProperty("error", e.getMessage());
                    ex.respond(401, body);
                }
            }
        });
        r.post("/_gridify/admin/v0/do/setup", new InstallSetupApiHandler(g));
        r.post("/admin/firstRunWizard", new InstallSetupWizardHandler(g));
        r.get("/admin/", new HomepageHandler(g));
        r.get("/admin", new RedirectHandler("/admin/"));
        r.get("/", new AdminHandler(g) {
            @Override
            protected void handle(Exchange ex) {
                if (!g.isSetup()) ex.redirect("/admin/");
                else new RootLogoHandler(g).handle(ex);
            }
        });
        r.get("/**", new RootLogoHandler(g));
    }

}
