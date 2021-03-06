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

package io.kamax.test.gridify.server.core.auth;

import io.kamax.gridify.server.core.auth.AuthResult;
import io.kamax.gridify.server.core.identity.GenericThreePid;
import org.junit.Test;

import static org.junit.Assert.*;

public class AuthResultTest {

    @Test
    public void success() {
        AuthResult result = AuthResult.success(new GenericThreePid("uid", "uid"));
        assertTrue(result.isSuccess());
        assertEquals("uid", result.getUid().getMedium());
        assertEquals("uid", result.getUid().getAddress());
    }

    @Test
    public void fail() {
        AuthResult result = AuthResult.failed();
        assertFalse(result.isSuccess());
        assertNull(result.getUid());
    }

}
