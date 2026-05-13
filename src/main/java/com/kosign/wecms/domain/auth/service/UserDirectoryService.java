package com.kosign.wecms.domain.auth.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.dto.TenantUserListResponse;
import com.kosign.wecms.domain.auth.dto.TenantUserRow;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository.TenantUserProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Tenant-user directory service — drives the {@code GET /api/v1/users}
 * endpoint consumed by approver / signer pickers. Mirrors the legacy
 * {@code comm_0192_001_r001} ("Inquiry Approver") behaviour with two
 * deliberate divergences:
 *
 * <ul>
 *   <li>No phone-number column &mdash; legacy decrypts {@code CLPH_NO}
 *       via the legacy {@code decrypt()} DB function. The Approval
 *       Line picker has no phone-number UI; the cost of porting the
 *       function is not justified for this consumer.</li>
 *   <li>No {@code ADMIN_YN} / {@code MOB_YN} sub-flags &mdash; sub-
 *       flags inherited from the broader
 *       {@code USER_BUSI_CONN_INFM_R004} IDO; not consumed here.</li>
 * </ul>
 *
 * <p>Read-only &mdash; no audit-log writes, no mutations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDirectoryService {

    /** Slug-based REST URI template the picker hands back for
     *  approvers with a stored image. The streaming endpoint itself
     *  remains a follow-up MR (Approval Line OQ-1); the frontend's
     *  {@code <Avatar>} fallback tolerates 404 on the URL. */
    private static final String USER_IMAGE_URI_TEMPLATE = "/api/v1/users/%s/image";

    private final UserBusiConnInfmRepository userBusiRepo;

    /**
     * Lookup the tenant's active users for the picker. Tenant scope
     * comes from {@link TenantContext#currentBizKey()}; the optional
     * {@code q} argument is a case-insensitive substring match
     * against {@code USER_INFM.USER_NM}.
     */
    @Transactional(readOnly = true)
    public TenantUserListResponse list(String q) {
        String bizKey = requireBizKey();
        String safeQ  = (q == null || q.isBlank()) ? "" : q.trim();

        List<TenantUserProjection> projections = userBusiRepo.findTenantUserPicker(bizKey, safeQ);
        List<TenantUserRow> rows = projections.stream()
                .map(UserDirectoryService::toRow)
                .toList();

        log.debug("user-directory list returned {} rows for bizKey={} q='{}'",
                rows.size(), bizKey, safeQ);
        return new TenantUserListResponse(rows.size(), rows);
    }

    private static TenantUserRow toRow(TenantUserProjection p) {
        String userImg = p.getUserImg();
        String userImgUrl = (userImg != null && !userImg.isBlank())
                ? String.format(USER_IMAGE_URI_TEMPLATE, p.getUserId())
                : null;
        return new TenantUserRow(
                p.getUserId(),
                p.getUserNm(),
                p.getPosiNm(),
                p.getDeptNm(),
                userImgUrl);
    }

    private static String requireBizKey() {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) {
            // Configuration bug — JWT filter should have populated
            // TenantContext on every authenticated request.
            throw new BusinessException("WBE9999");
        }
        return bizKey;
    }
}
