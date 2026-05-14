package com.kosign.wecms.domain.setting.users.dto;

/**
 * Response for {@code GET /api/v1/settings/users/check-id?login_id=…}.
 *
 * <p>Drives the panel's "Check availability" button next to the ID
 * field. {@code available = true} when no {@code USER_INFM} row exists
 * for the candidate id (across all tenants — {@code USER_ID} is the
 * global PK).
 */
public record CheckLoginIdResponse(boolean available) { }
