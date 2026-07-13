package com.prizm.auth.service;

public record IssuedAccessToken(String value, long expiresInSeconds) {
}
