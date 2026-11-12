package com.kosign.wecms.domain.weinvoice.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.common.NotificationKey.Type;
import com.kosign.wecms.domain.weinvoice.common.NotificationReadState.Row;
import com.kosign.wecms.domain.weinvoice.dto.NotificationItem;
import com.kosign.wecms.domain.weinvoice.dto.NotificationListResponse;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository.CompanyRow;
import com.kosign.wecms.domain.weinvoice.repository.NotificationRepository;
import com.kosign.wecms.domain.weinvoice.repository.NotificationRepository.BucketRow;
import com.kosign.wecms.domain.weinvoice.repository.NotificationRepository.Scope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bell service (record weinvoice-notifications-2026-11-12.md): filters, ownership, read state, item shape,
 * paging, and what is stored on "read" / "read all".
 */
class NotificationServiceTest {

    private static final String OWNER = "lamine@gmail.com";
    private static final String MST = "1201496252";
    private static final String MST2 = "0108515678-001";

    private final NotificationRepository repo = mock(NotificationRepository.class);
    private final CompanyConnectionRepository companies = mock(CompanyConnectionRepository.class);
    private final CompanyAccessGuard guard = mock(CompanyAccessGuard.class);
    private final NotificationService service = new NotificationService(repo, companies, guard);

    @BeforeEach
    void setUp() {
        when(guard.ownerId()).thenReturn(OWNER);
        when(guard.requireCompany(MST)).thenReturn(company(MST, "Webcash"));
        when(guard.requireCompany("0101243150")).thenThrow(new BusinessException("WBE3002", "0101243150"));
        when(guard.ownedMsts()).thenReturn(List.of(MST, MST2));
        when(companies.listActive(OWNER)).thenReturn(List.of(company(MST, "Webcash"), company(MST2, "Branch")));
        when(repo.reads(OWNER)).thenReturn(List.of());
    }

    private static CompanyRow company(String mst, String name) {
        return new CompanyRow(OWNER, mst, name, null, mst, true, "CONNECTED", null, null, false, false, null, null);
    }

    private static BucketRow count(Type type, String mst, String dir, String hour, String atDtm, long n, Long fromEmail,
                                   List<String> months) {
        return new BucketRow(type, mst, dir, hour, atDtm, n, fromEmail, months, null, null, null, null);
    }

    private static BucketRow gdtError(String mst, String dtm) {
        return new BucketRow(Type.GDT_LOGIN_ERROR, mst, null, dtm, dtm, null, null, null, null, null, "ERROR",
                "Tài khoản đã bị khoá");
    }

    private static BucketRow mailboxError(String mst, String dtm, long id) {
        return new BucketRow(Type.MAILBOX_ERROR, mst, null, dtm + "." + id, dtm, null, null, null, id, "ke.toan@x.vn",
                "EXPIRED", "token revoked");
    }

    // ------------------------------------------------------------- filters / ownership

    @Test
    void defaultFilterIsTheWorkingMstAndNeedsIt() {
        assertThatThrownBy(() -> service.list(null, null, 0, 20)).isInstanceOf(BusinessException.class).hasMessage("WBE3037");
        assertThatThrownBy(() -> service.list(" ", "mst", 0, 20)).hasMessage("WBE3037");
        verify(repo, never()).buckets(anyString(), any(), any(), anyBoolean());
    }

    @Test
    void unknownFilterIsAValidationError() {
        assertThatThrownBy(() -> service.list(MST, "unread", 0, 20))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("filter");
    }

    @Test
    void mstFilterChecksOwnershipFirst() {
        assertThatThrownBy(() -> service.list("0101243150", "mst", 0, 20)).hasMessage("WBE3002");
        verify(repo, never()).buckets(anyString(), any(), any(), anyBoolean());

        when(repo.buckets(OWNER, MST, Scope.ALL_TYPES, false)).thenReturn(List.of());
        service.list(MST, "MST", 0, 20);
        verify(repo).buckets(OWNER, MST, Scope.ALL_TYPES, false);
    }

