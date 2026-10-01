package com.pokernight.web.session;

import org.springframework.http.HttpStatus;

public final class ApiAuthenticationException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiAuthenticationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
}
