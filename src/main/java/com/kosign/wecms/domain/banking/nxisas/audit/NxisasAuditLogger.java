package com.kosign.wecms.domain.banking.nxisas.audit;

import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.nxisas.audit.entity.NxsiasModuleTranLogHis;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Writes one {@code NXSIAS_MODULE_TRAN_LOG_HIS} row per NXISAS gateway
 * call. Uses {@code REQUIRES_NEW} so the audit row survives outer
 * rollbacks (matches the {@link com.kosign.wecms.common.auditlog.service.AuditLogService}
 * pattern).
 */
@Component
@RequiredArgsConstructor
public class NxisasAuditLogger {

    private static final DateTimeFormatter DTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final NxsiasModuleTranLogHisRepository repo;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String trSeq,
                       String moduleName,
                       String classCategory,
                       String job,
                       String errCode,
                       String errCont,
                       String reqData,
                       String type) {
        NxsiasModuleTranLogHis row = new NxsiasModuleTranLogHis();
        row.setUuid(trSeq);
        row.setRegDtm(LocalDateTime.now(ZoneOffset.UTC).format(DTM14));
        row.setBizKey(TenantContext.currentBizKey());
        row.setRegId(TenantContext.currentUserId());
        row.setNxisasModule(safe(moduleName));
        row.setNxisasClass(safe(classCategory));
        row.setNxisasJob(safe(job));
        row.setNxisasErrCode(safe(errCode));
        row.setNxisasErrCont(truncate(errCont, 10_000));
        row.setReqData(truncate(reqData, 32_000));
        row.setNxisasType(type);
        repo.save(row);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
