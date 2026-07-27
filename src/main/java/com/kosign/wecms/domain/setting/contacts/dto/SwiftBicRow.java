package com.kosign.wecms.domain.setting.contacts.dto;

/**
 * One row in the overseas-contact BIC search typeahead.
 *
 * <p>Mirrors the legacy {@code comm_1007_001} output record
 * {@code {BIC_CD, TWN_NM}} (from {@code IDO.SWIFT_BIC_MST_R002}). On
 * selection the frontend writes {@code bicCd} into the BIC field and
 * auto-fills the read-only Town Name from {@code twnNm}.
 *
 * <p>Wire convention: lowercase snake_case via the global Jackson
 * strategy → {@code bic_cd} / {@code twn_nm}.
 *
 * @param bicCd  ISO 20022 BIC code (8 or 11 chars).
 * @param twnNm  Postal-address town / city name (legacy
 *               {@code PSTL_ADR_TWN_NM AS TWN_NM}); may be null.
 */
public record SwiftBicRow(String bicCd, String twnNm) { }
