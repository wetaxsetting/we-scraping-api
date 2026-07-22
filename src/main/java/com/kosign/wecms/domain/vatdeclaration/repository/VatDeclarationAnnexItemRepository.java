package com.kosign.wecms.domain.vatdeclaration.repository;

import com.kosign.wecms.domain.vatdeclaration.entity.VatDeclarationAnnexItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Access to {@code TAX_DECLARATION_ANNEX_ITEMS}. Tenant scope rides through the
 * owning declaration join ({@code declaration.bizKey}) on every method — the
 * annex-line table carries no {@code BIZ_KEY} of its own.
 */
public interface VatDeclarationAnnexItemRepository extends JpaRepository<VatDeclarationAnnexItem, Long> {

    /** All live annex rows of one declaration (all four types), display order. */
    List<VatDeclarationAnnexItem> findByDeclaration_SidAndDeclaration_BizKeyAndDelYnOrderByAnnexTypeAscDispOrdAsc(
            Long declSid, String bizKey, String delYn);

    /** Live annex rows of one type (BKBR / BKMV / ALLOC / REDUCE8) for one declaration. */
    List<VatDeclarationAnnexItem> findByDeclaration_SidAndDeclaration_BizKeyAndAnnexTypeAndDelYnOrderByDispOrdAsc(
            Long declSid, String bizKey, String annexType, String delYn);

    /** Single row, tenant-checked via the FK join (delete / edit guard). */
    Optional<VatDeclarationAnnexItem> findByIdAndDeclaration_BizKeyAndDelYn(
            Long id, String bizKey, String delYn);
}
