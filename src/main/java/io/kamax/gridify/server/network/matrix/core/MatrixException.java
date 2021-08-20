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

package io.kamax.gridify.server.network.matrix.core;

public class MatrixException extends RuntimeException {

    private int code;
    private String errCode;
    private String error;

    public MatrixException(int code, String errCode, String error) {
        this.code = code;
        this.errCode = errCode;
        this.error = error;
    }

    public int getCode() {
        return code;
    }

    public String getErrCode() {
        return errCode;
    }

    public String getError() {
        return error;
    }
}
