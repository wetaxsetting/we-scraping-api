package com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response;

import java.math.BigDecimal;

/**
 * One row in the source-account picker for Own Account Transfer.
 * Mirrors the legacy {@code trns_0001_001_r001} record shape but drops
 * fields that are not consumed by the source-picker UI (last-login
 * telemetry, NXISAS module internals).
 *
 * <p>{@code canTransfer} is computed server-side from the
 * {@code ACCT_AUTH_INFO} pivot ({@code use_yn_w='Y' AND auth_type='W'}
 * — legacy parity at {@code trns_0001_001_v001.ts:530-537}) so the
 * table component never sees the raw permission columns. ADMIN
 * callers always get {@code true}.
 *
 * <p><b>Cert fields (Phase B):</b>
 * {@code bankCertSeq} + {@code certInfoCon} + {@code certInfoDetlCon}
 * (decrypted) + {@code certInfoDetlCon2} + {@code nxisasGbSeqNo} carry
 * the saved-bank-cert material the local NXiSAS engine needs to log
 * into the bank IB site. They join from {@link com.kosign.wecms.domain.banking.bankcredentials.entity.BankCertInfo}
 * keyed by {@code ACCT_MSTR.BANK_CERT_SEQ}, which is populated by
 * {@code AccountRegistrationService} when the user registers accounts
 * via Settings → Login Credentials → Account Picker. Source rows whose
 * {@code BANK_CERT_SEQ} is NULL (or whose join target has empty cert
 * columns) ship the cert fields as null; the composer's
 * {@code hasSavedBankCert(source)} pre-flight surfaces the
 * "cert not configured" warning for those rows — the expected UX for
 * accounts not yet registered through the credentials flow.
 *
 * <p><b>Security note:</b> {@code certInfoDetlCon} is plaintext over
 * the wire — this is unavoidable because the local NXiSAS.exe is a
 * browser-resident process and the bank IB login requires cleartext.
 * Exposure is constrained to:
 * <ul>
 *   <li>The authenticated tenant ({@code BIZ_KEY} from JWT).</li>
 *   <li>Source rows the user holds a W-grant for (or ADMIN).</li>
 *   <li>VND own-accounts only (OQ-A scope).</li>
 * </ul>
 * The same scope rules already gate the row visibility; the cert
 * fields ride with the row, so no new authorization surface is added.
 * Null/missing-cert rows surface as {@code null} cert fields — the
 * frontend's {@code hasSavedBankCert(source)} pre-flight handles that
 * case by suppressing engine calls and surfacing a warning.
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
        /** Plaintext login id (legacy {@code CERT_INFO_CON}). */
        String     certInfoCon,
        /** Decrypted login password (legacy {@code CERT_INFO_DETL_CON},
         *  decrypted via {@link com.kosign.wecms.domain.banking.bankcredentials.service.BankCertCryptoService}).
         *  Plaintext on the wire — see security note in class Javadoc. */
        String     certInfoDetlCon,
        /** Optional secondary credential field — corporate id for the
         *  few banks that need one (legacy {@code CERT_INFO_DETL_CON2}). */
        String     certInfoDetlCon2,
        /** Engine driver sequence number used by the bundle's
         *  {@code makeHeader(...)} path (legacy {@code NXISAS_GB_SEQ_NO}). */
        String     nxisasGbSeqNo,
        boolean    canTransfer
) { }
