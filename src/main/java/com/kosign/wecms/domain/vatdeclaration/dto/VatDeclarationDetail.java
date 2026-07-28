package com.kosign.wecms.domain.vatdeclaration.dto;

import jakarta.persistence.Column;

/**
 * Detail view — header + full indicator block. Dates ship as
 * {@code yyyy-MM-dd} strings.
 */
public record VatDeclarationDetail(
        Long sid,
        String periodType,
        Integer periodValue,
        Integer declYear,
        String periodLabel,
        Integer supplNo,
        String declType,
        String status,
        String tpName,
        String tpTaxCode,
        String activityCode,
        String activityName,
        String fromDt,
        String toDt,
        String declDt,
        VatIndicatorSet indicators,
        String annexBkbrYn,
        String annexBkmvYn,
        String annexAllocYn,
        String annexAllocTeleYn,
        String annexReduce8Yn,

        // Header [06]–[11A]: tax agent + dependent unit (agent_ctr_dt = yyyy-MM-dd)
        String agentNm,
        String agentTaxCode,
        String agentCtrNo,
        String agentCtrDt,
        String duNm,
        String duTaxCode,
        String duAddr,
        String duProv,
        String duDist,
        String duWard
){
}
