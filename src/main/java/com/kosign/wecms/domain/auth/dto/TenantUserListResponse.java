package com.kosign.wecms.domain.auth.dto;

import java.util.List;

/**
 * Envelope for {@code GET /api/v1/users}. No server-side pagination
 * &mdash; the tenant-user directory is small enough in practice
 * (matches the legacy {@code comm_0192_001_r001} behaviour which
 * also returns the whole result set in one shot).
 *
 * <p>{@code totalCount} is the length of {@link #users} &mdash;
 * shipped on the wire as {@code total_count} so the frontend can
 * render a "showing X of Y" hint without recomputing.
 */
public record TenantUserListResponse(
        int                  totalCount,
        List<TenantUserRow>  users
) {}
