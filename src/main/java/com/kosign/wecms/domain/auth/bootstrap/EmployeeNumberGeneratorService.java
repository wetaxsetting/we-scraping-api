package com.kosign.wecms.domain.auth.bootstrap;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Generates the next {@code EMPL_NO} for a tenant on a given day.
 *
 * <p>Phase 1 — delegating stub. Calls the legacy
 * {@code wb_get_next_val('empl_infm','empl_no', BIZ_KEY)} branch which
 * returns {@code YYYYMMDD_NNN} based on row count per (BIZ_KEY, today).
 *
 * <p><b>Phase 2</b> — reimplement natively in Java with row-level locking
 * or {@code INSERT ... ON CONFLICT DO UPDATE RETURNING}. The naive
 * {@code count(*)+1} the legacy function uses is race-prone under
 * concurrent Spring writes (seahorse plan §2 + §6 rule 12).
 *
 * <p>The {@code today} parameter is accepted to make the eventual native
 * implementation testable with a fixed clock. The current legacy delegate
 * ignores it — the SQL function uses {@code timezone('utc', now())}.
 */
@Service
@RequiredArgsConstructor
public class EmployeeNumberGeneratorService {

    private final LegacyFunctionRepository legacyFunctions;

    public String nextEmplNo(String bizKey, LocalDate today) {
        if (bizKey == null || bizKey.isBlank()) {
            throw new IllegalArgumentException("bizKey is required to generate an EMPL_NO");
        }
        return legacyFunctions.nextVal("EMPL_INFM", "EMPL_NO", bizKey, null, null, null, null, null);
    }
}
