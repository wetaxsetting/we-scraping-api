package com.kosign.wecms.domain.auth.bootstrap;

/**
 * Inputs for creating a new tenant via {@link CompanyBootstrapService}.
 *
 * <p>If {@code bizKey} is {@code null} the service generates one via
 * {@link BusinessKeyGeneratorService}. If supplied (e.g. by the LOCAL
 * runner pinning to {@code wecms.tenant.biz-key}) it is used verbatim
 * after the {@link com.kosign.wecms.config.TenantProperties#assertConsistent}
 * guard.
 */
public record CompanyBootstrapCommand(
        String bizKey,
        String cmpnNm,
        String ntnlCd,
        String chnlId,
        FirstAdminCommand firstAdmin
) { }
