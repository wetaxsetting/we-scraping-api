package com.kosign.wecms.domain.vatdeclaration.repository;

import com.kosign.wecms.domain.vatdeclaration.entity.VatDeclarationIndicator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Access to {@code TAX_DECLARATION_ITEMS}. The table carries no {@code BIZ_KEY}
 * of its own (per the PO design sheet), so tenant scoping rides through the
 * owning declaration join ({@code declaration.bizKey}) on every method.
 */
public interface VatDeclarationIndicatorRepository extends JpaRepository<VatDeclarationIndicator, Long> {

    /** Indicator row of one declaration, tenant-checked via the FK join. */
    Optional<VatDeclarationIndicator> findByDeclaration_SidAndDeclaration_BizKeyAndDelYn(
            Long declSid, String bizKey, String delYn);

    /** Existence probe used by the save-draft upsert branch. */
    boolean existsByDeclaration_SidAndDeclaration_BizKeyAndDelYn(
            Long declSid, String bizKey, String delYn);

    /** Batch fetch for the Dashboard list (tax_amount = ct40 per row). */
    java.util.List<VatDeclarationIndicator> findByDeclaration_SidInAndDelYn(
            java.util.Collection<Long> declSids, String delYn);
}
