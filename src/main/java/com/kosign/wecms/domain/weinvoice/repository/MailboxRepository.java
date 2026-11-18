package com.kosign.wecms.domain.weinvoice.repository;

import com.kosign.wecms.domain.weinvoice.common.WeInvoiceTime;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code winv_mlbx_infm} — mailboxes a user connects. Every query is scoped by {@code owner_id}.
 * The secret column is written here and read by {@link #findSecret} only; {@link MailboxRow} never
 * carries it, so no mapper can leak it into a response.
 */
@Repository
public class MailboxRepository {

    private static final String SELECT = """
            SELECT mlbx_seq, owner_id, mlbx_kind, mail_addr, auth_type, imap_host, imap_port, tls_mode, login_id,
                   fldr_list, cmpn_scope, cmpn_mst, conn_stts, conn_msg, last_scan_dtm, last_scan_cnt, reg_dtm
              FROM {app}.winv_mlbx_infm
            """;

    private static final RowMapper<MailboxRow> MAPPER = (rs, n) -> new MailboxRow(
            rs.getLong("mlbx_seq"),
            rs.getString("owner_id"),
            rs.getString("mlbx_kind"),
            rs.getString("mail_addr"),
            rs.getString("auth_type"),
            rs.getString("imap_host"),
            Rows.intOrNull(rs, "imap_port"),
            rs.getString("tls_mode"),
            rs.getString("login_id"),
            rs.getString("fldr_list"),
            rs.getString("cmpn_scope"),
            rs.getString("cmpn_mst"),
            rs.getString("conn_stts"),
            rs.getString("conn_msg"),
            rs.getString("last_scan_dtm"),
            Rows.intOrNull(rs, "last_scan_cnt"),
            rs.getString("reg_dtm"));

    private final WeInvoiceDb db;

    public MailboxRepository(WeInvoiceDb db) {
        this.db = db;
    }

    public List<MailboxRow> listActive(String ownerId) {
        return db.jdbc().query(db.sql(SELECT + " WHERE owner_id = :ownerId AND del_yn = 'N' ORDER BY reg_dtm, mlbx_seq"),
                new MapSqlParameterSource("ownerId", ownerId), MAPPER);
    }

    /** Active mailbox of the user. */
    public Optional<MailboxRow> find(String ownerId, long id) {
        return db.jdbc().query(db.sql(SELECT + " WHERE owner_id = :ownerId AND mlbx_seq = :id AND del_yn = 'N'"),
                        new MapSqlParameterSource("ownerId", ownerId).addValue("id", id), MAPPER)
                .stream().findFirst();
    }

    /** Active mailbox of the user with this address (case-insensitive). */
    public Optional<MailboxRow> findByAddress(String ownerId, String address) {
        return db.jdbc().query(db.sql(SELECT
                                + " WHERE owner_id = :ownerId AND lower(mail_addr) = lower(:addr) AND del_yn = 'N'"),
                        new MapSqlParameterSource("ownerId", ownerId).addValue("addr", address), MAPPER)
                .stream().findFirst();
    }

    /** The encrypted secret of an active mailbox — for a re-test that keeps it. Never put it in a response. */
    public Optional<String> findSecret(String ownerId, long id) {
        return db.jdbc().queryForList(db.sql("""
                        SELECT secret_enc FROM {app}.winv_mlbx_infm
                         WHERE owner_id = :ownerId AND mlbx_seq = :id AND del_yn = 'N'
                        """),
                new MapSqlParameterSource("ownerId", ownerId).addValue("id", id), String.class).stream().findFirst();
    }

    /**
     * Connects an address the engine has just verified: inserts it, or — when the user already has an
     * active mailbox with the same address — updates that row (new settings, new secret, {@code CONNECTED}).
     *
     * @return the mailbox id
     */
    public long save(MailboxWrite w, String userId) {
        String dtm = WeInvoiceTime.nowDtm();
        Long id = db.jdbc().queryForObject(db.sql("""
                INSERT INTO {app}.winv_mlbx_infm
                       (owner_id, mlbx_kind, mail_addr, auth_type, imap_host, imap_port, tls_mode, login_id, secret_enc,
                        fldr_list, cmpn_scope, cmpn_mst, conn_stts, conn_msg, del_yn, reg_id, reg_dtm, mod_id, mod_dtm)
                VALUES (:ownerId, :kind, :addr, :auth, :host, :port, :tls, :login, :secret,
                        :folders, :scope, :mst, 'CONNECTED', NULL, 'N', :userId, :dtm, :userId, :dtm)
                ON CONFLICT (owner_id, lower(mail_addr)) WHERE del_yn = 'N' DO UPDATE
                   SET mlbx_kind = EXCLUDED.mlbx_kind, mail_addr = EXCLUDED.mail_addr, auth_type = EXCLUDED.auth_type,
                       imap_host = EXCLUDED.imap_host, imap_port = EXCLUDED.imap_port, tls_mode = EXCLUDED.tls_mode,
                       login_id = EXCLUDED.login_id, secret_enc = EXCLUDED.secret_enc, fldr_list = EXCLUDED.fldr_list,
                       cmpn_scope = EXCLUDED.cmpn_scope, cmpn_mst = EXCLUDED.cmpn_mst,
                       conn_stts = 'CONNECTED', conn_msg = NULL, mod_id = EXCLUDED.mod_id, mod_dtm = EXCLUDED.mod_dtm
                RETURNING mlbx_seq
                """), params(w, userId, dtm).addValue("secret", w.secretEnc()), Long.class);
        return id == null ? 0L : id;
    }

    /**
     * Updates an active mailbox after a successful re-test. {@code secretEnc == null} keeps the stored secret.
     *
     * @return rows changed (0 = not found / not owned)
     */
    public int update(long id, MailboxWrite w, String userId) {
        return db.jdbc().update(db.sql("""
                UPDATE {app}.winv_mlbx_infm
                   SET mlbx_kind = :kind, mail_addr = :addr, auth_type = :auth, imap_host = :host, imap_port = :port,
                       tls_mode = :tls, login_id = :login, secret_enc = COALESCE(:secret, secret_enc),
                       fldr_list = :folders, cmpn_scope = :scope, cmpn_mst = :mst,
                       conn_stts = 'CONNECTED', conn_msg = NULL, mod_id = :userId, mod_dtm = :dtm
                 WHERE owner_id = :ownerId AND mlbx_seq = :id AND del_yn = 'N'
                """), params(w, userId, WeInvoiceTime.nowDtm())
                .addValue("secret", w.secretEnc(), java.sql.Types.VARCHAR)
                .addValue("id", id));
    }

    /** Soft delete; the stored secret is erased at the same time (column is NOT NULL → empty string). */
    public int softDelete(String ownerId, long id, String userId) {
        return db.jdbc().update(db.sql("""
                UPDATE {app}.winv_mlbx_infm
                   SET del_yn = 'Y', secret_enc = '', mod_id = :userId, mod_dtm = :dtm
                 WHERE owner_id = :ownerId AND mlbx_seq = :id AND del_yn = 'N'
                """), new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("id", id)
                .addValue("userId", userId)
                .addValue("dtm", WeInvoiceTime.nowDtm()));
    }

    private static MapSqlParameterSource params(MailboxWrite w, String userId, String dtm) {
        return new MapSqlParameterSource()
                .addValue("ownerId", w.ownerId())
                .addValue("kind", w.kind())
                .addValue("addr", w.address())
                .addValue("auth", w.authType())
                .addValue("host", w.imapHost())
                .addValue("port", w.imapPort(), java.sql.Types.INTEGER)
                .addValue("tls", w.tlsMode())
                .addValue("login", w.loginId())
                .addValue("folders", w.folders())
                .addValue("scope", w.companyScope())
                .addValue("mst", w.companyMst(), java.sql.Types.VARCHAR)
                .addValue("userId", userId)
                .addValue("dtm", dtm);
    }

    /** Values written for a mailbox. {@code folders} is the stored comma-separated list. */
    public record MailboxWrite(String ownerId, String kind, String address, String authType, String imapHost,
                               Integer imapPort, String tlsMode, String loginId, String secretEnc, String folders,
                               String companyScope, String companyMst) {

        /** Never print the secret, even encrypted. */
        @Override
        public String toString() {
            return "MailboxWrite[ownerId=" + ownerId + ", kind=" + kind + ", address=" + address + ", authType=" + authType
                    + ", imapHost=" + imapHost + ", imapPort=" + imapPort + ", tlsMode=" + tlsMode + ", loginId=" + loginId
                    + ", secretEnc=" + (secretEnc == null ? "<keep>" : "***") + ", folders=" + folders
                    + ", companyScope=" + companyScope + ", companyMst=" + companyMst + "]";
        }
    }

    /** One mailbox row, secret excluded. Datetimes are the stored {@code yyyyMMddHHmmss} (UTC). */
    public record MailboxRow(
            long id,
            String ownerId,
            String kind,
            String address,
            String authType,
            String imapHost,
            Integer imapPort,
            String tlsMode,
            String loginId,
            String folders,
            String companyScope,
            String companyMst,
            String status,
            String statusMessage,
            String lastScanDtm,
            Integer lastScanCount,
            String regDtm) {
    }
}
