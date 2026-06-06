package com.kosign.wecms.domain.banking.ownaccounttransfer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Feature flag for the B1 SEED-CBC engine-output decrypt (Own Account
 * Transfer). Bound from {@code wecms.oat.seed-decrypt.*}.
 *
 * <p>When {@code enabled=false} (the default) the {@code /nxisas/decrypt}
 * and {@code /nxisas/encrypt} endpoints return {@code NXISAS_DATA}
 * unchanged — the pre-B1 pass-through, i.e. no runtime behaviour change.
 * When {@code enabled=true} the endpoints run the legacy Coocon
 * {@code ISASSeedCBC} cipher (bundled as {@code libs/isas1.0.jar}) so a
 * BALANCE / TRANSFER {@code Output.Result} is decrypted to JSON before
 * the frontend parses it — byte-for-byte parity with legacy
 * {@code nxisas_decrypt_act.jsp}.
 *
 * <p>Default OFF so the licensed cipher path only activates in a verified
 * environment. Flip to {@code true} once live BALANCE parity is confirmed
 * per in-scope bank (B1, 2026-06-05). See
 * {@code docs/jex/features/own-account-transfer/b1-decrypt-implementation-plan.md}.
 */
@ConfigurationProperties("wecms.oat.seed-decrypt")
public record OatSeedDecryptProperties(

        @DefaultValue("false")
        boolean enabled

) { }
