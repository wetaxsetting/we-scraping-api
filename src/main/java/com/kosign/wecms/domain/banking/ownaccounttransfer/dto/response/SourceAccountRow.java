package com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response;

import java.math.BigDecimal;

/**
 * One row in the source-account picker for Own Account Transfer.
 * Mirrors the legacy {@code trns_0001_001_r001} record shape but
 * drops fields that are not consumed by the source-picker UI
 * (passwords, NXISAS internals, last-login telemetry).
 *
 * <p>{@code canTransfer} is computed server-side from the
 * {@code ACCT_AUTH_INFO} pivot ({@code use_yn_w='Y' AND auth_type='W'}
 * — legacy parity at {@code trns_0001_001_v001.ts:530-537}) so the
 * table component never sees the raw permission columns. ADMIN
 * callers always get {@code true}.
 *
 * <p>{@code bankCertSeq} carries the bank-side certificate sequence
 * the local NXiSAS engine ties to the user's stored credentials —
 * read-only from this app's perspective; the composer relays it to
 * the engine as part of the transfer composition.
 *
 * <p>Wire shape is lowercase snake_case via the global Jackson naming
 * strategy.
 */
public record SourceAccountRow(
        String     bankCd,
        String     bankNm,
        String     acctNo,
        String     acctNickNm,
        String     acctTypeCd,
        BigDecimal bal,
        BigDecimal realAmt,
        String     ccyCd,
        String     acctBalInqStts,
        String     acctBalInqMsg,
        String     acctBalUpdDtm,
        String     acctBalLstInqDtm,
        String     bankCertSeq,
        boolean    canTransfer
) { }
