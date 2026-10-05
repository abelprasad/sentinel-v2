package com.sentinel.security;

/**
 * Server-side roles for admin access. The database is the source of truth —
 * roles are never accepted from client input (this is what killed v1's
 * open {@code /auth/register}, where callers chose their own role).
 */
public enum Role {
    /** Read-only: view aircraft, anomalies, baselines. */
    ANALYST,
    /** Analyst plus acknowledge/escalate anomalies and manage aircraft. */
    OPERATOR,
    /** Full access, including user management. */
    ADMIN
}
