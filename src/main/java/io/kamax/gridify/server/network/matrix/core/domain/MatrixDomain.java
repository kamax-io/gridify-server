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

package io.kamax.gridify.server.network.matrix.core.domain;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.kamax.gridify.server.core.crypto.KeyIdentifier;
import io.kamax.gridify.server.core.crypto.RegularKeyIdentifier;
import io.kamax.gridify.server.core.store.DomainDao;
import io.kamax.gridify.server.util.GsonUtil;

import java.util.ArrayList;
import java.util.List;

public class MatrixDomain {

    public static MatrixDomain fromDao(DomainDao dao) {
        MatrixDomain domain = new MatrixDomain();

        domain.setLid(dao.getLocalId());
        domain.setDomain(dao.getDomain());
        domain.setHost(dao.getHost());
        domain.setCfg(GsonUtil.fromJson(dao.getConfig(), MatrixDomainConfig.class));
        GsonUtil.findString(dao.getProperties(), "signing_key").ifPresent(v -> {
            domain.setSigningKey(RegularKeyIdentifier.parse(v));
        });
        GsonUtil.findArray(dao.getProperties(), "old_signing_keys").ifPresent(v -> {
            v.forEach(vv -> domain.getOldSigningKeys().add(RegularKeyIdentifier.parse(vv.getAsString())));
        });

        return domain;
    }

    private Long lid;
    private String domain;
    private String host;
    private KeyIdentifier signingKey;
    private List<KeyIdentifier> oldSigningKeys = new ArrayList<>();
    private MatrixDomainConfig cfg = new MatrixDomainConfig();

    public Long getLid() {
        return lid;
    }

    public void setLid(long lid) {
        this.lid = lid;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public KeyIdentifier getSigningKey() {
        return signingKey;
    }

    public void setSigningKey(KeyIdentifier signingKey) {
        this.signingKey = signingKey;
    }

    public List<KeyIdentifier> getOldSigningKeys() {
        return oldSigningKeys;
    }

    public void setOldSigningKeys(List<KeyIdentifier> oldSigningKeys) {
        this.oldSigningKeys = oldSigningKeys;
    }

    public MatrixDomainConfig getCfg() {
        return cfg;
    }

    public void setCfg(MatrixDomainConfig cfg) {
        this.cfg = cfg;
    }

    public DomainDao toDao() {
        JsonArray oldKeys = new JsonArray();
        oldSigningKeys.forEach(k -> oldKeys.add(k.getId()));
        JsonObject properties = new JsonObject();
        properties.addProperty("signing_key", signingKey.getId());
        properties.add("old_signing_keys", oldKeys);
        DomainDao dao = new DomainDao();
        dao.setLocalId(lid);
        dao.setNetwork("matrix");
        dao.setDomain(domain);
        dao.setHost(host);
        dao.setProperties(properties);
        dao.setConfig(GsonUtil.makeObj(cfg));
        return dao;
    }

}
