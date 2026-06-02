# Approval Box — Frontend Design (Next.js 16, App Router)

**Feature slug:** `approval-box`
**Module:** `payment`
**Route:** `/payment/approval-box`
**Page file:** `app/(dashboard)/payment/approval-box/page.tsx` (already exists with static mock — to be wired up to the real API)
**Feature folder:** `components/payment/approval-box/`
**i18n prefix:** `approval_box.*` (snake_case keys per [wecms-web/CLAUDE.md](../../../../../wecms-web/CLAUDE.md))
**View Setting screenId:** `approval-box` (constant `SCREEN_IDS.APPROVAL_BOX` in `lib/viewSettingScreens.ts`)
**Date:** 2026-05-13
**Status:** Design only — no TSX changes in `wecms-web/` yet.

Companion docs: [discovery.md](discovery.md), [design-backend.md](design-backend.md), [database-impact.md](database-impact.md), [api-flow.md](api-flow.md), [migration-plan.md](migration-plan.md).
Rules source: [wecms-web/CLAUDE.md](../../../../../wecms-web/CLAUDE.md), [wecms-web/AGENTS.md](../../../../../wecms-web/AGENTS.md), workspace [CLAUDE.md §Global Typography](../../../../../CLAUDE.md).

---

## 1 · Route & page layout

### 1.1 · Route

```
app/(dashboard)/payment/approval-box/page.tsx     ← thin (compose feature component, set metadata)
```

The route already exists and renders the static mock at [wecms-web/components/payment/approval-box/ApprovalBoxPage.tsx](../../../../../wecms-web/components/payment/approval-box/ApprovalBoxPage.tsx). This MR converts the mock to a data-driven page; the file layout does not move.

`metadata` (server component only):
```tsx
export const metadata = { title: "Approval Box — WeCMS" };
```

### 1.2 · Server vs client split

| File | Component type | Why |
|---|---|---|
| `app/(dashboard)/payment/approval-box/page.tsx` | **Server** | Static metadata + JSX shell. No browser APIs. |
| `components/payment/approval-box/ApprovalBoxPage.tsx` | **Client** (`"use client"`) | Owns filter/tab state, data-fetch hook, RHF for search. |
| All sub-components (ApprovalTabs, ApprovalTable, FilterBar, …) | **Client** | Event handlers (`onClick`, `onChange`). |

Pattern matches existing dashboard routes (e.g. `/banking/account-inquiry`).

### 1.3 · Shell composition

Already inherited from `app/(dashboard)/layout.tsx`:
- `<AppShell>` → `<Header>` + `<Sidebar>` + main slot.
- `<PageLayout>` → flex column, 16/24 px gutters.
- `<PageHeader title>` + `<PageActionButtons>` (list / print / excel icons).

**Reused as-is.** No re-mount per [wecms-web/CLAUDE.md](../../../../../wecms-web/CLAUDE.md) §"Shell".

---

## 2 · UI baseline & reused components

