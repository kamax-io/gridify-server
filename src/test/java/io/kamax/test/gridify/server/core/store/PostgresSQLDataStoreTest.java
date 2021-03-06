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

package io.kamax.test.gridify.server.core.store;

import io.kamax.gridify.server.config.StorageConfig;
import io.kamax.gridify.server.core.store.DataStore;
import io.kamax.gridify.server.core.store.postgres.PostgreSQLDataStore;
import io.kamax.gridify.server.util.GsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.junit.BeforeClass;

import java.util.Objects;

import static org.junit.Assume.assumeTrue;

public class PostgresSQLDataStoreTest extends DataStoreTest {

    private static StorageConfig cfg = new StorageConfig();
    private static PostgreSQLDataStore pStore;

    @BeforeClass
    public static void beforeClass() {
        String cfgJson = System.getenv("GRIDIFY_TEST_STORE_POSTGRESQL_CONFIG");
        assumeTrue(StringUtils.isNotBlank(cfgJson));
        StorageConfig.Database dbCfg = GsonUtil.parse(cfgJson, StorageConfig.Database.class);
        cfg.setDatabase(dbCfg);
        cfg.getDatabase().getPool().setRetryAttempts(0);
    }

    @Override
    protected DataStore getNewStore() {
        if (Objects.isNull(pStore)) {
            pStore = new PostgreSQLDataStore(cfg);
        }

        return pStore;
    }

}
