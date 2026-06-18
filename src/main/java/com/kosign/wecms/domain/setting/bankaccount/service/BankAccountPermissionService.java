package com.kosign.wecms.domain.setting.bankaccount.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.repository.BusiAdmnInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository.TenantUserManagementProjection;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.setting.bankaccount.dto.BankAccountPermissionMember;
import com.kosign.wecms.domain.setting.bankaccount.dto.BankAccountPermissionResponse;
import com.kosign.wecms.domain.setting.bankaccount.dto.BankAccountPermissionUpdateRequest;
import com.kosign.wecms.domain.setting.bankaccount.entity.AcctAuthInfo;
import com.kosign.wecms.domain.setting.bankaccount.entity.BankAccountAuthType;
import com.kosign.wecms.domain.setting.bankaccount.repository.AcctAuthInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Settings &gt; Bank Account &gt; View / Withdrawal Permission service.
 *
 * <p>Mirrors the legacy {@code comm_0220_*} permission popup
 * (`ACCT_AUTH_INFO` per-user-per-account rows). One {@code AUTH_TYPE}
 * row per kind ({@code 'V'} view / {@code 'W'} withdraw) — both can
 * exist independently for one account+user.
 *
 * <p>Save uses replace-semantics with a grant/revoke diff and a soft-
 * revoke ({@code USE_YN='N'}) to preserve audit history. Admin users
 * cannot be revoked; non-admin callers cannot revoke themselves.
 * Every write fans out to per-locale audit-log rows via the shared
 * {@link AuditLogService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankAccountPermissionService {

    /** Audit-log discriminator — same slug as the parent service. */
    private static final String WSVC_ID = SettingsBankAccountService.WSVC_ID;
    private static final String REF_NO  = "ACCT_AUTH_INFO";
    private static final DateTimeFormatter DTM14 =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AcctMstrRepository           acctRepo;
    private final AcctAuthInfoRepository       authRepo;
    private final UserBusiConnInfmRepository   userBusiRepo;
    private final BusiAdmnInfmRepository       busiAdmnRepo;
    private final AuditLogService              auditLogService;

    /* -----------------------------------------------------------------
     *  READ — full roster for one (account, kind)
     * ----------------------------------------------------------------- */

    @Transactional(readOnly = true)
    public BankAccountPermissionResponse list(String bankCd, String acctNo, String kindWire) {
        String bizKey = TenantContext.currentBizKey();
        String callerId = TenantContext.currentUserId();

        BankAccountAuthType kind = BankAccountAuthType.fromWireName(kindWire)
                .orElseThrow(() -> new BusinessException("WBE_VALIDATION"));
        if (kind == BankAccountAuthType.FULL_ACCESS) {
            // FULL_ACCESS is in the DB enum but not on this endpoint.
            throw new BusinessException("WBE_VALIDATION");
        }

        AcctMstr acct = acctRepo.findByBizKeyAndBankCdAndAcctNo(bizKey, bankCd, acctNo)
                .orElseThrow(() -> new BusinessException("WBE_BANK_ACCOUNT_NOT_FOUND"));

        // Existing active grants for this (account, kind).
        Set<String> activeUserIds = new HashSet<>();
        for (AcctAuthInfo row : authRepo.findAllByAccount(bizKey, bankCd, acctNo, kind.code())) {
            if ("Y".equals(row.getUseYn())) {
                activeUserIds.add(row.getUserId());
            }
        }

        // Every active user in the tenant — picker roster.
        List<TenantUserManagementProjection> users =
                userBusiRepo.findTenantUsersForManagement(bizKey);

        // Admin set for the tenant (USER_TYPE = ADMIN).
        Set<String> adminUserIds = new HashSet<>();
        busiAdmnRepo.findAll().forEach(row -> {
            if (bizKey.equals(row.getBizKey())) {
                adminUserIds.add(row.getUserId());
            }
        });

        List<BankAccountPermissionMember> members = new ArrayList<>(users.size());
        for (TenantUserManagementProjection u : users) {
            // Legacy comm_0183_002_r001 (IDO.ACCT_AUTH_INFO_R004:80) filters
            // `AND T.ADMIN_YN != 'Y'` — admins are implicitly authorized and
            // never appear as selectable grantees in the permission popup.
            if (adminUserIds.contains(u.getUserId())) {
                continue;
            }
            boolean isSelf     = u.getUserId().equals(callerId);
            boolean isSelected = activeUserIds.contains(u.getUserId());
            String  lockReason = null;
            boolean isLocked   = false;
            if (isSelf && isSelected && !TenantContext.currentIsAdmin()) {
                // Self-row stays disabled when the caller is the
                // selected row and isn't admin — defence in depth so
                // the dialog can't even attempt the WBE_CANNOT_REVOKE_SELF
                // path from the UI.
                isLocked = true;
                lockReason = "SELF";
            }
            members.add(new BankAccountPermissionMember(
                    u.getUserId(),
                    u.getUserNm(),
                    "USER",
                    maskPhone(u.getClphNo()),
                    isSelected,
                    isLocked,
                    lockReason));
        }
        // Admin-first then by display name, stable on userId for ties.
        members.sort(Comparator
                .comparing((BankAccountPermissionMember m) -> "ADMIN".equals(m.userType()) ? 0 : 1)
                .thenComparing(BankAccountPermissionMember::userNm, Comparator.nullsLast(String::compareTo))
                .thenComparing(BankAccountPermissionMember::userId));

        int totalSelected = (int) members.stream().filter(BankAccountPermissionMember::isSelected).count();
        String accountLabel = composeAccountLabel(acct);
        return new BankAccountPermissionResponse(kind.wireName(), accountLabel, members, totalSelected);
    }

    /* -----------------------------------------------------------------
     *  WRITE — replace semantics, soft-revoke on remove
     * ----------------------------------------------------------------- */

    @Transactional
    public void replace(String bankCd, String acctNo, BankAccountPermissionUpdateRequest req) {
        String bizKey   = TenantContext.currentBizKey();
        String callerId = TenantContext.currentUserId();

        BankAccountAuthType kind = BankAccountAuthType.fromWireName(req.kind())
                .orElseThrow(() -> new BusinessException("WBE_VALIDATION"));
        if (kind == BankAccountAuthType.FULL_ACCESS) {
            throw new BusinessException("WBE_VALIDATION");
        }

        // Existence check — never leak via 403; missing → 404.
        if (acctRepo.findByBizKeyAndBankCdAndAcctNo(bizKey, bankCd, acctNo).isEmpty()) {
            throw new BusinessException("WBE_BANK_ACCOUNT_NOT_FOUND");
        }

        Set<String> wantIds = req.userIds() == null
                ? Set.of()
                : new HashSet<>(req.userIds());

        // Withdraw permission may only be granted on the 5 withdrawal-capable
        // banks (legacy comm_0183_002_u001_act.jsp:182/192 → WBE0452; business
        // decision 2026-06-16 = keep). Block any grant on a non-whitelisted bank;
        // a pure revoke-all (empty want set) is still allowed so stale rows can be
        // cleared.
        if (kind == BankAccountAuthType.WITHDRAWAL
                && !WithdrawalBankPolicy.isWithdrawalAllowed(bankCd)
                && !wantIds.isEmpty()) {
            throw new BusinessException("WBE0452");
        }

        // Validate every want_id belongs to the tenant.
        if (!wantIds.isEmpty()) {
            long matched = userBusiRepo
                    .findAllByUserIdInAndBizKeyAndDelYn(wantIds, bizKey, "N").size();
            if (matched != wantIds.size()) {
                throw new BusinessException("WBE_USER_NOT_IN_TENANT");
            }
        }

        // Admin set — used only by the self-revoke guard below. Admins are
        // NOT part of the grantee roster (legacy comm_0183_002_r001 excludes
        // them via ADMIN_YN != 'Y'), so there is no "admin must remain
        // selected" guard: the popup never lists admins and the FE never
        // sends admin ids in the want set.
        Set<String> adminIds = new HashSet<>();
        busiAdmnRepo.findAll().forEach(row -> {
            if (bizKey.equals(row.getBizKey())) {
                adminIds.add(row.getUserId());
            }
        });

        // Self-revoke guard for non-admin callers.
        if (!TenantContext.currentIsAdmin()
                && !adminIds.contains(callerId)
                && !wantIds.contains(callerId)) {
            // Current state — is the caller currently selected?
            boolean callerCurrentlyHas = authRepo
                    .findAllByAccount(bizKey, bankCd, acctNo, kind.code()).stream()
                    .anyMatch(r -> callerId.equals(r.getUserId()) && "Y".equals(r.getUseYn()));
            if (callerCurrentlyHas) {
                throw new BusinessException("WBE_CANNOT_REVOKE_SELF");
            }
        }

        List<AcctAuthInfo> existing = authRepo.findAllByAccount(bizKey, bankCd, acctNo, kind.code());
        Map<String, AcctAuthInfo> existingByUser = new HashMap<>();
        for (AcctAuthInfo row : existing) existingByUser.put(row.getUserId(), row);

        String now = nowDtm14();
        List<String> granted = new ArrayList<>();
        List<String> revoked = new ArrayList<>();

        // Grant phase — insert new rows or flip USE_YN back to 'Y'.
        for (String userId : wantIds) {
            AcctAuthInfo row = existingByUser.get(userId);
            if (row == null) {
                row = new AcctAuthInfo();
                row.setBizKey(bizKey);
                row.setUserId(userId);
                row.setBankCd(bankCd);
                row.setAcctNo(acctNo);
                row.setAuthType(kind.code());
                row.setUseYn("Y");
                row.setRegId(callerId);
                row.setRegDtm(now);
                row.setModId(callerId);
                row.setModDtm(now);
                authRepo.save(row);
                granted.add(userId);
            } else if ("N".equals(row.getUseYn())) {
                row.setUseYn("Y");
                row.setModId(callerId);
                row.setModDtm(now);
                authRepo.save(row);
                granted.add(userId);
            }
        }

        // Revoke phase — soft revoke existing active rows that are no
        // longer in the want set.
        for (AcctAuthInfo row : existing) {
            if (!wantIds.contains(row.getUserId()) && "Y".equals(row.getUseYn())) {
                row.setUseYn("N");
                row.setModId(callerId);
                row.setModDtm(now);
                authRepo.save(row);
                revoked.add(row.getUserId());
            }
        }

        // Skip audit when nothing actually changed — idempotent saves
        // shouldn't pollute the audit log.
        if (granted.isEmpty() && revoked.isEmpty()) return;

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .refNo1(bankCd)
                .refNo2(acctNo)
                .activityKey("audit.bank_account.activity")
                .detailKey(kind == BankAccountAuthType.VIEW
                        ? "audit.bank_account.permission.view.detail"
                        : "audit.bank_account.permission.withdrawal.detail")
                .msgArgs(new Object[]{granted.size(), revoked.size()})
                .build());
    }

    /* -----------------------------------------------------------------
     *  Helpers
     * ----------------------------------------------------------------- */

    private static String composeAccountLabel(AcctMstr e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getBankCd()).append(" / ").append(e.getAcctNo());
        if (e.getCcyCd() != null && !e.getCcyCd().isBlank()) {
            sb.append(" (").append(e.getCcyCd()).append(')');
        }
        return sb.toString();
    }

    /** Phone-number mask: last 4 digits visible, rest replaced with
     *  {@code •}. Empty / null phones pass through unchanged. The dialog
     *  renders the masked value verbatim. */
    private static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return phone;
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() <= 4) return phone;
        String tail = digits.substring(digits.length() - 4);
        return "•••••" + tail;
    }

    private static String nowDtm14() {
        return LocalDateTime.now(ZoneOffset.UTC).format(DTM14);
    }
}