Per [wecms-web/CLAUDE.md §UI Implementation Baseline](../../../../../wecms-web/CLAUDE.md#ui-implementation-baseline), pick the closest of four approved references. Approval Box **is itself reference #2** — the canonical tabbed feature page. Existing static UI components are correct; this MR only swaps mock data for real wire data.

| Concern | Component reused (path) | Adjustments for this MR |
|---|---|---|
| Tab strip | `components/payment/approval-box/ApprovalTabs.tsx` | Bound to React state; counts come from `tab_counts` in response. |
| Filter row | `components/payment/approval-box/FilterBar.tsx` + `DateRangePicker.tsx` + `components/shared/SearchInput.tsx` | Date picker swapped for real picker once `comm_0106_001` parity lands (out of scope — keep UI-only initially). |
| Table | `components/payment/approval-box/ApprovalTable.tsx` (bare `<table>` + shared `ApprovalTableColumns.tsx` colgroup) + `StatusBadge.tsx` | Switch from `mockRows` to mapped wire rows. |
| Footer summary | `components/payment/approval-box/ApprovalTableFooter.tsx` | Map server `currency_totals` to the existing `CurrencyTotal[]` prop. |
| Pagination | `components/shared/Pagination.tsx` | Bind page index, page size, total count. |
| Page header / action buttons | `components/shared/PageHeader.tsx` + `components/shared/PageActionButtons.tsx` | `showList` / `showPrint` / `showExcel` props — Excel calls export endpoint. |
| Data fetch | NEW: `lib/api/approvalBox.ts` (Axios call + mapper). | Adds `fetchApprovalBoxList(...)` returning `ApprovalBoxRow[] + totals`. |
| Mapper | NEW entries in `lib/types/api.ts` + `lib/api/mappers.ts`. | snake_case wire → camelCase in-memory. |
| Hover card (approver chain) | NEW: `components/payment/approval-box/ApproverHoverCard.tsx` | Mirrors legacy `pic-infor-contBox` shown in [resl_0001_001_osmc.ts:546-600](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001_osmc.ts#L546). |

---

## 3 · Types — three-layer split

Per [wecms-web/CLAUDE.md](../../../../../wecms-web/CLAUDE.md) §"API Naming & Serialization Standards":

### 3.1 · Wire types (snake_case) — `lib/types/api.ts`

```ts
// — Approval Box list (GET /api/v1/payment/approval-box) —
export interface ApiApprovalBoxApprover {
    apprvr_line_no:    number;
    apprvr_id:         string;
    apprvr_nm:         string;
    apprvr_stts:       "P" | "A" | "R" | "W" | "S";   // stable identifier values stay UPPERCASE
    appr_dtm:          string | null;                 // "YYYYMMDDHHmmss"
    profile_img_url:   string | null;
}

export interface ApiApprovalBoxRow {
    appr_seq_no:           string;
    doc_no:                string;
    title:                 string;
    post_title:            string | null;
    appr_dv:               string;                    // "21" | "31" | "32" | "33" | "34" | "35"
    type_label:            "payroll" | "vendor" | "expense" | "corporate_card" | "overseas" | "internal_transfer";
    currency:              "VND" | "USD" | "KRW";
    tot_pay_amt:           string;                    // numeric on the wire (avoid float)
    pay_date:              string | null;             // "YYYYMMDD"
    scheduled_date:        string | null;
    draft_user_id:         string;
    draft_user_nm:         string;
    reg_dtm:               string;
    appr_stts:             "P" | "I" | "C" | "R";
    apprvr_tot_cnt:        number;
    apprvr_appr_tot_cnt:   number;
    pay_end_yn:            "Y" | "N";
    file_nm:               string | null;
    approvers:             ApiApprovalBoxApprover[];
}

export interface ApiApprovalBoxTabCounts {
    pending:     number;
    in_progress: number;
    approved:    number;
    rejected:    number;
}

export interface ApiApprovalBoxCurrencyTotal {
    currency: "VND" | "USD" | "KRW";
    amount:   string;
}

export interface ApiApprovalBoxListResponse {
    total_count:      number;
    tab_counts:       ApiApprovalBoxTabCounts;
    currency_totals:  ApiApprovalBoxCurrencyTotal[];
    rows:             ApiApprovalBoxRow[];
}
```

### 3.2 · In-memory types (camelCase semantic full words) — `components/payment/approval-box/types.ts`

The existing file is **extended**, not replaced. The current `ApprovalStatus`/`ApprovalTabKey`/`ApprovalRow`/`TabCounts`/`CurrencyTotal` types are already camelCase-shaped — they continue to back the table props.

Diff summary:

```ts
// Existing (KEEP as-is):
export type ApprovalStatus = "draft" | "pending" | "in_progress" | "approved" | "rejected";
export type ApprovalTabKey = "all" | ApprovalStatus;
// …

// NEW additions:
export type ApprovalType =
    | "payroll" | "vendor" | "expense"
    | "corporate_card" | "overseas" | "internal_transfer";

export type ApproverStatus = "P" | "A" | "R" | "W" | "S";

export type ApprovalApprover = {
    lineNo:         number;
    userId:         string;
    userName:       string;
    status:         ApproverStatus;
    actedAt:        string | null;        // ISO-like; render via formatDateTime
    profileImgUrl:  string | null;
};

// Existing ApprovalRow EXTENDED:
export type ApprovalRow = {
    id:               string;     // = appr_seq_no
    apprSeqNo:        string;
    date:             string;     // formatted reg_dtm (existing field, kept for UI)
    type:             string;     // localised display label (resolved via t(type_label))
    typeKey:          ApprovalType;
    documentNo:       string;
    title:            string;
    fileName:         string;
    currency:         "VND" | "USD" | "KRW";
    amount:           string;     // formatted per locale (UI-side)
    status:           ApprovalStatus;
    statusStep?:      string;     // e.g. "1/3"
    scheduledDate?:   string;
    requestor:        string;
    approver:         string;     // shown approver (first in chain or current-step)
    approverCount?:   number;
    approvers:        ApprovalApprover[];   // full chain — hover card source
    payEndYn:         "Y" | "N";
};
```

**OQ-1 reconciliation:** if PO confirms 5 tabs only (per [discovery.md](discovery.md) §10 OQ-1), drop `"draft"` from `ApprovalStatus` and `ApprovalTabKey`, plus the matching column in `TabCounts` and the matching default in `mockTabCounts`. The wire model has no `draft` already.

### 3.3 · Mapper (translation at the boundary) — `lib/api/mappers.ts`

```ts
import type {
    ApiApprovalBoxApprover, ApiApprovalBoxRow, ApiApprovalBoxListResponse,
} from "@/lib/types/api";
import type {
    ApprovalApprover, ApprovalRow, TabCounts, CurrencyTotal, ApprovalStatus,
} from "@/components/payment/approval-box/types";

const STATUS_MAP: Record<"P" | "I" | "C" | "R", ApprovalStatus> = {
    P: "pending", I: "in_progress", C: "approved", R: "rejected",
};

export function mapApprover(a: ApiApprovalBoxApprover): ApprovalApprover {
    return {
        lineNo:        a.apprvr_line_no,
        userId:        a.apprvr_id,
        userName:      a.apprvr_nm,
        status:        a.apprvr_stts,
        actedAt:       a.appr_dtm,
        profileImgUrl: a.profile_img_url,
    };
}

export function mapApprovalRow(r: ApiApprovalBoxRow): ApprovalRow {
    const step = `${r.apprvr_appr_tot_cnt}/${r.apprvr_tot_cnt}`;
    return {
        id:             r.appr_seq_no,
        apprSeqNo:      r.appr_seq_no,
        date:           r.reg_dtm,                // raw — view layer formats
        type:           "",                       // resolved by useTranslation in the row
        typeKey:        r.type_label,
        documentNo:     r.doc_no,
        title:          r.title,
        fileName:       r.file_nm ?? "",
        currency:       r.currency,
        amount:         r.tot_pay_amt,            // formatted per locale at render
        status:         STATUS_MAP[r.appr_stts],
        statusStep:     r.appr_stts === "I" || r.appr_stts === "C" ? step : undefined,
        scheduledDate:  r.scheduled_date ?? undefined,
        requestor:      r.draft_user_nm,
        approver:       r.approvers[0]?.apprvr_nm ?? "",
        approverCount:  r.apprvr_tot_cnt,
        approvers:      r.approvers.map(mapApprover),
        payEndYn:       r.pay_end_yn,
    };
}

export function mapApprovalBoxList(res: ApiApprovalBoxListResponse): {
    totalCount:     number;
    tabCounts:      TabCounts;
    currencyTotals: CurrencyTotal[];
    rows:           ApprovalRow[];
} {
    return {
        totalCount:     res.total_count,
        tabCounts: {
            all:         res.total_count,
            pending:     res.tab_counts.pending,
            in_progress: res.tab_counts.in_progress,
            approved:    res.tab_counts.approved,
            rejected:    res.tab_counts.rejected,
            ...(/* drop or keep 'draft' per OQ-1 */ {} as Partial<TabCounts>),
        } as TabCounts,
        currencyTotals: res.currency_totals.map(c => ({ currency: c.currency, total: c.amount })),
        rows:           res.rows.map(mapApprovalRow),
    };
}
```

The wire→UI translation happens here exactly once. Pages, hooks, components never read `response.data.user_id` or any snake_case key directly. (Enforced by lint review per [wecms-web/CLAUDE.md](../../../../../wecms-web/CLAUDE.md) "Forbidden — Component code that reaches into `response.data.user_id` directly".)

---

## 4 · API client — `lib/api/approvalBox.ts` (new)

```ts
import { api } from "@/lib/axios";
import { mapApprovalBoxList } from "@/lib/api/mappers";
import type { ApiResponse, ApiApprovalBoxListResponse } from "@/lib/types/api";

export type ApprovalBoxQuery = {
    fromDt:       string;        // "YYYYMMDD"
    toDt:         string;
    srchWd?:      string;
    apprTypeCd?:  "" | "00" | "01" | "02" | "03";
    pageNo?:      number;
    pagePerCnt?:  number;
    sortField?:   string;
    sortDirection?: "ASC" | "DESC";
};

export async function fetchApprovalBoxList(q: ApprovalBoxQuery) {
    const params = {
        from_dt:        q.fromDt,
        to_dt:          q.toDt,
        srch_wd:        q.srchWd ?? "",
        appr_type_cd:   q.apprTypeCd ?? "",
        page_no:        q.pageNo ?? 1,
        page_per_cnt:   q.pagePerCnt ?? 15,
        sort_field:     q.sortField ?? "REG_DTM",
        sort_direction: q.sortDirection ?? "DESC",
    };

    const res = await api.get<ApiResponse<ApiApprovalBoxListResponse>>(
        "/api/v1/payment/approval-box", { params }
    );
    if (res.data.status.error) {
        throw Object.assign(new Error(res.data.status.message), {
            code:    res.data.status.code,
            traceId: res.data.trace_id,
        });
    }
    return mapApprovalBoxList(res.data.data!);
}

export async function fetchApprovalBoxExport(q: ApprovalBoxQuery): Promise<Blob> {
    const res = await api.post(
        "/api/v1/payment/approval-box/export",
        { /* shared ExcelExportRequest body — fields + filter mirrors q */ q },
        { responseType: "blob" }
    );
    return res.data as Blob;
}
```

Error funnels through Axios → `ApiCallError`. `traceId` rendered verbatim in the system error dialog (per [wecms-web/CLAUDE.md](../../../../../wecms-web/CLAUDE.md) §"Error handling (frontend side)").

---

## 5 · Page-level state

Inside `ApprovalBoxPage.tsx`:

```ts
const [tab, setTab]               = useState<ApprovalTabKey>("all");
const [search, setSearch]         = useState("");
const [dateRange, setDateRange]   = useState({ start: "...", end: "..." });
const [page, setPage]             = useState(1);
const [pageSize, setPageSize]     = useState(15);
const [sort, setSort]             = useState<{ field: string; direction: "ASC" | "DESC" | "default" }>({
    field: "REG_DTM", direction: "DESC",
});

const [data, setData]             = useState<ApprovalBoxListResponse | null>(null);
const [loading, setLoading]       = useState(false);

// Trigger fetch on any filter change.
useEffect(() => {
    let cancelled = false;
    (async () => {
        setLoading(true);
        try {
            const result = await fetchApprovalBoxList({
                fromDt: toYYYYMMDD(dateRange.start),
                toDt:   toYYYYMMDD(dateRange.end),
                srchWd: search.trim(),
                apprTypeCd: tabToApprTypeCd(tab),
                pageNo: page,
                pagePerCnt: pageSize,
                sortField: sort.field,
                sortDirection: sort.direction === "default" ? "DESC" : sort.direction,
            });
            if (!cancelled) setData(result);
        } catch (err) {
            useDialog().error({ /* traceId from err */ });
        } finally {
            if (!cancelled) setLoading(false);
        }
    })();
    return () => { cancelled = true; };
}, [tab, search, dateRange, page, pageSize, sort]);
```

`tabToApprTypeCd`:
```
"all"         → ""
"pending"     → "00"
"in_progress" → "03"
"approved"    → "01"
"rejected"    → "02"
"draft"       → (drop per OQ-1)
```
Mapping mirrors [resl_0001_001_view_osmc.jsp:268-294](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_osmc.jsp#L268).

No React Query / SWR — direct `useEffect` per [wecms-web/CLAUDE.md](../../../../../wecms-web/CLAUDE.md) "Explicitly NOT used: react-query/SWR". No Redux / Zustand — local state only.

---

## 6 · Form validation & error rendering

The page has two user inputs that need validation:

| Input | Library | Rule | Error |
|---|---|---|---|
| Date range | Zod (single schema in `loginSchema.ts`-style) | `fromDt <= toDt`, both YYYYMMDD, both required | Inline below the picker; bilingual via `t("approval_box.error.invalid_period")`. |
| Search box | RHF + Zod (`approvalBoxSearchSchema.ts`) | `@Size(max=100)` (matches WSVC `length="100"`) | Inline + system dialog on truncation. |

Per [wecms-web/CLAUDE.md](../../../../../wecms-web/CLAUDE.md) "Translation keys live in the Zod message". Sample:

```ts
export const approvalBoxSearchSchema = z.object({
    srchWd: z.string().max(100, "approval_box.error.search_too_long"),
});
```

Server errors arrive on the response as `{ code, message, traceId }`. `code === "WBE_APPROVAL_BOX_INVALID_PERIOD"` → variant **`warning`** dialog. Network / 5xx → variant **`error`** dialog with `traceId` shown verbatim (per [wecms-web/CLAUDE.md](../../../../../wecms-web/CLAUDE.md) §"Dialog variant" — "API/network failures are ALWAYS error, never warning").

---

## 7 · View Setting integration

Per [wecms-web/CLAUDE.md](../../../../../wecms-web/CLAUDE.md) §"Routing" + §13 and [wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"Screen Option (View Setting)":

1. Register screen in `lib/viewSettingScreens.ts`:

```ts
export const SCREEN_IDS = {
    // … existing entries
    APPROVAL_BOX: "approval-box",
} as const;
```

2. Wire the View Setting popover icon (already in `PageActionButtons` via `showList`) to call `GET /api/v1/screen-options/approval-box/default`. The popover/save/reset flows are screen-agnostic per the existing backend module — no per-feature backend changes needed.

3. Each sortable column's `field_id` MUST match `ApprovalBoxSortField` enum constants from [design-backend.md](design-backend.md) §6. Column dictionary (seeded later when View Setting columns ship — DB-impact §3):

| FIELD_ID | Label key | Disp default | Sort default |
|---|---|---|---|
| `CHECKBOX`   | `approval_box.column.checkbox` | Y | N |
| `REG_DTM`    | `approval_box.column.date`     | Y | Y |
| `DOC_NM`     | `approval_box.column.type`     | Y | Y |
| `DOC_NO`     | `approval_box.column.doc_no`   | Y | Y |
| `TITL`       | `approval_box.column.title`    | Y | Y |
| `TOT_PAY_AMT`| `approval_box.column.amount`   | Y | Y |
| `PAY_END_YN` | `approval_box.column.status`   | Y | Y |
| `PAY_DATE`   | `approval_box.column.scheduled_date` | Y | Y |
| `DRAFT_USER_NM` | `approval_box.column.requestor` | Y | Y |
| `APPRER_NM`  | `approval_box.column.approver` | N (off by default) | Y |
| `CURRENCY`   | `approval_box.column.currency` | N | Y |
| `ETC`        | `approval_box.column.etc`      | Y | N |

Compare with the legacy `data-sort` set at [resl_0001_001_view.jsp:189-198](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view.jsp#L189). Final whitelist subject to OQ-5.

---

## 8 · i18n keys (prefix `approval_box.*`)

Keys land in `locales/en.json`, `locales/ko.json`, `locales/vi.json`. **Never** in `km.json` ([wecms-web/CLAUDE.md](../../../../../wecms-web/CLAUDE.md) "Never add entries to km.json").

```json
{
  "approval_box": {
    "title":            "Approval Box",
    "tab": {
      "all":            "All",
      "pending":        "Pending",
      "in_progress":    "In Progress",
      "approved":       "Approved",
      "rejected":       "Rejected"
    },
    "type": {
      "payroll":           "Payroll Payment",
      "vendor":            "Vendor Payment",
      "expense":           "Expense Payment",
      "corporate_card":    "Corporate Card Payment",
      "overseas":          "Overseas Payment",
      "internal_transfer": "Internal Transfer"
    },
    "status": {
      "pending":     "Pending",
      "in_progress": "In Progress",
      "approved":    "Approved",
      "rejected":    "Rejected",
      "unable":      "Unable"
    },
    "column": {
      "checkbox":       "",
      "date":           "Date",
      "type":           "Type",
      "doc_no":         "Document No.",
      "title":          "Subject",
      "amount":         "Transfer Amount",
      "status":         "Approval Status",
      "scheduled_date": "Scheduled Date",
      "requestor":      "Requestor",
      "approver":       "Approver",
      "currency":       "Currency",
      "etc":            ""
    },
    "date_range": {
      "last_30_days":   "Last 30 Days",
      "last_90_days":   "Last 90 Days",
      "last_180_days":  "Last 180 Days",
      "enter_manually": "Enter Manually"
    },
    "totals": {
      "documents":      "{count} document(s)",
      "total":          "Total ({currency})",
      "inquiry_period": "Inquiry Period ({date})"
    },
    "search_placeholder": "Search by document, title, doc no.",
    "error": {
      "invalid_period":   "Start date must be earlier than or equal to end date.",
      "search_too_long":  "Search keyword is too long."
    }
  }
}
```

Keys map back to legacy ML codes per [discovery.md](discovery.md) §6 — e.g. `approval_box.tab.all ⇔ resl_0001_001_m001`, `approval_box.type.payroll ⇔ resl_0001_001_j040`. Authoritative translation strings copied from Jex Studio's registered KO/VI values during the locale-file migration.

---

## 9 · Typography (Global rules)

Per workspace [CLAUDE.md §Global Typography](../../../../../CLAUDE.md):

- Font family is the `Inter / Noto Sans KR / Malgun Gothic / Dotum / sans-serif` stack (set globally; do NOT redeclare via `next/font` or inline `style.fontFamily`).
- Required tokens:
  - **Page title** ("Approval Box"): `text-xl font-medium` → semantic class `text-page-title` (18 / 500).
  - **Tab labels** + tab counts: `text-base font-normal` for inactive, `font-medium` for active. Tab counts render in the brand-tone chip.
  - **Section headings** (e.g. footer "Total" labels): `text-section-title` (14 / 600).
  - **Table header**: `text-table-header` (13 / 600, bg `#e8ebed`/`#fbfbfb`).
  - **Table body**: `text-table-cell` (13 / 400).
  - **Search input / placeholder**: `text-input` / `text-placeholder` (13 / 400, placeholder color `#a3a3a3`).
  - **Standard buttons** (Print, Excel, etc.): `text-button` (13 / 500). No large button on this page.
  - **Status badges** (`StatusBadge.tsx`): `text-xs font-medium` (11 / 500) — semantic helper class via `cn(...)`.
  - **Footer summary line**: `text-helper` (11 / 300) for the muted "Total" prefix; `text-base font-medium` for the amount.
- No `text-[14.5px]` / `font-[533]` / inline `style.fontFamily`. Tailwind tokens only.

The existing static-UI feature folder already follows these rules — verify on the next visual pass and lint with `npm run lint`.

---

## 10 · Dialog variants

| Trigger | Variant | Title key | Description key |
|---|---|---|---|
| Successful Excel download | `success` | `dialog.export.success.title` | `dialog.export.success.body` |
| Date range validation failure (frontend Zod) | `warning` | `approval_box.error.invalid_period` | — |
| Server returned `WBE_APPROVAL_BOX_INVALID_PERIOD` (`status.error = true`, business 4xx) | `warning` | `approval_box.error.invalid_period` | server message |
| Server returned `WBE9999` (or HTTP 5xx, network) | `error` | `dialog.api.failure.title` | server message + `traceId` rendered verbatim |
| Network failure (no response body) | `error` | `dialog.network.failure.title` | "Unable to reach server. (Trace: <localTraceId>)" |

Per [wecms-web/CLAUDE.md](../../../../../wecms-web/CLAUDE.md) §"Dialog variant": API/network failures are **always `error`, never `warning`**. Use `useDialog().show({ type: "error", ... })`.

---

## 11 · Sort cycle

Frontend sort behavior follows [wecms-web/CLAUDE.md](../../../../../wecms-web/CLAUDE.md) §"Sort cycle":

```
default → ASC → DESC → default
```

Send `sort_field` + `sort_direction` only when direction ∈ `ASC` / `DESC`; on `default`, omit `sort_direction` and let backend default to `REG_DTM DESC`. Special FIELD_IDs `CHECKBOX` / `ETC` are never sortable.

---

## 12 · Files modified / created (frontend)

NEW:

- `components/payment/approval-box/ApproverHoverCard.tsx` — chain + status chips.
- `lib/api/approvalBox.ts` — Axios calls + Blob export.
- New mapper exports in `lib/api/mappers.ts`.
- New wire types in `lib/types/api.ts`.

MODIFIED:

- `components/payment/approval-box/ApprovalBoxPage.tsx` — replace mock with real `useEffect`-driven fetch; bind tab/search/date/sort/pagination.
- `components/payment/approval-box/types.ts` — extend with `ApprovalApprover`, `ApprovalType`, etc. (No breaking change to existing fields.)
- `components/payment/approval-box/mockData.ts` — keep alive as a fallback dev-only mock (or delete if not consumed elsewhere).
- `lib/viewSettingScreens.ts` — add `APPROVAL_BOX: "approval-box"`.
- `locales/en.json` / `locales/ko.json` / `locales/vi.json` — add `approval_box.*` keys.

The static UI files already in `components/payment/approval-box/` (ApprovalTable, ApprovalTabs, ApprovalTableFooter, FilterBar, DateRangePicker, StatusBadge, ApprovalTableColumns) **stay as-is** — they are correct per the UI baseline. The MR adds a thin orchestration layer.

---

## 13 · Out-of-scope frontend work (deferred)

| Concern | Reason |
|---|---|
| Real date-range picker | OSMC uses `comm_0106_001` helper — porting it is a separate UI-shared MR. |
| Approval Details popup (`/payment/approval-box/[apprSeqNo]`) | Tied to detail / approve / reject endpoint ports. |
| Bulk-approve toolbar button | Pending OQ-2. |
| Print | Defer until print-pipeline port lands across all list pages. |
| Profile-image fallback | `/api/v1/users/{userId}/profile-image` endpoint not in scope — use placeholder avatar. |

---

## 14 · Cross-references

- Component baseline: [wecms-web/components/payment/approval-box/ApprovalBoxPage.tsx](../../../../../wecms-web/components/payment/approval-box/ApprovalBoxPage.tsx)
- Reference: [Approval Box JEX OSMC TS](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001_osmc.ts)
- Sibling refactor port (read-only list with tabs + counts): `erp-transaction-history` — [docs/refactor-wecms/features/settings/erp-transaction-history.md](../../settings/transaction-history.md)
