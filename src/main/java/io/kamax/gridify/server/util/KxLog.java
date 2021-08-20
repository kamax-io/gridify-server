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

package io.kamax.gridify.server.util;

import io.kamax.gridify.server.GridifyServer;
import io.kamax.gridify.server.network.matrix.http.handler.home.client.ClientApiHandler;
import io.kamax.gridify.server.network.matrix.http.handler.home.server.ServerApiHandler;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KxLog {

    private static final String g = GridifyServer.class.getPackage().getName();
    private static final String gNetMx = g + ".network.matrix";
    private static final String gNetMxC2sApi = ClientApiHandler.class.getCanonicalName();
    private static final String gNetMxS2sApi = ServerApiHandler.class.getCanonicalName();

    public static Logger make(Class<?> c) {
        String name = c.getCanonicalName();
        if (name.startsWith(gNetMxS2sApi)) {
            name = StringUtils.replace(name, gNetMxS2sApi, "[Mx.S2S]", 1);
        }

        if (name.startsWith(gNetMxC2sApi)) {
            name = StringUtils.replace(name, gNetMxC2sApi, "[Mx.C2S]", 1);
        }

        if (name.startsWith(gNetMx)) {
            name = StringUtils.replace(name, gNetMx, "[g.net.mx]", 1);
        }

        if (name.startsWith(g)) {
            name = StringUtils.replace(name, g, "[g]", 1);
        }

        return LoggerFactory.getLogger(name);
    }

}
