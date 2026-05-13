package com.kosign.wecms.domain.banking.dto.coocon;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NxisasJobInput {
    @JsonProperty("inqDate")
    private String inqDate;

    @JsonProperty("seqNo")
    private String seqNo;
}
