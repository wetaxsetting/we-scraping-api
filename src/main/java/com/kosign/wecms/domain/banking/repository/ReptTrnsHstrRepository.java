package com.kosign.wecms.domain.banking.repository;

import com.kosign.wecms.domain.banking.entity.ReptTrnsHstr;
import com.kosign.wecms.domain.banking.entity.ReptTrnsHstrId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Write access to the HAENGSUNG Daily Financial ledger base table
 * ({@code REPT_TRNS_HSTR}) for the per-field editable-cell save
 * (legacy {@code rept_0017_001_u001}).
 *
 * <p>Tenant scope is enforced by the composite PK
 * ({@code BIZ_KEY} + {@code ACCT_SEQ_NO}) — the service always resolves
 * {@code BIZ_KEY} from {@link com.kosign.wecms.common.tenant.TenantContext},
 * never from the request, so a caller can only patch rows in its own tenant.
 */
public interface ReptTrnsHstrRepository extends JpaRepository<ReptTrnsHstr, ReptTrnsHstrId> {

    Optional<ReptTrnsHstr> findByBizKeyAndAcctSeqNo(String bizKey, String acctSeqNo);
}
