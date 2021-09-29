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

import io.kamax.gridify.server.GridifyServer;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class HomeServerManager {

    private final GridifyServer g;
    private HomeServerClient networkClient;

    public HomeServerManager(GridifyServer g) {
        this.g = g;
        networkClient = new HomeServerHttpClient();
    }

    public void setNetworkClient(HomeServerClient networkClient) {
        this.networkClient = networkClient;
    }

    public List<HomeServerLink> getLink(String origin, Collection<String> domains, boolean all) {
        return domains.stream()
                .filter(domain -> all || !StringUtils.equals(origin, domain))
                .map(domain -> getLink(origin, domain))
                .collect(Collectors.toList());
    }

    public HomeServerLink getLink(String origin, String domain) {
        return new HomeServerLink(g.overMatrix().forDomain(origin), domain, networkClient);
    }

}
