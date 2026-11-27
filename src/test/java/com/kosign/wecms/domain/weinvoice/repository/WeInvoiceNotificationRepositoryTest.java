package com.kosign.wecms.domain.weinvoice.repository;

import com.kosign.wecms.domain.weinvoice.WeInvoiceTestProps;
import com.kosign.wecms.domain.weinvoice.common.NotificationKey.Type;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import com.kosign.wecms.domain.weinvoice.repository.WeInvoiceNotificationRepository.BucketRow;
import com.kosign.wecms.domain.weinvoice.repository.WeInvoiceNotificationRepository.Scope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Array;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Query contract of the bell (no database): the aggregation is per (type, MST, direction, UTC hour) in SQL,
 * scoped to the owner's active tax codes, leaves out what the user did (uploads, test data), and the mailbox
 * part is only present when V2 is installed.
 */
class WeInvoiceNotificationRepositoryTest {

    private final WeInvoiceDb db = mock(WeInvoiceDb.class);
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final AtomicLong now = new AtomicLong(1_000_000L);
    private WeInvoiceNotificationRepository repo;

    @BeforeEach
    void setUp() {
        when(db.jdbc()).thenReturn(jdbc);
        when(db.sql(anyString())).thenAnswer(inv -> inv.getArgument(0));
        repo = new WeInvoiceNotificationRepository(db, now::get);
    }

    @Test
    void allTypesAggregatePerHourInUtcAndScopeToTheOwnersActiveTaxCodes() {
        String sql = WeInvoiceNotificationRepository.bucketSql(false, Scope.ALL_TYPES, false, true);
        assertThat(sql).contains(
                "FROM {app}.winv_cmpn_infm c",
                "WHERE c.owner_id = :ownerId AND c.del_yn = 'N'",
                "now() - interval '30 days' AS since",
                "date_trunc('hour', i.first_seen_at AT TIME ZONE 'UTC'), 'YYYYMMDDHH24'",
                "date_trunc('hour', x.fetched_at AT TIME ZONE 'UTC'), 'YYYYMMDDHH24'",
                "date_trunc('hour', p.fetched_at AT TIME ZONE 'UTC'), 'YYYYMMDDHH24'",
                "max(i.first_seen_at) AS at", "max(x.fetched_at)", "max(p.fetched_at)",
                "JOIN {eng}.invoices i ON i.company_mst = o.mst",
                "JOIN {eng}.invoice_xml x ON x.invoice_id = i.id",
                "JOIN {eng}.invoice_pdf p ON p.invoice_id = i.id",
                "GROUP BY 2, 3, 4",
                "array_agg(DISTINCT to_char(i.ngay_lap, 'YYYY-MM')",
                "{app}.winv_mlbx_infm m",
                "OR m.cmpn_mst IS NULL");
        assertThat(sql.split("UNION ALL", -1)).hasSize(5);
        // one parameter set only; no literal from a request ever reaches the text
        assertThat(sql).doesNotContain(":mst").doesNotContain("[1:3]");
    }

    @Test
    void uploadsAndTestDataAreNotNotified() {
        String sql = WeInvoiceNotificationRepository.bucketSql(false, Scope.ALL_TYPES, false, false);
        assertThat(sql).contains(
                "COALESCE(i.list_raw ->> 'nguon', '') NOT IN ('upload', 'test_data')",
                "COALESCE(p.source_id, '') NOT IN ('upload', 'test_data')",
                "count(*) FILTER (WHERE p.source_id = 'email' OR p.source_id LIKE 'email:%')");
    }

    @Test
    void errorsComeFromTheConnectionTablesOfTheOwner() {
        String sql = WeInvoiceNotificationRepository.bucketSql(false, Scope.ACTION_ONLY, false, true);
        assertThat(sql).contains(
                "'GDT_LOGIN_ERROR'", "o.conn_stts IN ('ERROR', 'EXPIRED')",
                "COALESCE(c.conn_chk_dtm, c.mod_dtm, c.reg_dtm) AS err_dtm",
                "'MAILBOX_ERROR'", "m.owner_id = :ownerId AND m.del_yn = 'N'",
                "m.conn_stts IN ('ERROR', 'EXPIRED')", "COALESCE(m.mod_dtm, m.reg_dtm) AS dtm",
                "e.dtm || '.' || m.mlbx_seq");
        assertThat(sql).as("action filter never touches the engine tables")
                .doesNotContain("{eng}.invoices", "{eng}.invoice_xml", "{eng}.invoice_pdf");
    }

