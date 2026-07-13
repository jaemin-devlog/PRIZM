package com.prizm.auth.service;

public record IssuedAccessToken(String value, long expiresInSeconds) {

    @Override
    public String toString() {
        return "IssuedAccessToken[value=[REDACTED], expiresInSeconds=" + expiresInSeconds + "]";
    }
}
