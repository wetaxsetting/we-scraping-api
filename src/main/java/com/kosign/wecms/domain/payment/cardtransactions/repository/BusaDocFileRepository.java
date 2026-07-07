package com.kosign.wecms.domain.payment.cardtransactions.repository;

import com.kosign.wecms.domain.payment.cardtransactions.entity.BusaDocFile;
import com.kosign.wecms.domain.payment.cardtransactions.entity.BusaDocFileId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

/**
 * Read / write access to {@code BUSA_DOC_FILE} (audit G3/G5) — receipt /
 * e-Invoice attachments. PK {@code (BIZ_KEY, DOC_ID, FILE_ID)}; tenant-scoped
 * directly via {@code BIZ_KEY} (unlike {@code TRCO_INFO_FILE}). The service
 * still resolves the parent {@code BUSA_DOC_MSTR} in the tenant before touching
 * a file row so an out-of-tenant / unknown {@code doc_id} can't attach files.
 */
public interface BusaDocFileRepository extends JpaRepository<BusaDocFile, BusaDocFileId> {

    /** All attachments for one document, ascending by numeric file-id token. */
    List<BusaDocFile> findByBizKeyAndDocIdOrderByFileIdAsc(String bizKey, String docId);

    /** Batch — all attachments for a page of documents (Receipt / e-Invoice
     *  tab file count + dropdown, one query, grouped in the service). */
    List<BusaDocFile> findByBizKeyAndDocIdInOrderByDocIdAscFileIdAsc(
            String bizKey, List<String> docIds);

    Optional<BusaDocFile> findByBizKeyAndDocIdAndFileId(String bizKey, String docId, String fileId);

    @Modifying
    void deleteByBizKeyAndDocIdAndFileId(String bizKey, String docId, String fileId);
}