    @Test
    void oneMstFilterBindsTheMstAndLeavesOutMailboxesWithoutTaxCode() {
        String sql = WeInvoiceNotificationRepository.bucketSql(true, Scope.ALL_TYPES, false, true);
        assertThat(sql).contains("AND c.cmpn_mst = :mst", "m.cmpn_mst IN (SELECT o.mst FROM own o))")
                .doesNotContain("OR m.cmpn_mst IS NULL");
    }

    @Test
    void unreadPathStartsAfterTheReadAllMarkerAndSkipsMonthLists() {
        String sql = WeInvoiceNotificationRepository.bucketSql(false, Scope.ALL_TYPES, true, true);
        assertThat(sql).contains(
                "GREATEST(now() - interval '30 days'",
                "r.ntfc_key IN ('*', '*|' || c.cmpn_mst)",
                "+ interval '1 second')",
                "NULL::text[] AS months")
                .doesNotContain("array_agg");
    }

    /** A UNION takes its column names from the first branch; the mapper reads every column by name. */
    @Test
    void firstBranchOfEveryVariantNamesEveryMappedColumn() {
        List<String> columns = List.of("ntfc_type", "mst", "dir", "bucket", "at", "at_dtm", "cnt", "from_email",
                "months", "mailbox_id", "address", "status", "message");
        for (Scope scope : Scope.values()) {
            for (boolean oneMst : new boolean[]{false, true}) {
                for (boolean unreadOnly : new boolean[]{false, true}) {
                    String sql = WeInvoiceNotificationRepository.bucketSql(oneMst, scope, unreadOnly, true);
                    String body = sql.substring(sql.indexOf("\n)\n") + 3);
                    String firstBranch = body.split("UNION ALL", -1)[0];
                    String selectList = firstBranch.substring(0, firstBranch.indexOf("FROM own o"));
                    for (String col : columns) {
                        assertThat(selectList).as(scope + " oneMst=" + oneMst + " unreadOnly=" + unreadOnly)
                                .containsPattern("AS " + col + "\\b");
                    }
                }
            }
        }
    }

