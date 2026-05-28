package com.kosign.wecms.domain.auth.repository;

import com.kosign.wecms.domain.auth.entity.UserPwChgHis;
import com.kosign.wecms.domain.auth.entity.UserPwChgHisId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserPwChgHisRepository
        extends JpaRepository<UserPwChgHis, UserPwChgHisId> {

    /**
     * The three most-recent password-history rows for a user, newest first.
     * Backs the "cannot reuse your last 3 passwords" rule — mirrors legacy
     * {@code IDO.USER_PW_CHG_HIS_R003.xml} (ORDER BY REG_DTM DESC LIMIT 3).
     * The candidate new password is compared against each row's
     * {@code USER_PW} hash with the shared {@code PasswordEncoder} so both
     * BCrypt and legacy SHA-256 rows are honoured.
     */
    List<UserPwChgHis> findTop3ByUserIdOrderByRegDtmDescRegSeqNoDesc(String userId);

    /**
     * Current MAX(REG_SEQ_NO) for the user, or 0 when the user has no
     * history rows yet. The service adds 1 to allocate the next sequence —
     * a Java-side per-user generator (no legacy {@code wb_get_next_val}
     * branch) per the DB Function / Legacy Function Policy.
     */
    @Query("select coalesce(max(h.regSeqNo), 0) from UserPwChgHis h where h.userId = :userId")
    long findMaxRegSeqNo(@Param("userId") String userId);
}
