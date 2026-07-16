package com.prizm.cleanup.service;

public record CleanupFailure(boolean retryable, String errorCode) {
}
