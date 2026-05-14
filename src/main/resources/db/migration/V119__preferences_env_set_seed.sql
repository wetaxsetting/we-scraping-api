-- ============================================================================
-- V119__preferences_env_set_seed.sql
--
-- Settings > Preferences panel — seed the SYS defaults + tenant BUSI
-- rows for the two toggle codes that drive the panel's purpose / VAS
-- switches:
--
--   00074 = Use VAS Account  (legacy comm_0215_001.ts:466 / live JEX
--                             screen — toggles BUSI_VAS_ACCT_INFM
--                             availability across the product)
--   00077 = Use Purpose      (legacy comm_0215_001.ts:516 — toggles
--                             USAG_INFM availability across the product)
--
-- V69 created the SYS / BUSI tables and seeded 10 codes (firm-banking
-- automation + doc-id / doc-no prefixes). The two preferences codes
-- (00074, 00077) and the related 00075 / 00076 (g-VAS scope + interface
-- method, not surfaced on the Preferences panel yet but observed in
-- DEV) were not in V69's set. This migration adds them with the legacy
-- DEV defaults verbatim, then backfills BUSI rows for every existing
-- tenant so the generic env-settings PUT (WBE2031 guard) succeeds out
-- of the box.
--
-- Idempotent.
-- ============================================================================

-- SYS defaults (verbatim from wecmsvndev.sys_env_set_infm — queried
-- via the postgres MCP). 'Y' for both toggles matches the live DEV
-- behaviour where Purpose + VAS are on by default.
INSERT INTO sys_env_set_infm (env_set_inf, env_set_cont) VALUES
    ('00074', 'Y'),   -- Use VAS Account
    ('00075', 'V'),   -- VAS Account inquiry scope (V = standard; G = g-VAS)
    ('00076', 'E'),   -- Interface method (E = Excel upload; A = API)
    ('00077', 'Y')    -- Use Purpose
ON CONFLICT (env_set_inf) DO NOTHING;

-- BUSI rows — one per tenant per code. Without these the PUT path
-- throws WBE2031 (mirrors the legacy assumption that wb_init_busi_data
-- pre-seeded every SYS code at tenant bootstrap). Initial value
-- inherits the SYS default; the user flips it from the Preferences
-- panel. mod_dtm format follows the legacy yyyyMMddHHmmss VARCHAR(14)
-- shape used elsewhere in V69 / V70.
INSERT INTO busi_env_set_infm (biz_key, env_set_inf, env_set_cont, mod_id, mod_dtm)
SELECT b.biz_key, s.env_set_inf, s.env_set_cont, 'SYSTEM',
       to_char(now() AT TIME ZONE 'UTC', 'YYYYMMDDHH24MISS')
FROM busi_infm b
CROSS JOIN sys_env_set_infm s
WHERE s.env_set_inf IN ('00074', '00075', '00076', '00077')
ON CONFLICT (biz_key, env_set_inf) DO NOTHING;