    @Test
    void placeholdersResolveToTheConfiguredSchemas() {
        WeInvoiceDb real = new WeInvoiceDb(WeInvoiceTestProps.disabled());
        String sql = real.sql(WeInvoiceNotificationRepository.bucketSql(false, Scope.ALL_TYPES, true, true));
        assertThat(sql).doesNotContain("{eng}", "{app}")
                .contains("public.invoices", "weinvoice.winv_cmpn_infm", "weinvoice.winv_ntfc_read", "weinvoice.winv_mlbx_infm");
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingMailboxTableMeansNoMailboxPartAndIsRecheckedLater() {
        when(jdbc.queryForObject(eq("SELECT to_regclass(:t) IS NOT NULL"), any(MapSqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(false, false, true);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(jdbc.query(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        repo.buckets("owner", null, Scope.ALL_TYPES, true);
        repo.buckets("owner", null, Scope.ALL_TYPES, true);   // within the re-check interval: cached "missing"
        assertThat(sql.getAllValues()).allSatisfy(s -> assertThat(s).doesNotContain("winv_mlbx_infm"));
        verify(jdbc, times(1)).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Boolean.class));

        now.addAndGet(WeInvoiceNotificationRepository.MAILBOX_RECHECK_MS);
        repo.buckets("owner", null, Scope.ALL_TYPES, true);   // re-checked: still missing
        now.addAndGet(WeInvoiceNotificationRepository.MAILBOX_RECHECK_MS);
        repo.buckets("owner", null, Scope.ALL_TYPES, true);   // re-checked: now installed
        repo.buckets("owner", null, Scope.ALL_TYPES, true);   // installed is remembered
        verify(jdbc, times(3)).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Boolean.class));
        assertThat(sql.getAllValues().get(3)).contains("winv_mlbx_infm");
        assertThat(sql.getAllValues().get(4)).contains("winv_mlbx_infm");
    }

    @Test
    @SuppressWarnings("unchecked")
    void writesUseTheDatabaseClockAndPruneOnlyOldIdsOfTheOwner() {
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(db.tx()).thenReturn(tx);
        doAnswer(inv -> {
            ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(null);
            return null;
        }).when(tx).executeWithoutResult(any());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        when(jdbc.update(sql.capture(), params.capture())).thenReturn(1);
        ArgumentCaptor<String> batchSql = ArgumentCaptor.forClass(String.class);
        when(jdbc.batchUpdate(batchSql.capture(), any(SqlParameterSource[].class))).thenReturn(new int[]{1});

        repo.markAll("owner", "*|1201496252");
        assertThat(sql.getAllValues().get(0)).contains("INSERT INTO {app}.winv_ntfc_read",
                "to_char(now() AT TIME ZONE 'UTC', 'YYYYMMDDHH24MISS')",
                "ON CONFLICT (owner_id, ntfc_key) DO UPDATE SET read_dtm = EXCLUDED.read_dtm");
        assertThat(params.getAllValues().get(0).getValue("key")).isEqualTo("*|1201496252");
        assertThat(sql.getAllValues().get(1)).contains("DELETE FROM {app}.winv_ntfc_read", "WHERE owner_id = :ownerId",
                "ntfc_key NOT LIKE '*%'", "interval '31 days'");

        repo.markRead("owner", List.of("NEW_INVOICES|1201496252|purchase|2026111208"));
        assertThat(batchSql.getValue()).contains("ON CONFLICT (owner_id, ntfc_key) DO NOTHING");
        assertThat(sql.getAllValues().get(2)).isEqualTo(WeInvoiceNotificationRepository.PRUNE_SQL);

        repo.markRead("owner", List.of());   // nothing to store → no database call
        verify(tx, times(2)).executeWithoutResult(any());
    }

    @Test
    void mapperConvertsTheEventTimeToUtcAndKeepsTheThreeNewestMonths() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        Array months = mock(Array.class);
        when(months.getArray()).thenReturn(new String[]{"2026-11", "2026-10", "2026-09", "2026-08"});
        when(rs.getObject("at", OffsetDateTime.class))
                .thenReturn(OffsetDateTime.of(2026, 11, 12, 15, 30, 5, 700_000_000, ZoneOffset.ofHours(7)));
        when(rs.getArray("months")).thenReturn(months);
        when(rs.getString("ntfc_type")).thenReturn("NEW_INVOICES");
        when(rs.getString("mst")).thenReturn("1201496252");
        when(rs.getString("dir")).thenReturn("mua");
        when(rs.getString("bucket")).thenReturn("2026111208");
        when(rs.getLong("cnt")).thenReturn(12L);

        BucketRow row = WeInvoiceNotificationRepository.MAPPER.mapRow(rs, 0);
        assertThat(row.atDtm()).isEqualTo("20261112083005");
        assertThat(row.months()).containsExactly("2026-11", "2026-10", "2026-09");
        assertThat(row.type()).isEqualTo(Type.NEW_INVOICES);
        assertThat(row.key().id()).isEqualTo("NEW_INVOICES|1201496252|purchase|2026111208");
    }

    @Test
    void mapperTakesTheStoredDtmForErrorRows() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("ntfc_type")).thenReturn("MAILBOX_ERROR");
        when(rs.getString("at_dtm")).thenReturn("20261112083015");
        when(rs.getString("bucket")).thenReturn("20261112083015.7");
        when(rs.getLong("mailbox_id")).thenReturn(7L);

        BucketRow row = WeInvoiceNotificationRepository.MAPPER.mapRow(rs, 0);
        assertThat(row.atDtm()).isEqualTo("20261112083015");
        assertThat(row.months()).isNull();
        assertThat(row.key().id()).isEqualTo("MAILBOX_ERROR|-|-|20261112083015.7");
    }
}
