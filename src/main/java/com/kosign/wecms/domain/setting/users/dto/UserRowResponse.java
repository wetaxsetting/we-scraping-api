package com.kosign.wecms.domain.setting.users.dto;

/**
 * One user row in the Settings &gt; Users list / detail response.
 *
 * <p>Java field names are camelCase; the global Jackson SNAKE_CASE
 * naming strategy emits them as lowercase snake_case on the wire
 * (e.g. {@code userNm} → {@code user_nm}).
 *
 * @param userId           login id (PK on {@code USER_INFM})
 * @param userNm           display name
 * @param eml              email
 * @param clphNo           telephone (free-form, country code embedded)
 * @param posiNm           position / job title
 * @param deptNm           department
 * @param isAdmin          {@code true} when a {@code BUSI_ADMN_INFM} row
 *                         exists for the current tenant
 * @param isCurrentUser    {@code true} when this row is the JWT caller
 *                         (the frontend renders the "(You)" suffix)
 * @param userImgUrl       avatar URL or {@code null} when no avatar is
 *                         set; placeholder for the future avatar pipeline
 */
public record UserRowResponse(
        String  userId,
        String  userNm,
        String  eml,
        String  clphNo,
        String  posiNm,
        String  deptNm,
        boolean isAdmin,
        boolean isCurrentUser,
        String  userImgUrl
) { }
