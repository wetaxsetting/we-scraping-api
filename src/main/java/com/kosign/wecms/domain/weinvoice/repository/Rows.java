package com.kosign.wecms.domain.weinvoice.repository;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Nullable number reads. {@code rs.getObject(col, Long.class)} is not used on purpose: pgjdbc
 * converts to {@code Long} only from {@code bigint} and throws "conversion … not supported" for the
 * {@code numeric(18)} keys of the WeInvoice tables, while {@code getLong} reads both.
 */
final class Rows {

    private Rows() {
    }

    static Long longOrNull(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }

    static Integer intOrNull(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }
}
