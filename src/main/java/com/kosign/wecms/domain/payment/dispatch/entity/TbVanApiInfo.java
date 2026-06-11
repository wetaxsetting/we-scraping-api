package com.kosign.wecms.domain.payment.dispatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * VAN/EFS per-organisation API credential — holds the AES/HMAC shared secret
 * used to build/verify the EFS crypto envelope (EV/VV).
 *
 * <p>Mirrors {@code wecmsdevdb.tb_van_api_info} (created by Flyway V248,
 * column-for-column with the legacy {@code wecmsvndev.tb_van_api_info}):
 * single-column PK {@code VAN_ORG_CD VARCHAR(20)} + {@code API_KEY VARCHAR(100)},
 * both NOT NULL. The legacy table carries no audit columns, so this entity
 * does <b>not</b> extend {@code BaseEntity} and uses a plain {@code @Id}
 * (single-PK idiom, same as {@code UserInfm} / {@code BusiInfm}) rather than the
 * {@code @IdClass} pattern of the composite-key dispatch tables.
 *
 * <p>Legacy read path: {@code util.CommUtil.getApiKey(VAN_ORG_CD)} →
 * {@code SELECT API_KEY FROM TB_VAN_API_INFO WHERE VAN_ORG_CD = ?}. The refactor
 * routes that lookup through
 * {@link com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService}.
 *
 * <p><b>TODO(B2):</b> {@code API_KEY} is the per-VAN secret and is provisioned
 * out-of-band (secure channel) — never seeded in Flyway or any committed file.
 * The table ships empty (V248 = schema only); a lookup returns no value until B2.
 */
@Entity
@Table(name = "TB_VAN_API_INFO")
@Getter
@Setter
@NoArgsConstructor
public class TbVanApiInfo {

    @Id
    @Column(name = "VAN_ORG_CD", length = 20, nullable = false)
    private String vanOrgCd;

    @Column(name = "API_KEY", length = 100, nullable = false)
    private String apiKey;
}
