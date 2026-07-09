package com.kosign.wecms.domain.setting.logincredentials.dto;

/**
 * GDT local-engine module config served to the frontend so the
 * client-side NXiSAS engine can resolve the GDT scrapping module/class
 * without a JexAdmin/OSMC round-trip. Wire (snake_case): {@code api_id},
 * {@code scrapping_module}, {@code scrapping_class}, {@code bank_cd}.
 *
 * <p>Sourced from the fixed code constants in
 * {@link com.kosign.wecms.domain.setting.logincredentials.config.GdtNxisasConfig}.
 */
public record GdtEngineConfigResponse(
        String apiId,
        String scrappingModule,
        String scrappingClass,
        String bankCd
) { }
