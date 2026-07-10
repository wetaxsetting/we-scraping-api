package com.kosign.wecms.domain.payment.validation.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.payment.validation.PymnType;
import com.kosign.wecms.domain.payment.validation.dto.BlockedCharsRuleRow;
import com.kosign.wecms.domain.payment.validation.dto.BlockedCharsRulesResponse;
import com.kosign.wecms.domain.payment.validation.entity.BlockedCharsRule;
import com.kosign.wecms.domain.payment.validation.repository.BlockedCharsRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Read-only endpoint that returns active {@code BLOCKED_CHARS_RULE}
 * rows for a given {@code (bank_cd, pymn_type)} pair. Drives the
 * column-header tooltip on every Excel upload preview + payment
 * Scheduled / Result detail table.
 *
 * <p>Legacy parity: replaces the hardcoded JS lookup table in
 * {@code add_osmc.js#addHeaderInfoIcon} (a per-bank map literally
 * embedded in JavaScript). The refactor reads from the same DB the
 * server-side {@code SpecialCharValidator} already consults, so the
 * tooltip and the validation are guaranteed to stay in sync.
 *
 * <p>The endpoint is intentionally module-neutral — every payment
 * module (Internal Transfer, Vendor, Expense, Payroll, Overseas)
 * passes its {@link PymnType} via the {@code pymn_type} query param.
 * Adding rules for a new bank / payment type is a Flyway insert into
 * {@code BLOCKED_CHARS_RULE}; no controller or frontend change is
 * required.
 */
@RestController
@RequestMapping("/api/v1/payment/blocked-chars")
@RequiredArgsConstructor
public class BlockedCharsRuleController {

    private static final DateTimeFormatter DT8 = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId VN_TZ = ZoneId.of("Asia/Ho_Chi_Minh");

    private final BlockedCharsRuleRepository repository;
    private final com.kosign.wecms.domain.payment.common.OutBankCdNormalizer outBankCdNormalizer;

    /**
     * @param bankCd    withdrawal bank code. Callers pass it in EITHER form:
     *                  the upload dialog sends the internal {@code BANK_CD}
     *                  (e.g. {@code 11704088}); the payment detail / approval
     *                  screens send the payment's stored {@code OUT_BANK_CD},
     *                  which is the canonical NAPAS code (e.g. {@code 970424}).
     *                  {@code BLOCKED_CHARS_RULE.BANK_CD} is keyed only on the
     *                  internal code, so we resolve NAPAS → internal first
     *                  (audit task-44508 fix — without this the detail /
     *                  approval tooltips silently returned no rule, which is
     *                  why the info icons disappeared once the hardcoded
     *                  frontend fallback was removed). An already-internal code
     *                  passes through {@code toInternalBankCd} unchanged.
     * @param pymnType  payment type code (e.g. {@code "INTR"} / {@code "VEND"})
     */
    @GetMapping
    public ApiResponse<BlockedCharsRulesResponse> list(
            @RequestParam("bank_cd")   String bankCd,
            @RequestParam("pymn_type") String pymnType) {

        String internalBankCd = outBankCdNormalizer.toInternalBankCd(bankCd);
        String asOf = LocalDate.now(VN_TZ).format(DT8);
        List<BlockedCharsRuleRow> rows = repository
                .findActiveByBankAndPymnType(internalBankCd, pymnType, asOf)
                .stream()
                .map(BlockedCharsRuleController::toRow)
                .collect(Collectors.toList());
        return ApiResponse.ok(new BlockedCharsRulesResponse(rows));
    }

    private static BlockedCharsRuleRow toRow(BlockedCharsRule r) {
        return new BlockedCharsRuleRow(r.getFieldCd(), r.getBlockedChars());
    }
}
