package com.kosign.wecms.common.auditlog.service;

import com.kosign.wecms.common.auditlog.entity.AuditLogEntry;
import com.kosign.wecms.common.auditlog.repository.AuditLogEntryRepository;
import com.kosign.wecms.common.auditlog.repository.AuditLogSeqNoRepository;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link AuditLogService}. Pins:
 * <ul>
 *   <li>locale fan-out: one row per supported locale (EN/KO/VI/KM),
 *       all sharing one allocated seq_no;</li>
 *   <li>each row carries the locale-resolved
 *       {@code AUDIT_LOGS_ACTV} / {@code AUDIT_LOGS_ACTV_DTL} string
 *       (verified by inspecting the saved entries' fields);</li>
 *   <li>{@code reg_id} / {@code reg_nm} / {@code biz_key} pulled from
 *       {@link TenantContext}; missing context → swallowed with
 *       caller unaffected;</li>
 *   <li>repository failures during write are swallowed (the caller's
 *       business write must not be disturbed by an audit failure);
 *   </li>
 *   <li>missing required {@link AuditLogContext} fields are swallowed
 *       too (defence — same intent as repository failures).</li>
 * </ul>
 *
 * <p>Transactional behaviour ({@code REQUIRES_NEW}, rollback isolation)
 * lives in {@link AuditLogServiceIntegrationTest} — Mockito unit
 * tests cannot exercise Spring's tx aspect.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock AuditLogEntryRepository  entryRepository;
    @Mock AuditLogSeqNoRepository  seqNoRepository;
    @Mock MessageResolver          messages;

    AuditLogService service;

    private static final String BIZ      = "VN_VNM0004_000001";
    private static final String USER_ID  = "demo_user";
    private static final String USER_NM  = "Demo User";

    @BeforeEach
    void setUp() {
        service = new AuditLogService(entryRepository, seqNoRepository, messages);
        TenantContext.set(USER_ID, USER_NM, BIZ, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------
    // Happy path — multi-language fan-out
    // ------------------------------------------------------------------

    @Test
    void append_writesOneRowPerSupportedLocale_allSharingOneSeqNo() {
        when(seqNoRepository.allocateNextSeqNo(BIZ, "TB_FIRM_CONF", "970424"))
                .thenReturn(BigDecimal.valueOf(344));
        // MessageResolver returns a deterministic per-locale string so
        // we can assert the row mapping. ANY_OBJECT_ARRAY tolerates
        // both null and empty msgArgs.
        when(messages.resolve(eq("audit.firm_banking_settings.activity"),
                any(Locale.class), any(Object[].class)))
                .thenAnswer(inv -> "actv-" + inv.getArgument(1));
        when(messages.resolve(eq("audit.firm_banking_settings.update.detail"),
                any(Locale.class), any(Object[].class)))
                .thenAnswer(inv -> "detl-" + inv.getArgument(1));

        service.append(AuditLogContext.builder()
                .wsvcId("firm-banking-settings")
                .refNo("TB_FIRM_CONF")
                .refNo1("970424")
                .refNo2("700005285280")
                .activityKey("audit.firm_banking_settings.activity")
                .detailKey("audit.firm_banking_settings.update.detail")
                .build());

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(entryRepository, times(4)).save(captor.capture());
        List<AuditLogEntry> saved = captor.getAllValues();
        assertThat(saved).hasSize(4);

        // Every row shares the SAME seq_no — one event, four rows.
        // This is the JEX-parity invariant verified DEV-side
        // 2026-05-09 (audit_logs_seq_no=343 across KO/VI/EN for one
        // event). The refactor extends to KM but keeps the seq_no
        // sharing.
        assertThat(saved).extracting(AuditLogEntry::getAuditLogsSeqNo)
                .containsOnly(BigDecimal.valueOf(344));

        // Each row carries a distinct lang_cd. Order matches
        // SUPPORTED_LOCALES (EN, KO, VI, KM).
        assertThat(saved).extracting(AuditLogEntry::getLangCd)
                .containsExactly("EN", "KO", "VI", "KM");

        // Activity / detail strings are locale-resolved per row.
        assertThat(saved.get(0).getAuditLogsActv()).isEqualTo("actv-en");
        assertThat(saved.get(1).getAuditLogsActv()).isEqualTo("actv-ko");
        assertThat(saved.get(2).getAuditLogsActv()).isEqualTo("actv-vi");
        assertThat(saved.get(3).getAuditLogsActv()).isEqualTo("actv-km");
        assertThat(saved.get(0).getAuditLogsActvDtl()).isEqualTo("detl-en");
        assertThat(saved.get(3).getAuditLogsActvDtl()).isEqualTo("detl-km");

        // Tenant scoping pulled from TenantContext.
        assertThat(saved).allSatisfy(e -> {
            assertThat(e.getBizKey()).isEqualTo(BIZ);
            assertThat(e.getRegId()) .isEqualTo(USER_ID);
            assertThat(e.getRegNm()) .isEqualTo(USER_NM);
            assertThat(e.getWsvcId()).isEqualTo("firm-banking-settings");
            assertThat(e.getRefNo()) .isEqualTo("TB_FIRM_CONF");
            assertThat(e.getRefNo1()).isEqualTo("970424");
            assertThat(e.getRefNo2()).isEqualTo("700005285280");
            assertThat(e.getRegDtm()).hasSize(14);   // yyyyMMddHHmmss
        });

        // Allocator called exactly once per append (not once per locale).
        verify(seqNoRepository, times(1))
                .allocateNextSeqNo(BIZ, "TB_FIRM_CONF", "970424");
    }

    @Test
    void append_blankActivityKey_leavesActvColumnNull() {
        // Some events only need a detail string (e.g. the future
        // "Delete" event if OQ-2 ever flips). The activity column is
        // nullable — passing a blank/null activityKey skips the
        // resolution and leaves the column null.
        when(seqNoRepository.allocateNextSeqNo(any(), any(), any()))
                .thenReturn(BigDecimal.ONE);
        when(messages.resolve(eq("detail.key"), any(Locale.class), any(Object[].class)))
                .thenReturn("only detail");

        service.append(AuditLogContext.builder()
                .wsvcId("firm-banking-settings")
                .refNo("TB_FIRM_CONF")
                .refNo1("970424")
                .activityKey("")          // blank → activity column stays null
                .detailKey("detail.key")
                .build());

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(entryRepository, times(4)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(AuditLogEntry::getAuditLogsActv)
                .containsOnlyNulls();
        assertThat(captor.getAllValues()).extracting(AuditLogEntry::getAuditLogsActvDtl)
                .containsOnly("only detail");
    }

    // ------------------------------------------------------------------
    // Failure isolation — caller's business write must not be disturbed
    // ------------------------------------------------------------------

    @Test
    void append_repositoryThrows_swallowed_noPropagation() {
        when(seqNoRepository.allocateNextSeqNo(any(), any(), any()))
                .thenReturn(BigDecimal.ONE);
        when(messages.resolve(any(), any(Locale.class), any(Object[].class)))
                .thenReturn("any");
        // First save throws — the catch-block must absorb it without
        // bubbling the exception to the caller.
        when(entryRepository.save(any()))
                .thenThrow(new RuntimeException("simulated DB failure"));

        // Calling append must NOT throw — the test passes simply by
        // returning normally.
        service.append(AuditLogContext.builder()
                .wsvcId("firm-banking-settings")
                .refNo("TB_FIRM_CONF")
                .refNo1("970424")
                .activityKey("a")
                .detailKey("d")
                .build());

        // Behaviour observable to the caller: the exception was
        // swallowed (no rethrow). The fact that this assertion is
        // reached at all is the proof.
        assertThat(true).isTrue();
    }

    @Test
    void append_seqNoAllocatorThrows_swallowed() {
        when(seqNoRepository.allocateNextSeqNo(any(), any(), any()))
                .thenThrow(new RuntimeException("seq_no race lost"));

        service.append(AuditLogContext.builder()
                .wsvcId("firm-banking-settings")
                .refNo("TB_FIRM_CONF")
                .refNo1("970424")
                .detailKey("d")
                .build());

        // No save should be attempted when allocator fails.
        verify(entryRepository, never()).save(any());
    }

    @Test
    void append_seqNoAllocatorReturnsNull_swallowed() {
        when(seqNoRepository.allocateNextSeqNo(any(), any(), any()))
                .thenReturn(null);

        service.append(AuditLogContext.builder()
                .wsvcId("firm-banking-settings")
                .refNo("TB_FIRM_CONF")
                .refNo1("970424")
                .detailKey("d")
                .build());

        verify(entryRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Validation — required-field checks (swallowed too — append never throws)
    // ------------------------------------------------------------------

    @Test
    void append_nullContext_swallowed() {
        service.append(null);
        verify(seqNoRepository, never()).allocateNextSeqNo(any(), any(), any());
        verify(entryRepository, never()).save(any());
    }

    @Test
    void append_blankWsvcId_swallowed() {
        service.append(AuditLogContext.builder()
                .wsvcId("")
                .refNo("TB_FIRM_CONF")
                .refNo1("970424")
                .detailKey("d")
                .build());
        verify(seqNoRepository, never()).allocateNextSeqNo(any(), any(), any());
        verify(entryRepository, never()).save(any());
    }

    @Test
    void append_blankRefNo1_swallowed() {
        service.append(AuditLogContext.builder()
                .wsvcId("firm-banking-settings")
                .refNo("TB_FIRM_CONF")
                .refNo1("")
                .detailKey("d")
                .build());
        verify(seqNoRepository, never()).allocateNextSeqNo(any(), any(), any());
        verify(entryRepository, never()).save(any());
    }

    @Test
    void append_blankDetailKey_swallowed() {
        // detailKey is the only key whose absence is fatal — it maps
        // to the NOT-NULL audit_logs_actv_dtl column.
        service.append(AuditLogContext.builder()
                .wsvcId("firm-banking-settings")
                .refNo("TB_FIRM_CONF")
                .refNo1("970424")
                .detailKey("")
                .build());
        verify(seqNoRepository, never()).allocateNextSeqNo(any(), any(), any());
        verify(entryRepository, never()).save(any());
    }

    @Test
    void append_emptyTenantContext_swallowed() {
        TenantContext.clear();

        service.append(AuditLogContext.builder()
                .wsvcId("firm-banking-settings")
                .refNo("TB_FIRM_CONF")
                .refNo1("970424")
                .detailKey("d")
                .build());

        verify(seqNoRepository, never()).allocateNextSeqNo(any(), any(), any());
        verify(entryRepository, never()).save(any());
    }
}
