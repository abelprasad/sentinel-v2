package com.sentinel.web;

import java.time.Instant;

/**
 * Uniform error body for every API failure. The {@code message} is always
 * safe to show — internal details are logged server-side, never serialized.
 */
public record ErrorDto(
        String error,
        String message,
        String path,
        Instant timestamp) {

    public static ErrorDto of(String error, String message, String path) {
        return new ErrorDto(error, message, path, Instant.now());
    }
}
