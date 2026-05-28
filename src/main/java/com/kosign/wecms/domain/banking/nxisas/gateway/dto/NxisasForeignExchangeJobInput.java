package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Coocon FOREIGN_EXCHANGE job input — "fetch the FX-rate list for one
 * bank+round on one inquiry date". Mirrors legacy
 * {@code kr.co.coocon.ibasews.cmo.NxisasForignExchangeJobInput} (the
 * legacy typo {@code Forign} preserved only in the legacy class name —
 * the wire field names below match the gateway exactly).
 *
 * <p>Used by the Exchange Rate batch (legacy BCS
 * {@code EXCH_0001_001} / {@code EXCH_0010_001} / {@code EXCH_0020_001}):
 *
 * <pre>{@code
 *   { inqDate: "yyyyMMdd", seqNo: "1" }
 * }</pre>
 *
 * <p>{@code inqDate} is a {@code yyyyMMdd} string (NOT ISO);
 * Coocon's adapter does not parse other formats. {@code seqNo} is a
 * string-typed integer (Shinhan returns 1..2 rounds per day, Woori
 * returns 1..N where N is the highest existing seq for that REF_DATE).
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasForeignExchangeJobInput {

    @JsonProperty("inqDate") private String inqDate;
    @JsonProperty("seqNo")   private String seqNo;

    public NxisasForeignExchangeJobInput(String inqDate, String seqNo) {
        this.inqDate = inqDate;
        this.seqNo = seqNo;
    }
}
