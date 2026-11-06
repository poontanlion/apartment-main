package com.victory.apartment.service;

import org.springframework.http.HttpStatus;

/** A pricing rule was broken; the API reports it as {@code {code, message}} with this status. */
public class PriceRuleException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public PriceRuleException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
