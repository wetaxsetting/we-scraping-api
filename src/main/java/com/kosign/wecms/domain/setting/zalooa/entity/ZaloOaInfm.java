package com.kosign.wecms.domain.setting.zalooa.entity;

import com.kosign.wecms.common.entity.BaseEntity;
import com.kosign.wecms.common.entity.LegacyDtmConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Settings &gt; Zalo OA — a Zalo Official Account connected by one tenant,
 * together with the OAuth tokens issued for it. Maps {@code ZALO_OA_INFM}
 * (V254 migration).
 *
 * <p>Refactor-native table: Zalo has no legacy JEX counterpart, so there is no
 * DEV schema to mirror. The functional reference is the WeBill365 VN
 * implementation audited 2026-08-17.
 *
 * <p><b>Link and tokens live in the same row.</b> WeBill365 keeps the
 * tenant&harr;OA link ({@code msg_auth}) apart from the tokens
 * ({@code msg_auth_token}, keyed by a globally UNIQUE {@code client_id}); two
 * tenants that connect the same Official Account therefore end up sharing one
 * token row and overwrite each other's credentials. Keying this table
 * {@code (BIZ_KEY, OA_ID)} removes that failure mode.
 *
 * <p><b>At most one live OA per tenant.</b> Enforced by the partial unique index
 * {@code zalo_oa_infm_uk01} on {@code (BIZ_KEY) WHERE USE_YN='Y' AND DEL_YN='N'}.
 * Service code that activates an OA must deactivate the tenant's others in the
 * same transaction or the insert will be rejected.
 *
 * <p>Tenant scoping is part of the primary key. Every repository lookup takes
 * {@code bizKey} from
 * {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ZALO_OA_INFM")
@IdClass(ZaloOaInfmId.class)
public class ZaloOaInfm extends BaseEntity {

    /** Tenant key. PK component. */
    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    /** Zalo Official Account ID. Entered by the tenant before the OAuth
     *  popup opens, then confirmed when the callback lands. PK component. */
    @Id
    @Column(name = "OA_ID", length = 50, nullable = false)
    private String oaId;

    /** Display name shown in the settings list. Not used when sending. */
    @Column(name = "OA_NM", length = 100)
    private String oaNm;

    /** Short-lived Zalo access token. Stored in plaintext, matching the
     *  reference implementation — encryption at rest is an open decision. */
    @Column(name = "ACCESS_TOKEN", length = 1000)
    private String accessToken;

    /** Long-lived refresh token. Zalo <b>rotates</b> this on every refresh, so
     *  the value returned by a refresh call must always be written back. */
    @Column(name = "REFRESH_TOKEN", length = 1000)
    private String refreshToken;

    /** Absolute expiry of {@link #accessToken}, derived at write time from the
     *  {@code expires_in} Zalo returns. Null until the first successful token
     *  exchange. */
    @Convert(converter = LegacyDtmConverter.class)
    @Column(name = "TOKEN_EXPI_DTM", length = 14)
    private OffsetDateTime tokenExpiDtm;

    /** Most recent OAuth authorization code. Kept for troubleshooting only —
     *  a code is single-use and worthless once exchanged. */
    @Column(name = "AUTH_CD", length = 255)
    private String authCd;

    /** {@code Y} = the OA outbound sends go through. At most one per tenant. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "USE_YN", length = 1, nullable = false, columnDefinition = "CHAR(1) DEFAULT 'N'")
    private String useYn;

    /** {@code Y} = disconnected. Soft delete; excluded from every lookup. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "DEL_YN", length = 1, nullable = false, columnDefinition = "CHAR(1) DEFAULT 'N'")
    private String delYn;
}
