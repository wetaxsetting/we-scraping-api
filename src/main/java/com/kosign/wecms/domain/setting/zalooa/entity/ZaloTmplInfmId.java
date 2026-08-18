package com.kosign.wecms.domain.setting.zalooa.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite identity for {@link ZaloTmplInfm}:
 * {@code (BIZ_KEY, OA_ID, TMPL_TYPE, LANG_CD)}.
 *
 * <p>Language belongs in the key so template resolution at send time is an
 * exact primary-key lookup rather than a filtered scan that could return the
 * wrong locale.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ZaloTmplInfmId implements Serializable {
    private String bizKey;
    private String oaId;
    private String tmplType;
    private String langCd;
}
