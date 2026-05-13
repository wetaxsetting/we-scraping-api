package com.kosign.wecms.domain.setting.contacts.repository;

import com.kosign.wecms.domain.setting.contacts.entity.TrcoInfoFile;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoInfoFileId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

/**
 * Read / write access to {@code TRCO_INFO_FILE} (V98) — the
 * per-contact attachment table. PK is the composite
 * {@code (TRCO_KEY, FILE_ID)} so callers pass either a
 * {@link TrcoInfoFileId} or the two parts.
 *
 * <p>The table is tenant-scoped indirectly via the parent
 * {@code TRCO_INFM} row (FK + service-layer guard that resolves the
 * parent contact in the current tenant before consulting this
 * repository). No {@code BIZ_KEY} column on this table by legacy
 * design.
 */
public interface TrcoInfoFileRepository extends JpaRepository<TrcoInfoFile, TrcoInfoFileId> {

    /**
     * Lists every attachment for a given contact, ascending by the
     * numeric file id (legacy parity — file IDs are sequential text
     * tokens "1", "2", "3", ... so a lexicographic sort matches the
     * insertion order for any realistic count).
     */
    List<TrcoInfoFile> findByTrcoKeyOrderByFileIdAsc(String trcoKey);

    Optional<TrcoInfoFile> findByTrcoKeyAndFileId(String trcoKey, String fileId);

    /**
     * Hard-delete by composite key. The service is responsible for
     * confirming tenant ownership BEFORE calling this (the table has
     * no {@code BIZ_KEY} column so the repository can't enforce
     * tenancy on its own).
     */
    @Modifying
    void deleteByTrcoKeyAndFileId(String trcoKey, String fileId);

    long countByTrcoKey(String trcoKey);
}
