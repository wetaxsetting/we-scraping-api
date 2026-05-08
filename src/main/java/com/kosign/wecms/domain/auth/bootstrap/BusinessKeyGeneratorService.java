package com.kosign.wecms.domain.auth.bootstrap;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Issues the next {@code BIZ_KEY} for a new tenant.
 *
 * <p>Phase 1 — delegates to the legacy {@code wb_get_next_val('busi_infm','biz_key', NTNL_CD, CHNL_ID)}
 * branch (see seahorse plan §2 + functions.sql:5600). The DB function builds
 * keys of shape {@code {NTNL_CD}_{CHNL_ID}_{NNNNNN}} from MAX(suffix)+1 within
 * the matching prefix.
 *
 * <p>Phase 3 — swap to a Spring-managed sequence reader and drop this service's
 * dependency on the legacy function.
 */
@Service
@RequiredArgsConstructor
public class BusinessKeyGeneratorService {

    private final LegacyFunctionRepository legacyFunctions;

    /**
     * Allocates the next {@code BIZ_KEY} for the given country and channel.
     *
     * @param ntnlCd national/country code (e.g. {@code VN})
     * @param chnlId channel id (e.g. {@code VNM0004})
     * @return new BIZ_KEY of shape {@code {NTNL_CD}_{CHNL_ID}_{NNNNNN}}
     */
    public String nextBizKey(String ntnlCd, String chnlId) {
        if (ntnlCd == null || ntnlCd.isBlank() || chnlId == null || chnlId.isBlank()) {
            throw new IllegalArgumentException("ntnlCd and chnlId are required to generate a BIZ_KEY");
        }
        return legacyFunctions.nextVal("BUSI_INFM", "BIZ_KEY", ntnlCd, chnlId, null, null, null, null);
    }
}
