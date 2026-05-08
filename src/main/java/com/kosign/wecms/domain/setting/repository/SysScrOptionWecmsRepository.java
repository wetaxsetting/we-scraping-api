package com.kosign.wecms.domain.setting.repository;

import com.kosign.wecms.domain.setting.entity.SysScrOptionWecms;
import com.kosign.wecms.domain.setting.entity.SysScrOptionWecmsId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SysScrOptionWecmsRepository
        extends JpaRepository<SysScrOptionWecms, SysScrOptionWecmsId> {

    /** All system defaults for a screen + area, ordered for the UI. */
    List<SysScrOptionWecms> findByWebSvcIdAndDymAreaIdOrderByDispOrdAsc(
            String webSvcId, String dymAreaId);

    /** Distinct dynamic-area ids for one screen. Used by the
     *  {@code /screen-options/{webSvcId}/areas} aggregator. */
    @Query("select distinct s.dymAreaId from SysScrOptionWecms s "
            + "where s.webSvcId = :webSvcId order by s.dymAreaId asc")
    List<String> findDistinctDymAreaIdByWebSvcId(@Param("webSvcId") String webSvcId);

    /** Column count per dynamic area for one screen. Returns rows shaped
     *  as {@code [dymAreaId, count]}. */
    @Query("select s.dymAreaId, count(s) from SysScrOptionWecms s "
            + "where s.webSvcId = :webSvcId "
            + "group by s.dymAreaId "
            + "order by s.dymAreaId asc")
    List<Object[]> countColumnsByDymAreaId(@Param("webSvcId") String webSvcId);
}
