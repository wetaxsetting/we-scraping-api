package com.kosign.wecms.domain.setting.contacts.repository;

import com.kosign.wecms.domain.setting.contacts.entity.PgroBsacLnknInfm;
import com.kosign.wecms.domain.setting.contacts.entity.PgroBsacLnknInfmId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link PgroBsacLnknInfm}.
 *
 * <p>The Contacts service uses this repository only for the
 * create-time seed of the two default group rows
 * ({@code USER_ID = 'ALL'}, {@code GRP_CD &isin; { 'B', 'S' }}) per
 * legacy parity with {@code comm_0184_002_c001_act.jsp:131-147}.
 * Custom group rows are written elsewhere (legacy Payment / Sales),
 * out of refactor scope.
 */
public interface PgroBsacLnknInfmRepository
        extends JpaRepository<PgroBsacLnknInfm, PgroBsacLnknInfmId> {
}
