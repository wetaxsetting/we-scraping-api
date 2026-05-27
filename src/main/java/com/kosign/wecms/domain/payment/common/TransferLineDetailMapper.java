package com.kosign.wecms.domain.payment.common;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.banking.service.BankCdResolver;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferLineDetail;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartOfAccount;
import com.kosign.wecms.domain.setting.chartofaccounts.repository.ChartOfAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Shared per-line projector for the Request Approval dialog used by
 * every batch-file Payment module (Internal Transfer / Vendor /
 * Expense / Payroll). Resolves the two display-only fields the wire
 * doesn't carry verbatim:
 *
 * <ul>
 *   <li>{@code tarBankNm} — human-readable bank name resolved from
 *       {@code tarBankCd} via {@link BankCdResolver}. The dialog
 *       renders it next to the deposit bank code.</li>
 *   <li>{@code chartAcctBreadcrumb} — ancestor chain (no leaf, no
 *       codes) for the chart-of-accounts entry, e.g.
 *       {@code "EXPENSES > PAYMENT"}. Walks the parent chain on
 *       {@code CHART_ACCT_INFM}.</li>
 * </ul>
 *
 * <p>Each consumer service injects this component, builds the tenant
 * chart-account index ONCE per detail() call via
 * {@link #buildChartIndex(String)}, then maps each line via
 * {@link #toLineDetail(ScdulPymnSumrDetl, Map)}. The index pattern
 * avoids N+1 lookups when a batch has many lines.
 */
@Component
@RequiredArgsConstructor
public class TransferLineDetailMapper {

    private static final int MAX_BREADCRUMB_HOPS = 6;

    private final BankCdResolver bankCdResolver;
    private final ChartOfAccountRepository chartOfAccountRepository;
    /** NAPAS / CITAD code resolver — `tar_bank_cd` on Internal Transfer
     *  lines carries NAPAS codes (`970424` etc.) which aren't seeded in
     *  `BANK_INFM`. Falls back to the legacy `wb_get_napas_bank_nm_or
     *  _citad_bank_nm` function ported in V86 over `tb_firm_bank_code`. */
    private final LegacyFunctionRepository legacyFunctionRepository;

    /** Build a per-tenant {@code code → ChartOfAccount} index used by
     *  {@link #toLineDetail} to walk parent chains in-memory. Call once
     *  per detail() request — never inside a per-line loop. */
    public Map<String, ChartOfAccount> buildChartIndex(String bizKey) {
        return chartOfAccountRepository
                .findByBizKeyAndDelYn(bizKey, "N", Sort.unsorted())
                .stream()
                .collect(Collectors.toMap(
                        ChartOfAccount::getChartAcctCd,
                        Function.identity(),
                        (a, b) -> a));
    }

    /** Project one {@link ScdulPymnSumrDetl} row onto the wire DTO,
     *  resolving the two display-only fields. The shared row entity
     *  is used by every Payment module so the projection lives here
     *  in {@code domain/payment/common/} rather than per-module. */
    public TransferLineDetail toLineDetail(
            ScdulPymnSumrDetl d,
            Map<String, ChartOfAccount> chartByCode) {
        String tarBankNm = resolveBankName(d.getTarBankCd());
        String breadcrumb = buildChartBreadcrumb(d.getChartAcctCd(), chartByCode);
        return new TransferLineDetail(
                d.getScdulPymnDetlNo(),
                d.getAntDivCd(),
                d.getRcptNm(),
                d.getTarBankCd(),
                tarBankNm,
                d.getFincMdaNo(),
                d.getTranUsag(),
                d.getTranAmt(),
                d.getTrnfCcyCd(),
                d.getTrcoKey(),
                d.getHandTrcoKey(),
                d.getDocNo(),
                d.getNote(),
                d.getCitadBankCd(),
                d.getChartAcctCd(),
                d.getChartAcctSeqNo(),
                breadcrumb,
                d.getDepoCcyCd(),
                d.getBankTrnsShortId());
    }

    /** Two-step bank-name lookup mirroring the legacy
     *  {@code resl_0001_001 → comm_0195_001} flow:
     *
     *  <ol>
     *    <li>Try {@link BankCdResolver#resolveBankNm} which reads
     *        {@code BANK_INFM} (seeded in V20 — Vietnamese internal
     *        bank codes like {@code "01"}/{@code "02"}). When the code
     *        is there the resolver returns the real name; when it's
     *        missing it returns the input code verbatim.</li>
     *    <li>If step 1 echoed the code back (= cache miss), retry via
     *        {@link LegacyFunctionRepository#resolveNapasOrCitadBankName}
     *        which calls the V86-ported PL/pgSQL function over
     *        {@code tb_firm_bank_code}. This covers the NAPAS codes
     *        (e.g. {@code 970424} → ShinhanBank) the Internal Transfer
     *        Excel upload writes onto {@code SCDUL_PYMN_SUMR_DETL.TAR_BANK_CD}.</li>
     *  </ol>
     *
     *  <p>Returns {@code null} when neither lookup matches, so the
     *  dialog renders the code alone instead of "code - code".
     */
    /**
     * Public resolver for callers that need the same two-step lookup
     * outside the per-line projection — e.g. the Internal Transfer
     * Approval Details popup's "Withdrawal Bank" row, which shows the
     * batch master's {@code OUT_BANK_CD} resolved to the same human
     * name format as the per-line "Deposit Bank" cell.
     */
    public String resolveBankNm(String bankCd) {
        return resolveBankName(bankCd);
    }

    private String resolveBankName(String bankCd) {
        if (bankCd == null || bankCd.isBlank()) return null;
        String resolved = bankCdResolver.resolveBankNm(bankCd);
        if (resolved != null && !resolved.isBlank() && !resolved.equals(bankCd)) {
            return resolved;
        }
        // BankCdResolver echoed the code back (no BANK_INFM match).
        // Retry via the legacy NAPAS / CITAD function — "en" mirrors
        // legacy `LangCd.EN` since the dialog renders English labels.
        String napas = legacyFunctionRepository.resolveNapasOrCitadBankName(bankCd, "en");
        if (napas != null && !napas.isBlank() && !napas.equals(bankCd)) {
            return napas;
        }
        return null;
    }

    /** Walk the parent chain UP from the leaf chart-account code,
     *  building a {@code "GRANDPARENT > PARENT"} display string. The
     *  leaf itself is excluded — its own name renders on the dialog's
     *  primary line. Cycle-safe + depth-capped so malformed seed data
     *  can't loop the request thread. */
    private static String buildChartBreadcrumb(
            String leafCode,
            Map<String, ChartOfAccount> chartByCode) {
        if (leafCode == null || leafCode.isBlank()) return null;
        ChartOfAccount leaf = chartByCode.get(leafCode);
        if (leaf == null) return null;
        List<String> ancestors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String parentCd = leaf.getParentChartAcctCd();
        int hops = 0;
        while (parentCd != null && !parentCd.isBlank() && hops++ < MAX_BREADCRUMB_HOPS) {
            if (!seen.add(parentCd)) break;
            ChartOfAccount parent = chartByCode.get(parentCd);
            if (parent == null) break;
            String nm = parent.getChartAcctNmEn();
            if (nm != null && !nm.isBlank()) ancestors.add(0, nm);
            parentCd = parent.getParentChartAcctCd();
        }
        return ancestors.isEmpty() ? null : String.join(" > ", ancestors);
    }
}
