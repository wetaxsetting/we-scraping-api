package com.kosign.wecms.domain.banking.smartorganizer.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C3 — SQL-content + binding assertions for the eligibility query, relocated
 * from {@code SmartOrganizerServiceTest} when the native SQL moved here. Pins
 * that the usage-group/direction filter, the tenant filter, the whitelist
 * ORDER BY, pagination, and parameter binding survive the relocation.
 */
class SmartOrganizerEligibleRepositoryTest {

    private static final String BIZ  = "VN_VNM0004_000001";
    private static final String BANK = "11704088";
    private static final String ACCT = "5280123456";

    @Test
    void countEligible_sqlCarriesUsageGroupAndTenantFilter_andBinds() {
        EntityManager em = mock(EntityManager.class);
        Query q = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(q);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.getSingleResult()).thenReturn(0L);

        SmartOrganizerEligibleRepository repo = new SmartOrganizerEligibleRepository();
        ReflectionTestUtils.setField(repo, "em", em);

        long total = repo.countEligible(BIZ, BANK, ACCT, "20260101", "20260131", null);

        assertThat(total).isEqualTo(0L);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(em).createNativeQuery(sql.capture());
        String s = sql.getValue();
        // legacy getDynamic1 group↔direction filter, gated on a non-null usage (Option B)
        assertThat(s).contains("busi_usag_set_infm");
        assertThat(s).contains("u.usag_grp_cd = CASE WHEN h.inout_dv = '1' THEN '4' ELSE '6' END");
        assertThat(s).contains("r.use_usag_cd IS NULL OR EXISTS");
        // tenant filter present
        assertThat(s).contains("h.biz_key = :bizKey");
        // parameter binding used
        verify(q).setParameter("bizKey", BIZ);
        verify(q).setParameter("bankCd", BANK);
        verify(q).setParameter("acctNo", ACCT);
    }

    @Test
    void pageEligible_appliesWhitelistOrderBy_pagination_andSearchBinding() {
        EntityManager em = mock(EntityManager.class);
        Query q = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(q);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.setFirstResult(anyInt())).thenReturn(q);
        when(q.setMaxResults(anyInt())).thenReturn(q);
        when(q.getResultList()).thenReturn(List.of());

        SmartOrganizerEligibleRepository repo = new SmartOrganizerEligibleRepository();
        ReflectionTestUtils.setField(repo, "em", em);

        // 'h.trsc_amt' is a whitelist-validated expression supplied by the caller.
        repo.pageEligible(BIZ, BANK, ACCT, "20260101", "20260131", "GASOLINE",
                "h.trsc_amt", false, 30, 15);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(em).createNativeQuery(sql.capture());
        String s = sql.getValue();
        assertThat(s).contains("ORDER BY h.trsc_amt DESC");          // validated expr + direction
        assertThat(s).contains(", h.acct_seq_no ASC");               // deterministic tiebreaker
        assertThat(s).contains("h.rmrk ILIKE '%' || :search || '%'");// search predicate present
        assertThat(s).contains("h.biz_key = :bizKey");               // tenant filter
        verify(q).setParameter("search", "GASOLINE");                // search binding used
        verify(q).setFirstResult(30);                                // pagination offset
        verify(q).setMaxResults(15);                                 // pagination limit
    }
}
