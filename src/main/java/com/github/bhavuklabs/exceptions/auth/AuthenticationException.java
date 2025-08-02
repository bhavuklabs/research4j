package com.github.bhavuklabs.exceptions.auth;

import com.github.bhavuklabs.exceptions.Research4jException;

public class AuthenticationException extends Research4jException {

    private final String provider;

    public AuthenticationException(String message, String provider) {
        super("AUTH_ERROR", message, provider);
        this.provider = provider;
    }

    public AuthenticationException(String message, Throwable cause, String provider) {
        super("AUTH_ERROR", message, cause, provider);
        this.provider = provider;
    }

    public String getProvider() {
        return provider;
    }
}
