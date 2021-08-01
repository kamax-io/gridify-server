package io.kamax.grid.gridepo.network.matrix.core;

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
