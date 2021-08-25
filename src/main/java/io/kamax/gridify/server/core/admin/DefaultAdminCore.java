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

package io.kamax.gridify.server.core.admin;

import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.config.UIAuthConfig;
import io.kamax.gridify.server.core.GridType;
import io.kamax.gridify.server.core.auth.AuthPasswordDocument;
import io.kamax.gridify.server.core.auth.UIAuthSession;
import io.kamax.gridify.server.core.identity.User;
import io.kamax.gridify.server.util.GsonUtil;

public class DefaultAdminCore implements AdminCore {

    private final GridifyServer g;

    public DefaultAdminCore(GridifyServer g) {
        this.g = g;
    }


    @Override
    public String login(String username, String password) {
        UIAuthConfig sessionCfg = new UIAuthConfig();
        sessionCfg.addFlow().addStage(GridType.of("auth.id.password"));

        AuthPasswordDocument.Identifier id = new AuthPasswordDocument.Identifier();
        id.setType(GridType.id().internal().getId());
        id.setValue(username);
        AuthPasswordDocument doc = new AuthPasswordDocument();
        doc.setType(GridType.of("auth.id.password"));
        doc.setPassword(password);
        doc.setIdentifier(id);
        UIAuthSession session = g.getAuth().getSession("admin", sessionCfg);
        session.complete(GsonUtil.makeObj(doc));
        User u = g.login(session, GridType.of("auth.id.password"));
        return g.createSessionToken("admin", u);
    }
}