    @Test
    void allAndActionFiltersCoverEveryMstOfTheUser() {
        when(repo.buckets(eq(OWNER), isNull(), any(), eq(false))).thenReturn(List.of());
        service.list(null, "all", 0, 20);
        verify(repo).buckets(OWNER, null, Scope.ALL_TYPES, false);
        service.list(MST, "action", 0, 20);   // company_mst is not a restriction for "action"
        verify(repo).buckets(OWNER, null, Scope.ACTION_ONLY, false);
        verify(guard, never()).requireCompany(anyString());
    }

    // ------------------------------------------------------------- item shape

    @Test
    void itemsCarryTheContractFieldsPerType() {
        when(repo.buckets(OWNER, null, Scope.ALL_TYPES, false)).thenReturn(List.of(
                count(Type.NEW_INVOICES, MST, "mua", "2026111208", "20261112083005", 12, null, List.of("2026-11", "2026-10")),
                count(Type.PDF_READY, MST2, "ban", "2026111207", "20261112075959", 10, 3L, null),
                gdtError(MST2, "20261112060000"),
                mailboxError(null, "20261112050000", 7)));

        NotificationListResponse res = service.list(null, "all", 0, 20);
        List<NotificationItem> items = res.items();
        assertThat(items).extracting(NotificationItem::type)
                .containsExactly("NEW_INVOICES", "PDF_READY", "GDT_LOGIN_ERROR", "MAILBOX_ERROR");

        NotificationItem inv = items.get(0);
        assertThat(inv.id()).isEqualTo("NEW_INVOICES|1201496252|purchase|2026111208");
        assertThat(inv.companyName()).isEqualTo("Webcash");
        assertThat(inv.direction()).isEqualTo("purchase");
        assertThat(inv.count()).isEqualTo(12L);
        assertThat(inv.months()).containsExactly("2026-11", "2026-10");
        assertThat(inv.at()).isEqualTo("2026-11-12T08:30:05Z");
        assertThat(inv.action()).isFalse();
        assertThat(inv.fromProvider()).isNull();

        NotificationItem pdf = items.get(1);
        assertThat(pdf.direction()).isEqualTo("sales");
        assertThat(pdf.fromEmail()).isEqualTo(3L);
        assertThat(pdf.fromProvider()).isEqualTo(7L);
        assertThat(pdf.months()).isNull();

        NotificationItem gdt = items.get(2);
        assertThat(gdt.id()).isEqualTo("GDT_LOGIN_ERROR|0108515678-001|-|20261112060000");
        assertThat(gdt.action()).isTrue();
        assertThat(gdt.count()).isNull();
        assertThat(gdt.status()).isEqualTo("ERROR");
        assertThat(gdt.message()).isEqualTo("Tài khoản đã bị khoá");
        assertThat(gdt.companyName()).isEqualTo("Branch");

        NotificationItem mbx = items.get(3);
        assertThat(mbx.id()).isEqualTo("MAILBOX_ERROR|-|-|20261112050000.7");
        assertThat(mbx.action()).isTrue();
        assertThat(mbx.companyMst()).isNull();
        assertThat(mbx.companyName()).isNull();
        assertThat(mbx.mailboxId()).isEqualTo("7");
        assertThat(mbx.address()).isEqualTo("ke.toan@x.vn");
        assertThat(mbx.status()).isEqualTo("EXPIRED");
    }

    // ------------------------------------------------------------- read state, counts, paging

    @Test
    void readStateAndCountsFollowTheMarkers() {
        when(repo.reads(OWNER)).thenReturn(List.of(
                new Row("*|" + MST, "20261112080000"),
                new Row("XML_READY|" + MST + "|purchase|2026111209", "20261112091000")));
        when(repo.buckets(OWNER, MST, Scope.ALL_TYPES, false)).thenReturn(List.of(
                count(Type.NEW_INVOICES, MST, "mua", "2026111207", "20261112075000", 1, null, List.of()),   // before marker
                count(Type.NEW_INVOICES, MST, "mua", "2026111208", "20261112080001", 1, null, List.of()),   // after marker
                count(Type.XML_READY, MST, "mua", "2026111209", "20261112095000", 4, null, null)));         // own key

        NotificationListResponse res = service.list(MST, null, 0, 20);
        assertThat(res.totalCount()).isEqualTo(3);
        assertThat(res.unreadCount()).isEqualTo(1);
        assertThat(res.items()).extracting(NotificationItem::read).containsExactly(true, false, true);
    }

