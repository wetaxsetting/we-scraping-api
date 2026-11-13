package com.kosign.wecms.domain.weinvoice.common;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository.CompanyRow;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ownership scope of WeInvoice (UI Spec 02 §3.1).
 *
 * <p>The owner is the signed-in user (JWT user id); the request never carries it. Engine rows have
 * no owner column — they belong to a tax code — so access is granted through the user's
 * active rows in {@code winv_cmpn_infm}: a working MST must be one of them, and an invoice is
 * visible when its {@code company_mst} is one of them. A foreign invoice answers "not found"
 * rather than "forbidden", so ids cannot be probed.
 */
@Component
public class CompanyAccessGuard {

    private final WeInvoiceDb db;
    private final CompanyConnectionRepository companies;

    public CompanyAccessGuard(WeInvoiceDb db, CompanyConnectionRepository companies) {
        this.db = db;
        this.companies = companies;
    }

    /**
     * Owner of every WeInvoice row: the signed-in WeCMS user ({@code USER_INFM.USER_ID}), not the
     * WeCMS tenant. One account manages its own tax codes, possibly of several companies (UI Spec 02,
     * "Một tài khoản quản lý nhiều MST"); linking tax codes to a WeCMS BIZ_KEY is WeCMS's concern.
     */
    public String ownerId() {
        String ownerId = TenantContext.currentUserId();
        if (ownerId == null || ownerId.isBlank()) {
            throw new AccessDeniedException("No user bound to the request");
        }
        return ownerId;
    }

    public String userId() {
        String userId = TenantContext.currentUserId();
        return userId == null ? "SYSTEM" : userId;
    }

    /** The user's active connection for {@code mst}; {@code WBE3001} malformed, {@code WBE3002} not managed. */
    public CompanyRow requireCompany(String mst) {
        String taxId = WeInvoiceIds.requireTaxId(mst);
        return companies.find(ownerId(), taxId)
                .filter(c -> !c.deleted())
                .orElseThrow(() -> new BusinessException("WBE3002", taxId));
    }

    /** Active tax codes of the user (may be empty). */
    public List<String> ownedMsts() {
        return companies.activeMsts(ownerId());
    }

    /**
     * Resolves an invoice the user may see.
     *
     * @return the invoice's {@code company_mst}
     * @throws BusinessException {@code WBE3005} when the id does not exist or belongs to a tax
     *                           code the user does not manage
     */
    public InvoiceRef requireInvoice(long invoiceId) {
        List<InvoiceRef> rows = db.jdbc().query(db.sql("""
                        SELECT i.id, i.company_mst, i.direction, i.nbmst, i.khmshdon, i.khhdon, i.shdon
                          FROM {eng}.invoices i
                         WHERE i.id = :id
                           AND EXISTS (SELECT 1 FROM {app}.winv_cmpn_infm c
                                        WHERE c.owner_id = :ownerId AND c.cmpn_mst = i.company_mst AND c.del_yn = 'N')
                        """),
                new MapSqlParameterSource("id", invoiceId).addValue("ownerId", ownerId()),
                (rs, n) -> new InvoiceRef(
                        rs.getLong("id"),
                        rs.getString("company_mst"),
                        rs.getString("direction"),
                        rs.getString("nbmst"),
                        rs.getInt("khmshdon"),
                        rs.getString("khhdon"),
                        rs.getLong("shdon")));
        if (rows.isEmpty()) {
            throw new BusinessException("WBE3005");
        }
        return rows.get(0);
    }

    /** Keeps only the ids of invoices the user may see. */
    public List<Long> visibleInvoiceIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return db.jdbc().queryForList(db.sql("""
                        SELECT i.id FROM {eng}.invoices i
                         WHERE i.id IN (:ids)
                           AND EXISTS (SELECT 1 FROM {app}.winv_cmpn_infm c
                                        WHERE c.owner_id = :ownerId AND c.cmpn_mst = i.company_mst AND c.del_yn = 'N')
                        """),
                new MapSqlParameterSource("ids", ids).addValue("ownerId", ownerId()), Long.class);
    }

    /** Identity of an engine invoice: owner MST + the GDT key (seller MST, template, symbol, number). */
    public record InvoiceRef(long id, String companyMst, String direction, String sellerTaxId,
                             int templateNo, String symbol, long number) {
    }
}
