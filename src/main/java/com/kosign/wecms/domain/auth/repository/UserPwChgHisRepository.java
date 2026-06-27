package com.kosign.wecms.domain.auth.repository;

import com.kosign.wecms.domain.auth.entity.UserPwChgHis;
import com.kosign.wecms.domain.auth.entity.UserPwChgHisId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserPwChgHisRepository
        extends JpaRepository<UserPwChgHis, UserPwChgHisId> {

    /**
     * The three most-recent password-history rows for a user, newest first.
     * Backs the "cannot reuse your last 3 passwords" rule — mirrors legacy
     * {@code IDO.USER_PW_CHG_HIS_R003.xml} (ORDER BY REG_DTM DESC LIMIT 3).
     * The candidate new password is compared against each row's
     * {@code USER_PW} hash with the shared {@code PasswordEncoder}. New rows
     * are always the legacy SHA-256 ({@code public.hash()}) value; the
     * encoder also tolerates a pre-existing BCrypt hash on read (legacy dev
     * rows only), so both forms verify — but only SHA-256 is ever written.
     */
    List<UserPwChgHis> findTop3ByUserIdOrderByRegDtmDescRegSeqNoDesc(String userId);

    // REG_SEQ_NO allocation now routes through
    // LegacyFunctionRepository.nextUserPwChgRegSeqNo (V140 #27 — per-user MAX+1,
    // verbatim from the live JEX function) so every wb_get_next_val call goes
    // through the one funnel.
}
