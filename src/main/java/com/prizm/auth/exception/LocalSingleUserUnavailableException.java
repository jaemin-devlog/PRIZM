package com.prizm.auth.exception;

public class LocalSingleUserUnavailableException extends RuntimeException {

    public LocalSingleUserUnavailableException() {
        super("Local demo session is unavailable");
    }
}
