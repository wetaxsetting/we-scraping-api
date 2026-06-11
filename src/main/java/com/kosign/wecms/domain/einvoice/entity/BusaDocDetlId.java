package com.kosign.wecms.domain.einvoice.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Composite key for {@link BusaDocDetl} — {@code (BIZ_KEY, DOC_ID, DISP_ORD)}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BusaDocDetlId implements Serializable {
    private String bizKey;
    private String docId;
    private Integer dispOrd;
}
