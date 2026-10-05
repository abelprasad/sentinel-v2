package com.sentinel.web;

/**
 * Thrown when a requested resource does not exist. Mapped to 404 by
 * {@link ApiExceptionHandler}.
 *
 * <p>v1 returned 403 for a missing track because the authorization check
 * ran before the existence check. v2 checks existence first, so a
 * missing track is a 404, never a 403.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, String identifier) {
        super(resource + " not found: " + identifier);
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
