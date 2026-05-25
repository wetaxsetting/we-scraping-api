package com.kosign.wecms.domain.common.country.service;

import com.kosign.wecms.domain.common.country.dto.CountryListResponse;
import com.kosign.wecms.domain.common.country.dto.CountryRow;
import com.kosign.wecms.domain.common.country.entity.NatnInfm;
import com.kosign.wecms.domain.common.country.repository.NatnInfmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-only country directory service. Backs the global phone-country
 * picker and any future surface that needs the country master.
 *
 * <p>Mirrors legacy {@code comm_0110_001_r001_act.jsp} +
 * {@code IDO.NATN_INFM_R002}: returns every row sorted by display name
 * with a {@code default_yn} flag set to {@code "Y"} for Vietnam (the
 * project's default tenant locale). Single-tenant data — no filter.
 */
@Service
@RequiredArgsConstructor
public class CountryService {

    /** Legacy default — see {@code IDO.NATN_INFM_R002.xml}:
     *  {@code CASE WHEN NTNL_CD='VN' THEN 'Y'}. */
    private static final String DEFAULT_NTNL_CD = "VN";

    private static final String FLAG_Y = "Y";
    private static final String FLAG_N = "N";

    private final NatnInfmRepository natnInfmRepo;

    @Transactional(readOnly = true)
    public CountryListResponse list() {
        List<NatnInfm> rows = natnInfmRepo.findAllByOrderByNatlNmAsc();
        List<CountryRow> out = rows.stream().map(this::toRow).toList();
        return new CountryListResponse(out, out.size());
    }

    private CountryRow toRow(NatnInfm row) {
        return new CountryRow(
                row.getNtnlCd(),
                row.getNatlNm(),
                row.getNatnNum(),
                DEFAULT_NTNL_CD.equals(row.getNtnlCd()) ? FLAG_Y : FLAG_N
        );
    }
}
