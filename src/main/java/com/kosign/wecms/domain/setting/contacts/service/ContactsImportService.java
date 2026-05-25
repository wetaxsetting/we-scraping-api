package com.kosign.wecms.domain.setting.contacts.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.domain.setting.contacts.dto.ContactBulkImportRequest;
import com.kosign.wecms.domain.setting.contacts.dto.ContactBulkImportResponse;
import com.kosign.wecms.domain.setting.contacts.dto.ContactImportError;
import com.kosign.wecms.domain.setting.contacts.dto.ContactCreateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Bulk import service for the Contacts Excel Upload feature.
 *
 * <p>Each row is processed via {@link ContactsService#create} which
 * carries its own {@code @Transactional} scope. Because this class is
 * a separate Spring bean, every {@code create()} call goes through the
 * transactional proxy — a validation or constraint failure on one row
 * rolls back only that row's transaction, leaving every previously
 * imported row committed.
 *
 * <p>The response always returns {@code HTTP 200} with a summary;
 * partial failures are encoded in {@link ContactBulkImportResponse#errors()}
 * rather than as a 4xx/5xx. The frontend surfaces the counts in the
 * success/error dialog.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContactsImportService {

    private final ContactsService contactsService;
    private final MessageResolver messageResolver;

    /**
     * Process all rows in {@code request.contacts()}, importing each
     * one individually. Rows that pass validation and business rules are
     * committed; rows that throw are recorded in the error list without
     * affecting the others.
     */
    public ContactBulkImportResponse bulkImport(ContactBulkImportRequest request) {
        List<ContactCreateRequest> rows = request.contacts();
        int total = rows.size();
        int imported = 0;
        List<ContactImportError> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            ContactCreateRequest req = rows.get(i);
            try {
                contactsService.createForImport(req);
                imported++;
            } catch (BusinessException ex) {
                String message = messageResolver.resolve(ex.getCode(), ex.getArgs());
                errors.add(new ContactImportError(i, req.handTrcoKey(), ex.getCode(), message));
                log.warn("Contact import row {} ({}) failed: {} — {}",
                        i, req.handTrcoKey(), ex.getCode(), message);
            } catch (Exception ex) {
                errors.add(new ContactImportError(i, req.handTrcoKey(), "WBE9999",
                        ex.getMessage() != null ? ex.getMessage() : "Unexpected error"));
                log.error("Contact import row {} ({}) unexpected error", i, req.handTrcoKey(), ex);
            }
        }

        return new ContactBulkImportResponse(total, imported, total - imported, errors);
    }
}
