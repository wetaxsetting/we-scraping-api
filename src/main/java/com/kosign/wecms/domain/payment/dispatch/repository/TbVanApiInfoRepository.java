package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.payment.dispatch.entity.TbVanApiInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for {@link TbVanApiInfo} (VAN/EFS API credential).
 *
 * <p>PK is the single column {@code VAN_ORG_CD} ({@code String}). The
 * dispatch flow resolves the per-VAN {@code API_KEY} by org code — the refactor
 * of legacy {@code IDO.TB_VAN_API_INFO_R001} (`SELECT API_KEY ... WHERE
 * VAN_ORG_CD = ?`). Consumed via
 * {@link com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService}.
 */
public interface TbVanApiInfoRepository extends JpaRepository<TbVanApiInfo, String> {

    /** Look up the credential row by VAN org code (legacy {@code TB_VAN_API_INFO_R001}). */
    Optional<TbVanApiInfo> findByVanOrgCd(String vanOrgCd);
}
