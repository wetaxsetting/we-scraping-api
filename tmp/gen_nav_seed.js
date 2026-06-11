/*
 * One-shot generator: reads the JexAdmin MenuManager export and emits the
 * V243 navigation seed SQL (MENU_ITEM, PRODUCT_MENU, MENU_ITEM_TRANSLATION).
 *
 * Not part of the build — run once, the SQL it prints is committed as the
 * Flyway migration. Kept under tmp/ for traceability of how the seed was
 * produced. Maps are the verified slug / route / permission_cd sets from
 * docs/jex/features/menu-product-type-management/route-mapping.md §1/§2,
 * route-migration-audit.md §1, and permission-code-normalization-verification.md §1.
 */
const fs = require("fs");

const EXPORT = "C:/WECMS PROJECT/docs/jex/features/menu-product-type-management/export/wecms-menu-export.json";
const data = JSON.parse(fs.readFileSync(EXPORT, "utf8"));

// legacy MNU_ID -> refactor slug (menu_item_id). Covers parents + leaves.
const SLUG = {
  fnsh: "banking",
  fnsh_0001_VND: "banking-current-savings-vnd",
  fnsh_0001_001_VND: "banking-account-inquiry-vnd",
  fnsh_0002_001_VND: "banking-transaction-history-vnd",
  fnsh_0005_FCY: "banking-current-savings-fcy",
  fnsh_0005_001_FCY: "banking-account-inquiry-fcy",
  fnsh_0006_001_FCY: "banking-transaction-history-fcy",
  trns_0001: "banking-internal-transfer-vnd",
  trns_0001_001: "banking-transfer-own-accounts",
  trns_0003_001: "banking-transfer-history",
  fnsh_rept: "banking-reports",
  rept_0009_001: "banking-report-daily-cash",
  rept_0015_001: "banking-report-daily-financial",
  rept_0017_001: "banking-report-daily-financial-haengsung",
  cash_0001_001: "banking-report-cash-flow",
  prch: "payment",
  prch_resl: "payment-approval",
  resl_0001_001: "payment-approval-box",
  prch_intf: "payment-internal-transfer",
  pymn_0009_001: "payment-scheduled-transfer",
  pymn_0010_001: "payment-transfer-result",
  prch_vend_paym: "payment-vendor",
  pymn_0001_001: "payment-vendor-scheduled",
  pymn_0002_001: "payment-vendor-result",
  prch_paym: "payment-expense",
  pymn_0003_001: "payment-expense-scheduled",
  pymn_0004_001: "payment-expense-result",
  prch_payr: "payment-payroll",
  pymn_0005_001: "payment-payroll-scheduled",
  pymn_0006_001: "payment-payroll-result",
  prch_payr_oves: "payment-overseas",
  pymn_0007_001: "payment-overseas-request",
  pymn_0008_001: "payment-overseas-result",
  exch_0001_001: "payment-exchange-rate",
  prch_card: "payment-corporate-card",
  card_0001_001: "payment-card-transactions",
  adns: "addon-service",
  sale_prch_001: "addon-einvoice",
  sale_einv_001: "addon-sales-einvoice",
  prch_einv_001: "addon-expense-einvoice",
  aosv_0000_001: "addon-operation-status",
  aosv_0001_001: "addon-operation-status-inquiry",
};

