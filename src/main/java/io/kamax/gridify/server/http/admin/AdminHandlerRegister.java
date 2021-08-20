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

import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.http.Exchange;
import io.kamax.gridify.server.http.RootLogoHandler;
import io.kamax.gridify.server.http.admin.handler.AdminHandler;
import io.kamax.gridify.server.http.admin.handler.HomepageHandler;
import io.kamax.gridify.server.http.admin.handler.InstallSetupApiHandler;
import io.kamax.gridify.server.http.admin.handler.InstallSetupWizardHandler;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.RedirectHandler;

public class AdminHandlerRegister {

    public static void register(GridifyServer g, RoutingHandler r) {
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
