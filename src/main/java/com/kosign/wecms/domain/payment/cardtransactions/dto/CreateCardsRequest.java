package com.kosign.wecms.domain.payment.cardtransactions.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Save-cards payload for the Card Management modal's Add Card flow —
 * legacy {@code comm_0218_007_c001} (the "Confirm Cards" → "Save" step
 * inside {@code comm_0218_nxisas_view.jsp}). Posted by the frontend
 * after the user has scraped a bank's card list via the NXISAS popup
 * chain and ticked which cards to register.
 *
 * <p>Tenant scope is read from {@link com.kosign.wecms.common.tenant.TenantContext}
 * — the request does NOT carry {@code biz_key}.
 *
 * <p>{@code bank_cd} identifies the issuing bank (e.g. {@code 79302001}
 * for VCB). {@code bank_cert_seq} optionally points at the
 * {@code BANK_CERT_INFO.SEQ_NO} row whose credentials sourced the
 * scrape — written verbatim into {@code CARD_MSTR.BANK_CERT_SEQ} so
 * future inquiries can re-authenticate without prompting the user.
 * Null when the user verified credentials inline without saving them.
 */
public record CreateCardsRequest(
        @NotBlank @Pattern(regexp = "^[0-9A-Za-z]{1,20}$",
                message = "bank_cd: invalid format") String bankCd,
        Long                                                bankCertSeq,
        @NotEmpty @Valid List<ScrapedCardItem>              cards) {

    /**
     * One scraped card row. Mirrors the legacy NXISAS {@code CARD_INFO}
     * job's per-card output. {@code card_no} is the masked card number
     * the bank's scrape returns (e.g. {@code 4111-****-****-2308}) —
     * stored verbatim into {@code CARD_MSTR.CARD_NO} (which is the
     * tenant's PK component, so dedupe runs on it).
     *
     * <p>{@code card_type_cd} is the legacy single-character / two-character
     * type code (e.g. {@code 01} for credit, {@code 02} for debit) — the
     * frontend resolves this from the bank's card metadata. {@code issu_dt}
     * / {@code expi_dt} are {@code yyyyMMdd} strings; both optional.
     *
     * <p>{@code card_nick_nm} is optional and editable later via the
     * Card Management modal's inline-rename flow.
     */
    public record ScrapedCardItem(
            @NotBlank @Pattern(regexp = "^[0-9A-Za-z*\\-]{1,100}$",
                    message = "card_no: invalid format")  String cardNo,
            @NotBlank @Size(max = 2)                      String cardTypeCd,
            @Size(max = 8)                                String issuDt,
            @Size(max = 8)                                String expiDt,
            @Size(max = 50)                               String cardNm,
            @Size(max = 50)                               String cardNickNm,
            @NotBlank @Size(max = 50)                     String cardHldrNm,
            @Size(max = 50)                               String nxisasAcctType,
            @Size(max = 50)                               String pymnAcctNo) {
    }
}