// leaf MNU_ID -> live menuAuth.ts permission_cd (verified against the DB).
const PERMISSION_CD = {
  fnsh_0001_001_VND: "banking-vnd-account-inquiry",
  fnsh_0002_001_VND: "banking-vnd-inquire-transactions",
  fnsh_0005_001_FCY: "banking-fcy-update-inquire",
  fnsh_0006_001_FCY: "banking-fcy-inquire-transactions",
  trns_0001_001: "banking-internal-transfer-own",
  trns_0003_001: "banking-internal-transfer-history",
  rept_0009_001: "banking-reports-daily-cash",
  rept_0015_001: "banking-reports-daily-financial",
  rept_0017_001: "banking-reports-daily-financial-hs", // Daily Financial HS split target
  cash_0001_001: "banking-reports-cash-flow",
  resl_0001_001: "payment-approval-box",
  pymn_0009_001: "payment-internal-transfer-scheduled",
  pymn_0010_001: "payment-internal-transfer-result",
  pymn_0001_001: "payment-vendor-scheduled",
  pymn_0002_001: "payment-vendor-result",
  pymn_0003_001: "payment-expense-scheduled",
  pymn_0004_001: "payment-expense-result",
  pymn_0005_001: "payment-payroll-scheduled",
  pymn_0006_001: "payment-payroll-result",
  pymn_0007_001: "payment-overseas-request",
  pymn_0008_001: "payment-overseas-result",
  exch_0001_001: "payment-overseas-exchange",
  card_0001_001: "payment-corporate-card-transactions",
  sale_einv_001: "addon-einvoices-sales",
  prch_einv_001: "addon-einvoices-expenses",
  aosv_0001_001: "addon-operation-status-inquiry",
};

// leaf MNU_ID -> implemented Next.js route (route-migration-audit.md §1).
const ROUTE = {
  fnsh_0001_001_VND: "/banking/account-inquiry",
  fnsh_0002_001_VND: "/banking/transaction-history",
  fnsh_0005_001_FCY: "/banking/account-inquiry-fcy",
  fnsh_0006_001_FCY: "/banking/transaction-history-fcy",
  trns_0001_001: "/banking/transfer-own-accounts",
  trns_0003_001: "/banking/transfer-history",
  rept_0009_001: "/banking/reports/daily-cash",
  rept_0015_001: "/banking/reports/daily-financial",
  rept_0017_001: "/banking/reports/daily-financial-hs",
  cash_0001_001: "/banking/reports/cash-flow",
  resl_0001_001: "/payment/approval-box",
  pymn_0009_001: "/payment/internal-transfer/scheduled-transfer",
  pymn_0010_001: "/payment/internal-transfer/transfer-result",
  pymn_0001_001: "/payment/vendor-payment/scheduled-payment",
  pymn_0002_001: "/payment/vendor-payment/payment-result",
  pymn_0003_001: "/payment/expense-payment/scheduled-payment",
  pymn_0004_001: "/payment/expense-payment/payment-result",
  pymn_0005_001: "/payment/payroll/scheduled-payment",
  pymn_0006_001: "/payment/payroll/payment-result",
  pymn_0007_001: "/payment/overseas-payment/scheduled-payment",
  pymn_0008_001: "/payment/overseas-payment/payment-result",
  exch_0001_001: "/payment/overseas-payment/exchange-rate-inquiry",
  card_0001_001: "/payment/corporate-card/card-transactions",
  sale_einv_001: "/addon/sales-einvoices",
  prch_einv_001: "/addon/expenses-einvoices",
  aosv_0001_001: "/addon/operation-status",
};

