package com.paisafollows.common;

/** Every error the API can return, with its HTTP status code. */
public enum ErrorCode {
    INSUFFICIENT_FUNDS(422),
    INSUFFICIENT_QTY(422),
    MARKET_CLOSED(422),
    NO_PRICE(422),
    INVALID_ORDER(400),
    INVALID_INPUT(400),
    ALREADY_EXISTS(409),
    NOT_FOUND(404),
    UNAUTHORIZED(401);

    private final int status;

    ErrorCode(int status) {
        this.status = status;
    }

    public int status() {
        return status;
    }
}