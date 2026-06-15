package com.kosign.wecms.domain.auth.entity;

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
 * Mirrors legacy wecmsvndevdb.USER_PW_CHG_HIS — the password-change-history
 * ledger that backs the "cannot reuse your last 3 passwords" rule.
 * <p>
 * One row is written on every successful password change
 * ({@code ChangePasswordService}). The reuse check compares a candidate new
 * password against the {@code USER_PW} hash of the three most-recent rows
 * (legacy {@code IDO.USER_PW_CHG_HIS_R003.xml} parity).
 * <p>
 * {@code USER_PW} stores a BCrypt hash, exactly like {@code USER_INFM.USER_PW}
 * — written via {@code WecmsPasswordEncoder.encode}. Legacy stored the
 * {@code hash(:USER_PW)} SHA-256 base64 value; the refactor's hybrid encoder
 * reads either form, so DEV-imported rows still match.
 * <p>
 * No {@code BaseEntity} (this table has no BIZ_KEY and no standard audit
 * column set) — same precedent as the sibling {@link UserLoginHis}.
 * {@code REG_DTM} / {@code MOD_DTM} are {@link OffsetDateTime} and
 * {@code REG_SEQ_NO} is {@link Long}; see V207 for the documented deviations
 * from the legacy VARCHAR(14) / NUMERIC shapes.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "USER_PW_CHG_HIS")
@IdClass(UserPwChgHisId.class)
public class UserPwChgHis {

    @Id
    @Column(name = "USER_ID", length = 100, nullable = false)
    private String userId;

    @Id
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "REG_SEQ_NO", precision = 20, scale = 0, nullable = false)
    private Long regSeqNo;

    @Column(name = "USER_PW", length = 100, nullable = false)
    private String userPw;

    /** JEX VARCHAR(14) yyyyMMddHHmmss — converted from OffsetDateTime. */
    @Convert(converter = LegacyDtmConverter.class)
    @Column(name = "MOD_DTM", length = 14)
    private OffsetDateTime modDtm;

    @Column(name = "IP_ADDR", length = 100)
    private String ipAddr;

    @Convert(converter = LegacyDtmConverter.class)
    @Column(name = "REG_DTM", length = 14, nullable = false)
    private OffsetDateTime regDtm;
}
