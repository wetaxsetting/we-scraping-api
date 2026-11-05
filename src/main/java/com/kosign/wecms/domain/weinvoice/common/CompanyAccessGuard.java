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
 * Tenant scope of WeInvoice (UI Spec 02 §3.1).
 *
 * <p>The tenant is the JWT {@code BIZ_KEY}; the request never carries it. Engine rows have
 * no tenant column — they belong to a tax code — so access is granted through the tenant's
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

    public String bizKey() {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) {
            throw new AccessDeniedException("No tenant bound to the request");
        }
        return bizKey;
    }

    public String userId() {
        String userId = TenantContext.currentUserId();
        return userId == null ? "SYSTEM" : userId;
    }

    /** The tenant's active connection for {@code mst}; {@code WBE3001} malformed, {@code WBE3002} not managed. */
    public CompanyRow requireCompany(String mst) {
        String taxId = WeInvoiceIds.requireTaxId(mst);
        return companies.find(bizKey(), taxId)
                .filter(c -> !c.deleted())
                .orElseThrow(() -> new BusinessException("WBE3002", taxId));
    }

    /** Active tax codes of the tenant (may be empty). */
    public List<String> tenantMsts() {
        return companies.activeMsts(bizKey());
    }

    /**
     * Resolves an invoice the tenant may see.
     *
     * @return the invoice's {@code company_mst}
     * @throws BusinessException {@code WBE3005} when the id does not exist or belongs to a tax
     *                           code the tenant does not manage
     */
    public InvoiceRef requireInvoice(long invoiceId) {
        List<InvoiceRef> rows = db.jdbc().query(db.sql("""
                        SELECT i.id, i.company_mst, i.direction, i.nbmst, i.khmshdon, i.khhdon, i.shdon
                          FROM {eng}.invoices i
                         WHERE i.id = :id
                           AND EXISTS (SELECT 1 FROM {app}.winv_cmpn_infm c
                                        WHERE c.biz_key = :bizKey AND c.cmpn_mst = i.company_mst AND c.del_yn = 'N')
                        """),
                new MapSqlParameterSource("id", invoiceId).addValue("bizKey", bizKey()),
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

    /** Keeps only the ids of invoices the tenant may see. */
    public List<Long> visibleInvoiceIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return db.jdbc().queryForList(db.sql("""
                        SELECT i.id FROM {eng}.invoices i
                         WHERE i.id IN (:ids)
                           AND EXISTS (SELECT 1 FROM {app}.winv_cmpn_infm c
                                        WHERE c.biz_key = :bizKey AND c.cmpn_mst = i.company_mst AND c.del_yn = 'N')
                        """),
                new MapSqlParameterSource("ids", ids).addValue("bizKey", bizKey()), Long.class);
    }

    /** Identity of an engine invoice: owner MST + the GDT key (seller MST, template, symbol, number). */
    public record InvoiceRef(long id, String companyMst, String direction, String sellerTaxId,
                             int templateNo, String symbol, long number) {
    }
}
