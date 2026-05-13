package com.kosign.wecms.domain.setting.contacts.dto;

import java.util.List;

/**
 * One row in the contacts list / detail response. Composed of the
 * master {@code TRCO_INFM} columns plus the three nested child
 * collections.
 *
 * <p><b>Wire shape</b>: lowercase snake_case via the global Jackson
 * SNAKE_CASE strategy &mdash; no per-field {@code @JsonProperty}.
 * {@code trco_type} carries the verbatim DB code ({@code 'L'} /
 * {@code 'O'}); the frontend resolves the localized label.
 *
 * <p>{@code mod_dtm} is exposed for the default-sort tiebreaker on
 * the list endpoint &mdash; legacy 14-char string form.
 */
public record ContactRow(
        String trcoKey,
        String handTrcoKey,
        String trcoCmnm,
        String trcoCmenm,
        String trcoTaxId,
        String trcoType,
        String trcoStts,
        String modDtm,
        List<ContactAddressRow> addresses,
        List<ContactAccountRow> accounts,
        List<ContactOverseasRow> overseasBeneficiaries) { }
