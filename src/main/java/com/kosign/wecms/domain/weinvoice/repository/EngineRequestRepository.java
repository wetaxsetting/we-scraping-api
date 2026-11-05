package com.kosign.wecms.domain.weinvoice.repository;

import com.kosign.wecms.domain.weinvoice.common.WeInvoiceTime;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code winv_engn_rqst} — the backend → engine request queue. The backend inserts
 * {@code QUEUED} rows and may cancel them; status, step, progress and result belong to the
 * engine worker that claims the row.
 */
@Repository
public class EngineRequestRepository {

    private static final String SELECT = """
            SELECT rqst_seq, owner_id, cmpn_mst, rqst_type, trgr_cd, rqst_prmt::text AS prmt,
                   rqst_stts, step_cd, prgs_data::text AS prgs, rslt_data::text AS rslt, err_msg,
                   cncl_yn, job_run_id, strt_dtm, end_dtm, reg_id, reg_dtm
              FROM {app}.winv_engn_rqst
            """;

    private static final RowMapper<RequestRow> MAPPER = (rs, n) -> new RequestRow(
            rs.getLong("rqst_seq"),
            rs.getString("owner_id"),
            rs.getString("cmpn_mst"),
            rs.getString("rqst_type"),
            rs.getString("trgr_cd"),
            rs.getString("prmt"),
            rs.getString("rqst_stts"),
            rs.getString("step_cd"),
            rs.getString("prgs"),
            rs.getString("rslt"),
            rs.getString("err_msg"),
            "Y".equals(rs.getString("cncl_yn")),
            Rows.longOrNull(rs, "job_run_id"),
            rs.getString("strt_dtm"),
            rs.getString("end_dtm"),
            rs.getString("reg_id"),
            rs.getString("reg_dtm"));

    private final WeInvoiceDb db;

    public EngineRequestRepository(WeInvoiceDb db) {
        this.db = db;
    }

    /** Queues a request and returns its id. {@code paramsJson} is a JSON object. */
    public long insert(String ownerId, String mst, String type, String trigger, String paramsJson, String userId) {
        String dtm = WeInvoiceTime.nowDtm();
        Long id = db.jdbc().queryForObject(db.sql("""
                INSERT INTO {app}.winv_engn_rqst
                       (rqst_seq, owner_id, cmpn_mst, rqst_type, trgr_cd, rqst_prmt, rqst_stts,
                        cncl_yn, reg_id, reg_dtm, mod_id, mod_dtm)
                VALUES (nextval('{app}.sq_winv_rqst_seq'), :ownerId, :mst, :type, :trigger, CAST(:prmt AS jsonb), 'QUEUED',
                        'N', :userId, :dtm, :userId, :dtm)
                RETURNING rqst_seq
                """), new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("mst", mst)
                .addValue("type", type)
                .addValue("trigger", trigger)
                .addValue("prmt", paramsJson)
                .addValue("userId", userId)
                .addValue("dtm", dtm), Long.class);
        return id == null ? 0L : id;
    }

    public Optional<RequestRow> find(String ownerId, long id) {
        return db.jdbc().query(db.sql(SELECT + " WHERE owner_id = :ownerId AND rqst_seq = :id"),
                        new MapSqlParameterSource("ownerId", ownerId).addValue("id", id), MAPPER)
                .stream().findFirst();
    }

    /** Most recent request of a type for one MST (e.g. the login test behind a connection status). */
    public Optional<RequestRow> latest(String ownerId, String mst, String type) {
        return db.jdbc().query(db.sql(SELECT + """
                         WHERE owner_id = :ownerId AND cmpn_mst = :mst AND rqst_type = :type
                         ORDER BY rqst_seq DESC LIMIT 1
                        """),
                        new MapSqlParameterSource("ownerId", ownerId).addValue("mst", mst).addValue("type", type), MAPPER)
                .stream().findFirst();
    }

    /** Open (queued / running) requests of a type for one MST — used to avoid stacking duplicates. */
    public List<RequestRow> open(String ownerId, String mst, String type) {
        return db.jdbc().query(db.sql(SELECT + """
                         WHERE owner_id = :ownerId AND cmpn_mst = :mst AND rqst_type = :type
                           AND rqst_stts IN ('QUEUED', 'RUNNING')
                         ORDER BY rqst_seq
                        """),
                new MapSqlParameterSource("ownerId", ownerId).addValue("mst", mst).addValue("type", type), MAPPER);
    }

    /**
     * Asks to stop a request. A queued row is cancelled at once; a running row only gets
     * {@code cncl_yn = 'Y'} and the engine stops at its next checkpoint.
     *
     * @return number of rows changed — 0 when the request is already finished
     */
    public int cancel(String ownerId, long id, String userId) {
        String dtm = WeInvoiceTime.nowDtm();
        return db.jdbc().update(db.sql("""
                UPDATE {app}.winv_engn_rqst
                   SET cncl_yn = 'Y',
                       rqst_stts = CASE WHEN rqst_stts = 'QUEUED' THEN 'CANCELLED' ELSE rqst_stts END,
                       end_dtm   = CASE WHEN rqst_stts = 'QUEUED' THEN :dtm ELSE end_dtm END,
                       mod_id = :userId, mod_dtm = :dtm
                 WHERE owner_id = :ownerId AND rqst_seq = :id AND rqst_stts IN ('QUEUED', 'RUNNING')
                """), new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("id", id)
                .addValue("userId", userId)
                .addValue("dtm", dtm));
    }

    /** One request row; JSON columns are raw text. */
    public record RequestRow(
            long id,
            String ownerId,
            String mst,
            String type,
            String trigger,
            String paramsJson,
            String status,
            String step,
            String progressJson,
            String resultJson,
            String error,
            boolean cancelRequested,
            Long jobRunId,
            String startDtm,
            String endDtm,
            String regId,
            String regDtm) {
    }
}
