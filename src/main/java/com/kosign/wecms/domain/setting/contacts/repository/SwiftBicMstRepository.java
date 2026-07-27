package com.kosign.wecms.domain.setting.contacts.repository;

import com.kosign.wecms.domain.setting.contacts.entity.SwiftBicMst;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Read-only access to the SWIFT BIC directory master
 * ({@code SWIFT_BIC_MST}).
 *
 * <p>The derived finder mirrors legacy {@code IDO.SWIFT_BIC_MST_R002}:
 * {@code WHERE BIC_CD LIKE 'prefix%' ORDER BY BIC_CD LIMIT 50}. The
 * caller uppercases the keyword first (legacy {@code SRCH_WD} is
 * uppercased in {@code comm_1007_001_act.jsp}), and stored codes are
 * uppercase, so a case-sensitive prefix match is correct. An empty
 * keyword yields the first 50 codes (the legacy initial list).
 */
public interface SwiftBicMstRepository extends JpaRepository<SwiftBicMst, String> {

    /** Prefix search capped at 50 rows (legacy R002 parity). */
    List<SwiftBicMst> findTop50ByBicCdStartingWithOrderByBicCdAsc(String bicPrefix);
}