const q = (s) => (s == null ? "NULL" : "'" + String(s).trim().replace(/'/g, "''") + "'");

const groups = data.GRP_LIST;
// menu_item: union of distinct slugs across all products (F covers all but HS).
const itemSeen = new Set();
const itemRows = [];
for (const g of groups) {
  for (const n of g.MENU_LIST) {
    const slug = SLUG[n.MNU_ID];
    if (!slug) throw new Error("No slug for MNU_ID " + n.MNU_ID);
    if (itemSeen.has(slug)) continue;
    itemSeen.add(slug);
    const parentSlug = n.PRNT_MNU_ID === "ROOT" ? null : SLUG[n.PRNT_MNU_ID];
    itemRows.push({
      id: slug,
      parent: parentSlug,
      depth: parseInt(n.MNU_DEP, 10),
      route: ROUTE[n.MNU_ID] || null,
      icon: n.ICON && n.ICON.trim() !== "" ? n.ICON : null,
      cd: PERMISSION_CD[n.MNU_ID] || null,
      legacyMnu: n.MNU_ID,
      legacyWsvc: n.WSVC && n.WSVC.trim() !== "" ? n.WSVC : null,
    });
  }
}
// Stable order: depth then id (parents before children for readability; FK is deferred-soft anyway).
itemRows.sort((a, b) => a.depth - b.depth || a.id.localeCompare(b.id));

const pmRows = [];
const trRows = [];
for (const g of groups) {
  const pt = g.PRDT_TYPE;
  for (const n of g.MENU_LIST) {
    const slug = SLUG[n.MNU_ID];
    pmRows.push({ pt, id: slug, seq: parseInt(n.MNU_SEQ, 10) });
    for (const lang of ["EN", "KO", "VI"]) {
      const label = n.LANG[lang];
      if (label == null) continue;
      trRows.push({ pt, id: slug, lang, label });
    }
  }
}

let out = "";
out += "-- V243 — Navigation menu catalogue seed (F / M / H product trees).\n";
out += "--\n";
out += "-- Source of truth: docs/jex/features/menu-product-type-management/export/wecms-menu-export.json\n";
out += "-- (JexAdmin MenuManager export; F=41, H=33, M=41 nodes — validated).\n";
out += "-- Generated by tmp/gen_nav_seed.js. permission_cd values are the live menuAuth.ts\n";
out += "-- kebab codes verified against USER_MENU_AUTH_INFM in\n";
out += "-- permission-code-normalization-verification.md §1. Daily Financial HS uses the split\n";
out += "-- code banking-reports-daily-financial-hs (rept_0017_001); standard uses\n";
out += "-- banking-reports-daily-financial (rept_0015_001).\n";
out += "--\n";
out += "-- Idempotent: INSERT … ON CONFLICT DO NOTHING. Safe to re-run.\n";
out += "-- MENU_ITEM = product-independent structure (one row per distinct slug).\n";
out += "-- PRODUCT_MENU = per-product membership + ordering (the F/M/H catalogue difference).\n";
out += "-- MENU_ITEM_TRANSLATION = per-product per-language labels (M's VI/KO diverge from F).\n";
out += `-- Counts: menu_item=${itemRows.length}, product_menu=${pmRows.length}, translation=${trRows.length}.\n\n`;

out += "INSERT INTO MENU_ITEM\n";
out += "    (MENU_ITEM_ID, PARENT_ID, MENU_DEP, ROUTE, ICON, SHOW_YN, PERMISSION_CD, LEGACY_MNU_ID, LEGACY_WSVC_ID)\nVALUES\n";
out += itemRows
  .map(
    (r) =>
      `    (${q(r.id)}, ${q(r.parent)}, ${r.depth}, ${q(r.route)}, ${q(r.icon)}, 'Y', ${q(r.cd)}, ${q(r.legacyMnu)}, ${q(r.legacyWsvc)})`
  )
  .join(",\n");
out += "\nON CONFLICT (MENU_ITEM_ID) DO NOTHING;\n\n";

out += "INSERT INTO PRODUCT_MENU (PRODUCT_TYPE, MENU_ITEM_ID, DISP_ORD)\nVALUES\n";
out += pmRows.map((r) => `    (${q(r.pt)}, ${q(r.id)}, ${r.seq})`).join(",\n");
out += "\nON CONFLICT (PRODUCT_TYPE, MENU_ITEM_ID) DO NOTHING;\n\n";

out += "INSERT INTO MENU_ITEM_TRANSLATION (PRODUCT_TYPE, MENU_ITEM_ID, LANG_CD, LABEL)\nVALUES\n";
out += trRows.map((r) => `    (${q(r.pt)}, ${q(r.id)}, ${q(r.lang)}, ${q(r.label)})`).join(",\n");
out += "\nON CONFLICT (PRODUCT_TYPE, MENU_ITEM_ID, LANG_CD) DO NOTHING;\n";

fs.writeFileSync("src/main/resources/db/migration/V243__navigation_menu_seed.sql", out, "utf8");
console.log(
  `menu_item=${itemRows.length} product_menu=${pmRows.length} translation=${trRows.length}`
);
