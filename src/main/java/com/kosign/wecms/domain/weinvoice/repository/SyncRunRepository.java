package com.kosign.wecms.domain.weinvoice.repository;

import com.kosign.wecms.domain.weinvoice.common.WeInvoiceTime;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Sync history (screen 10): the tenant's {@code SYNC} requests plus the engine's own
 * scheduled list runs ({@code job_run_companies}) for the tenant's MSTs. An engine run that
 * served a request ({@code winv_engn_rqst.job_run_id}) is shown once, as the request.
 */
@Repository
public class SyncRunRepository {

    private static final String HISTORY = """
            SELECT * FROM (
                SELECT r.rqst_seq AS rqst_id, NULL::bigint AS run_id, r.cmpn_mst AS mst, r.trgr_cd AS trgr,
                       r.rqst_prmt::text AS prmt, r.rqst_stts AS stts, r.rslt_data::text AS rslt,
                       r.err_msg, r.reg_dtm, r.strt_dtm, r.end_dtm,
                       (to_timestamp(COALESCE(r.strt_dtm, r.reg_dtm), 'YYYYMMDDHH24MISS')::timestamp AT TIME ZONE 'UTC') AS sort_at,
                       NULL::timestamptz AS started_at, NULL::timestamptz AS finished_at
                  FROM {app}.winv_engn_rqst r
                 WHERE r.biz_key = :bizKey AND r.rqst_type = 'SYNC' AND r.cmpn_mst IN (:msts)
                UNION ALL
                SELECT NULL, j.run_id, j.company_mst, 'SCHEDULE', NULL, COALESCE(j.status, '_running_'), j.stats::text,
                       NULL, NULL, NULL, NULL, j.started_at, j.started_at, j.finished_at
                  FROM {eng}.job_run_companies j
                  JOIN {eng}.job_runs jr ON jr.id = j.run_id
                 WHERE j.company_mst IN (:msts)
                   AND jr.command IN ('tick', 'sync-lists', 'sweep', 'manual', 'xml')
                   AND NOT EXISTS (SELECT 1 FROM {app}.winv_engn_rqst r2 WHERE r2.job_run_id = j.run_id)
            ) h
            """;

    private final WeInvoiceDb db;

    public SyncRunRepository(WeInvoiceDb db) {
        this.db = db;
    }

    /**
     * @param rawStatuses stored status values to keep ({@code DONE}, {@code ok}, …); empty = all
     */
    public List<HistoryRow> history(String bizKey, List<String> msts, List<String> rawStatuses, int page, int size) {
        MapSqlParameterSource p = params(bizKey, msts);
        String where = where(rawStatuses, p);
        p.addValue("limit", size).addValue("offset", (long) page * size);
        return db.jdbc().query(db.sql(HISTORY + where + " ORDER BY h.sort_at DESC NULLS LAST LIMIT :limit OFFSET :offset"),
                p,
                (rs, n) -> new HistoryRow(
                        Rows.longOrNull(rs, "rqst_id"),
                        Rows.longOrNull(rs, "run_id"),
                        rs.getString("mst"),
                        rs.getString("trgr"),
                        rs.getString("prmt"),
                        rs.getString("stts"),
                        rs.getString("rslt"),
                        rs.getString("err_msg"),
                        rs.getString("reg_dtm"),
                        rs.getString("strt_dtm"),
                        rs.getString("end_dtm"),
                        WeInvoiceTime.iso(rs, "started_at"),
                        WeInvoiceTime.iso(rs, "finished_at")));
    }

    public long count(String bizKey, List<String> msts, List<String> rawStatuses) {
        MapSqlParameterSource p = params(bizKey, msts);
        Long n = db.jdbc().queryForObject(db.sql("SELECT count(*) FROM (" + HISTORY + where(rawStatuses, p) + ") c"),
                p, Long.class);
        return n == null ? 0L : n;
    }

    private static MapSqlParameterSource params(String bizKey, List<String> msts) {
        return new MapSqlParameterSource("bizKey", bizKey).addValue("msts", msts.isEmpty() ? List.of("") : msts);
    }

    private static String where(List<String> rawStatuses, MapSqlParameterSource p) {
        if (rawStatuses == null || rawStatuses.isEmpty()) return "";
        p.addValue("stts", rawStatuses);
        return " WHERE h.stts IN (:stts)";
    }

    /**
     * One history line. Requests carry {@code requestId} and the {@code *Dtm} columns; engine
     * runs carry {@code runId} and the ISO {@code startedAt} / {@code finishedAt}.
     */
    public record HistoryRow(Long requestId, Long runId, String mst, String trigger, String paramsJson,
                             String rawStatus, String resultJson, String error, String regDtm, String startDtm,
                             String endDtm, String startedAt, String finishedAt) {
    }
}
