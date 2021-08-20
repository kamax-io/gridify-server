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

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KxLog {

    private static final String g = "io.kamax.gridify.server";
    private static final String gNetMx = g + ".network.matrix";
    private static final String gNetMxC2sApi = gNetMx + ".http.handler.home.client.ClientApiHandler";

    public static Logger make(Class<?> c) {
        String name = c.getCanonicalName();
        if (name.startsWith(gNetMxC2sApi)) {
            name = StringUtils.replace(name, gNetMxC2sApi, "[MxC2S]", 1);
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
