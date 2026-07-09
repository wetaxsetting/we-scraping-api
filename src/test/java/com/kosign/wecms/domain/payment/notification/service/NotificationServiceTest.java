package com.kosign.wecms.domain.payment.notification.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.notification.dto.NotificationListResponse;
import com.kosign.wecms.domain.payment.notification.dto.NotificationProjection;
import com.kosign.wecms.domain.payment.notification.dto.NotificationRow;
import com.kosign.wecms.domain.payment.notification.dto.UnreadCountResponse;
import com.kosign.wecms.domain.payment.notification.repository.BltbNotiInfmRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service-layer coverage for the Notifications popup (legacy
 * {@code comm_0192_007} BLTB-branch port):
 * <ol>
 *   <li>list unread ({@code read_type=U} ⇒ {@code ACTV_YN='N'} filter)</li>
 *   <li>list all ({@code read_type=A} ⇒ no actv_yn filter)</li>
 *   <li>search by sender name (keyword lowercased + escaped + wrapped)</li>
 *   <li>unread count</li>
 *   <li>mark one read (idempotent; not-found envelope)</li>
 *   <li>mark all read</li>
 *   <li>{@code DEL_YN='Y'} row is still returned (not filtered)</li>
 *   <li>tenant / recipient isolation (queries always carry
 *       TenantContext bizKey + userId, never request input)</li>
 *   <li>empty result ⇒ success with empty list</li>
 *   <li>a row shaped exactly as {@code ApprovalNotificationService} writes
 *       it round-trips through the read mapper with all fields populated</li>
 * </ol>
 *
 * <p>Mirrors the Mockito-only style of
 * {@link com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryService}
 * tests — the repository is mocked; the JPQL itself is exercised by the
 * controller/integration layer and live SIT (no {@code @DataJpaTest}
 * infra in this project per {@code wecms-api/CLAUDE.md}).
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    BltbNotiInfmRepository repo;

    private NotificationService service;

    private static final String BIZ  = "VN_VNM0004_000001";
    private static final String USER = "approver1@example.com";
    private static final String BLTB = "000000000000000";

    // Fixed clock so mark-read / mark-all-read stamp a deterministic MOD_DTM.
    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-06-14T03:04:05Z"), ZoneOffset.UTC);
    private static final String FIXED_DTM = "20260614030405";

    @BeforeEach
    void setUp() {
        service = new NotificationService(repo, FIXED);
        TenantContext.set(USER, BIZ, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* =================================================================
     *  1. List unread
     * ================================================================= */

    @Test
    void list_unread_appliesActvYnFilterAndScopesToTenantUserLang() {
        when(repo.search(eq(BIZ), eq(USER), eq("EN"), eq("N"), isNull(), any(Pageable.class)))
                .thenReturn(emptyPage());
        when(repo.countUnread(BIZ, USER, "EN")).thenReturn(0L);

        service.list("U", null, 0, 0);

        verify(repo).search(eq(BIZ), eq(USER), eq("EN"), eq("N"), isNull(), any(Pageable.class));
    }

    @Test
    void list_unread_isCaseInsensitive() {
        when(repo.search(eq(BIZ), eq(USER), eq("EN"), eq("N"), isNull(), any(Pageable.class)))
                .thenReturn(emptyPage());
        when(repo.countUnread(BIZ, USER, "EN")).thenReturn(0L);

        service.list("u", null, 0, 0);

        verify(repo).search(eq(BIZ), eq(USER), eq("EN"), eq("N"), isNull(), any(Pageable.class));
    }

    /* =================================================================
     *  2. List all
     * ================================================================= */

    @Test
    void list_all_passesNullActvYnFilter() {
        when(repo.search(eq(BIZ), eq(USER), eq("EN"), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(emptyPage());
        when(repo.countUnread(BIZ, USER, "EN")).thenReturn(3L);

        NotificationListResponse res = service.list("A", null, 0, 0);

        verify(repo).search(eq(BIZ), eq(USER), eq("EN"), isNull(), isNull(), any(Pageable.class));
        // Unread count is independent of the tab filter.
        assertThat(res.unreadCount()).isEqualTo(3L);
    }

    @Test
    void list_defaultSort_isRegDtmDescThenNotiSeqNoDesc() {
        when(repo.search(eq(BIZ), eq(USER), eq("EN"), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(emptyPage());
        when(repo.countUnread(BIZ, USER, "EN")).thenReturn(0L);

        service.list("A", null, 0, 0);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).search(eq(BIZ), eq(USER), eq("EN"), isNull(), isNull(), captor.capture());
        List<Sort.Order> orders = captor.getValue().getSort().toList();
        assertThat(orders).extracting(Sort.Order::getProperty).containsExactly("regDtm", "notiSeqNo");
        assertThat(orders.get(0).getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(orders.get(1).getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void list_pagination_defaultsAndClamps() {
        when(repo.search(eq(BIZ), eq(USER), eq("EN"), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(emptyPage());
        when(repo.countUnread(BIZ, USER, "EN")).thenReturn(0L);

        // size<=0 -> default 20; negative page -> 0
        service.list("A", null, -3, 0);
        ArgumentCaptor<Pageable> c1 = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).search(eq(BIZ), eq(USER), eq("EN"), isNull(), isNull(), c1.capture());
        assertThat(c1.getValue().getPageNumber()).isEqualTo(0);
        assertThat(c1.getValue().getPageSize()).isEqualTo(NotificationService.DEFAULT_PAGE_SIZE);

        // oversized size -> clamped to MAX
        service.list("A", null, 2, 9999);
        ArgumentCaptor<Pageable> c2 = ArgumentCaptor.forClass(Pageable.class);
        verify(repo, org.mockito.Mockito.times(2))
                .search(eq(BIZ), eq(USER), eq("EN"), isNull(), isNull(), c2.capture());
        assertThat(c2.getValue().getPageSize()).isEqualTo(NotificationService.MAX_PAGE_SIZE);
    }

    /* =================================================================
     *  3. Search by sender name
     * ================================================================= */

    @Test
    void list_keyword_isLowercasedWrappedAndEscaped() {
        when(repo.search(eq(BIZ), eq(USER), eq("EN"), isNull(), any(), any(Pageable.class)))
                .thenReturn(emptyPage());
        when(repo.countUnread(BIZ, USER, "EN")).thenReturn(0L);

        service.list("A", "Sok", 0, 0);
        verify(repo).search(eq(BIZ), eq(USER), eq("EN"), isNull(), eq("%sok%"), any(Pageable.class));

        service.list("A", "100%_x!", 0, 0);
        verify(repo).search(eq(BIZ), eq(USER), eq("EN"), isNull(), eq("%100!%!_x!!%"), any(Pageable.class));
    }

    @Test
    void list_blankKeyword_passesNullPattern() {
        when(repo.search(eq(BIZ), eq(USER), eq("EN"), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(emptyPage());
        when(repo.countUnread(BIZ, USER, "EN")).thenReturn(0L);

        service.list("A", "   ", 0, 0);
        verify(repo).search(eq(BIZ), eq(USER), eq("EN"), isNull(), isNull(), any(Pageable.class));
    }

    /* =================================================================
     *  4. Unread count
     * ================================================================= */

    @Test
    void unreadCount_scopedToTenantUserLang() {
        when(repo.countUnread(BIZ, USER, "EN")).thenReturn(7L);
        UnreadCountResponse res = service.unreadCount();
        assertThat(res.totalCount()).isEqualTo(7L);
        verify(repo).countUnread(BIZ, USER, "EN");
    }

    /* =================================================================
     *  5. Mark one read — idempotent + not-found envelope
     * ================================================================= */

    @Test
    void markRead_updatesRowScopedToTenantUserWithModAudit() {
        when(repo.markRead(eq(BIZ), eq(USER), eq(BigDecimal.valueOf(759L)), eq(USER), eq(FIXED_DTM)))
                .thenReturn(1);

        service.markRead(759L);

        verify(repo).markRead(BIZ, USER, BigDecimal.valueOf(759L), USER, FIXED_DTM);
    }

    @Test
    void markRead_unknownOrOtherTenantRow_throwsNotFound() {
        when(repo.markRead(eq(BIZ), eq(USER), any(), eq(USER), any())).thenReturn(0);

        assertThatThrownBy(() -> service.markRead(9999L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("WBE_NOTIFICATION_NOT_FOUND");
    }

    /* =================================================================
     *  6. Mark all read
     * ================================================================= */

    @Test
    void markAllRead_updatesAllUnreadForTenantUser_successEvenWhenZero() {
        when(repo.markAllRead(BIZ, USER, USER, FIXED_DTM)).thenReturn(0);
        // No throw even when nothing was unread.
        service.markAllRead();
        verify(repo).markAllRead(BIZ, USER, USER, FIXED_DTM);
    }

    /* =================================================================
     *  7. Deleted row still returned
     * ================================================================= */

    @Test
    void list_deletedRow_isStillReturnedAndFlagged() {
        NotificationProjection deleted = projection(500L, "N", "Y",  // actvYn=N (unread), delYn=Y
                "Added a new approval.", "");
        when(repo.search(eq(BIZ), eq(USER), eq("EN"), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(deleted), PageRequest.of(0, 20), 1L));
        when(repo.countUnread(BIZ, USER, "EN")).thenReturn(1L);

        NotificationListResponse res = service.list("A", null, 0, 0);

        assertThat(res.list()).hasSize(1);
        NotificationRow row = res.list().get(0);
        assertThat(row.delYn()).isEqualTo("Y");
        assertThat(row.read()).isFalse();           // ACTV_YN='N'
        // Service does NOT drop deleted rows — frontend shows WBI1098 on click.
    }

    /* =================================================================
     *  8. Tenant / recipient isolation
     * ================================================================= */

    @Test
    void list_alwaysUsesTenantContextBizKeyAndUser_neverRequestInput() {
        TenantContext.set("other.user@example.com", "VN_OTHER_TENANT", LangCd.KO);
        when(repo.search(eq("VN_OTHER_TENANT"), eq("other.user@example.com"), eq("KO"),
                isNull(), isNull(), any(Pageable.class)))
                .thenReturn(emptyPage());
        when(repo.countUnread("VN_OTHER_TENANT", "other.user@example.com", "KO")).thenReturn(0L);

        service.list("A", null, 0, 0);

        verify(repo).search(eq("VN_OTHER_TENANT"), eq("other.user@example.com"), eq("KO"),
                isNull(), isNull(), any(Pageable.class));
        // The list call never touches the original BIZ/USER fixture.
        verify(repo, never()).search(eq(BIZ), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void markRead_scopesToTenantContext() {
        TenantContext.set("other.user@example.com", "VN_OTHER_TENANT", LangCd.VI);
        when(repo.markRead(eq("VN_OTHER_TENANT"), eq("other.user@example.com"),
                eq(BigDecimal.valueOf(1L)), eq("other.user@example.com"), any()))
                .thenReturn(1);

        service.markRead(1L);

        verify(repo).markRead(eq("VN_OTHER_TENANT"), eq("other.user@example.com"),
                eq(BigDecimal.valueOf(1L)), eq("other.user@example.com"), eq(FIXED_DTM));
    }

    /* =================================================================
     *  9. Empty result
     * ================================================================= */

    @Test
    void list_empty_returnsSuccessWithEmptyList() {
        when(repo.search(eq(BIZ), eq(USER), eq("EN"), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(emptyPage());
        when(repo.countUnread(BIZ, USER, "EN")).thenReturn(0L);

        NotificationListResponse res = service.list("A", null, 0, 0);

        assertThat(res.list()).isEmpty();
        assertThat(res.totalCount()).isEqualTo(0L);
        assertThat(res.unreadCount()).isEqualTo(0L);
        assertThat(res.pageNumber()).isEqualTo(0);
    }

    /* =================================================================
     *  10. Round-trip with ApprovalNotificationService's written shape
     * ================================================================= */

    @Test
    void list_readsBackTheExactShapeApprovalNotificationServiceWrites() {
        // Build a projection mirroring the row ApprovalNotificationService
        // persists (unread, BLTB_CD_APPR, NOTI_WSVC, EN title, DEL_YN='N'),
        // referencing the writer's own package-private constants so the
        // read contract stays pinned to the write contract.
        NotificationProjection p = new NotificationProjection(
                ApprovalNotificationService.BLTB_CD_APPR,
                BigDecimal.valueOf(42L),
                ApprovalNotificationService.NOTI_TITLE,   // "Added a new approval."
                "",
                "requestor@example.com",
                "Requestor Name",
                "{\"POST_SEQ_NO\":7,\"BLTB_CD\":\"" + ApprovalNotificationService.BLTB_CD_APPR + "\"}",
                ApprovalNotificationService.NOTI_WSVC,    // "comm_0193_001.act"
                "N",                                       // del_yn
                "N",                                       // actv_yn (unread)
                "20260614030405",
                "20260614030405",
                "uploads/avatar.png");                     // sender has an image

        when(repo.search(eq(BIZ), eq(USER), eq("EN"), eq("N"), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p), PageRequest.of(0, 20), 1L));
        when(repo.countUnread(BIZ, USER, "EN")).thenReturn(1L);

        NotificationRow row = service.list("U", null, 0, 0).list().get(0);

        assertThat(row.notificationId()).isEqualTo(42L);
        assertThat(row.notiSeqNo()).isEqualTo(42L);
        assertThat(row.bltbCd()).isEqualTo(ApprovalNotificationService.BLTB_CD_APPR);
        assertThat(row.title()).isEqualTo("Added a new approval.");
        assertThat(row.content()).isEmpty();
        assertThat(row.sndId()).isEqualTo("requestor@example.com");
        assertThat(row.sndName()).isEqualTo("Requestor Name");
        assertThat(row.notiWsvc()).isEqualTo(ApprovalNotificationService.NOTI_WSVC);
        assertThat(row.notiJson()).contains("POST_SEQ_NO");
        assertThat(row.delYn()).isEqualTo("N");
        assertThat(row.read()).isFalse();
        assertThat(row.actvYn()).isEqualTo("N");
        // Sender image → slug URL (approval-line precedent).
        assertThat(row.userImgUrl()).isEqualTo("/api/v1/users/requestor@example.com/image");
    }

    @Test
    void rowMapping_blankSenderImage_resolvesUserImgUrlToNull() {
        NotificationProjection p = new NotificationProjection(
                BLTB, BigDecimal.valueOf(1L), "t", "", "sys", "SYSTEM",
                null, "comm_0193_001.act", "N", "Y", "20260614030405", "20260614030405",
                "   ");   // blank image
        when(repo.search(eq(BIZ), eq(USER), eq("EN"), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p), PageRequest.of(0, 20), 1L));
        when(repo.countUnread(BIZ, USER, "EN")).thenReturn(0L);

        NotificationRow row = service.list("A", null, 0, 0).list().get(0);
        assertThat(row.userImgUrl()).isNull();
        assertThat(row.read()).isTrue();   // ACTV_YN='Y'
    }

    /* =================================================================
     *  helpers
     * ================================================================= */

    private static Page<NotificationProjection> emptyPage() {
        return new PageImpl<>(List.of(), PageRequest.of(0, 20), 0L);
    }

    private static NotificationProjection projection(
            long seqNo, String actvYn, String delYn, String titl, String cont) {
        return new NotificationProjection(
                BLTB, BigDecimal.valueOf(seqNo), titl, cont,
                "snd@example.com", "Sender Name",
                "{}", "comm_0193_001.act", delYn, actvYn,
                "20260614030405", "20260614030405", null);
    }
}