    @Test
    void pagesAreNewestFirstWithStableOrder() {
        List<BucketRow> rows = new ArrayList<>();
        for (int h = 0; h < 25; h++) {
            String hour = "20261111" + String.format("%02d", h % 24);
            rows.add(count(Type.XML_READY, MST, h < 24 ? "mua" : "ban", hour, hour + "0000", 1, null, null));
        }
        when(repo.buckets(OWNER, MST, Scope.ALL_TYPES, false)).thenReturn(rows);

        NotificationListResponse p0 = service.list(MST, "mst", 0, 0);       // size 0 → default 20
        assertThat(p0.size()).isEqualTo(20);
        assertThat(p0.items()).hasSize(20);
        assertThat(p0.items().get(0).at()).isEqualTo("2026-11-11T23:00:00Z");
        assertThat(p0.totalCount()).isEqualTo(25);
        assertThat(p0.unreadCount()).isEqualTo(25);

        NotificationListResponse p1 = service.list(MST, "mst", 1, 20);
        assertThat(p1.items()).hasSize(5);
        // equal times (hour 00, two directions): sorted by id, the same on every call
        assertThat(p1.items().get(3).id()).isEqualTo("XML_READY|1201496252|sales|2026111100");
        assertThat(p1.items().get(4).id()).isEqualTo("XML_READY|1201496252|purchase|2026111100");

        assertThat(service.list(MST, "mst", 9, 20).items()).isEmpty();
        assertThat(service.list(MST, "mst", -1, 500).size()).isEqualTo(100);
    }

    @Test
    void unreadCountUsesTheMarkerShortcutAndTheSameReadRule() {
        when(repo.reads(OWNER)).thenReturn(List.of(
                new Row("*", "20261112080000"),
                new Row("PDF_READY|" + MST + "|purchase|2026111209", "20261112091000")));
        when(repo.buckets(OWNER, null, Scope.ALL_TYPES, true)).thenReturn(List.of(
                count(Type.PDF_READY, MST, "mua", "2026111209", "20261112093000", 2, 0L, null),   // own key
                count(Type.NEW_INVOICES, MST2, "ban", "2026111209", "20261112093000", 2, null, null),
                gdtError(MST, "20261112070000"),                                                  // before "*"
                mailboxError(null, "20261112090000", 3)));

        assertThat(service.unreadCount().unreadCount()).isEqualTo(2);
        verify(repo).buckets(OWNER, null, Scope.ALL_TYPES, true);
        verify(companies, never()).listActive(anyString());
    }

    // ------------------------------------------------------------- writes

    @Test
    @SuppressWarnings("unchecked")
    void markReadStoresOnlyWellFormedIdsOfTheUsersTaxCodes() {
        ArrayList<String> ids = new ArrayList<>(List.of(
                "NEW_INVOICES|1201496252|purchase|2026111208",
                "NEW_INVOICES|1201496252|purchase|2026111208",       // duplicate
                "MAILBOX_ERROR|-|-|20261112050000.7",
                "GDT_LOGIN_ERROR|0101243150|-|20261112060000",       // another user's tax code
                "*", "*|1201496252",                                   // markers only through read-all
                "garbage"));
        ids.add(null);
        service.markRead(ids);

        ArgumentCaptor<Collection<String>> keys = ArgumentCaptor.forClass(Collection.class);
        verify(repo).markRead(eq(OWNER), keys.capture());
        assertThat(keys.getValue()).containsExactly(
                "NEW_INVOICES|1201496252|purchase|2026111208", "MAILBOX_ERROR|-|-|20261112050000.7");
    }

    @Test
    void readAllWritesTheRightMarkerAfterTheOwnershipCheck() {
        service.markAllRead(MST);
        verify(repo).markAll(OWNER, "*|" + MST);
        service.markAllRead(null);
        verify(repo).markAll(OWNER, "*");
        service.markAllRead("");
        verify(repo, org.mockito.Mockito.times(2)).markAll(OWNER, "*");

        assertThatThrownBy(() -> service.markAllRead("0101243150")).hasMessage("WBE3002");
        verify(repo, never()).markAll(OWNER, "*|0101243150");
    }
}
