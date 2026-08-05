# EFS REST API Guide v1.2.6 — Open-Question Closure Pass

> ## ⚠️ STATUS BANNER — remediation applied 2026-07-31 (read before acting on anything below)
>
> **The body of this document is the analysis as written, and is deliberately unchanged — it is the
> point-in-time record of what the vendor specification does and does not say.** Several of its
> recommendations have since been implemented. Where the text below reads *"Recommendation (not
> applied)"*, consult this banner first.
>
> ### Applied — 2026-07-31, same day, in the remediation pass that followed this audit
>
> | Item | Was | Now | Where |
> |---|---|---|---|
> | **F-1 / C-7** — read timeout below the vendor SLA (§4.5, §9) | `readTimeoutMs = 20_000` | **`60_000`**, matching legacy; `connectTimeoutMs` unchanged at 3 000 | `EfsProperties` + `application-{local,sit,uat,prod}.yml`. Javadoc now cites **§3.2** as the source and records that the §6 sample is non-binding. |
> | **D-4** — envelope RC over-reach (§6.6) | sibling fail-stop → `G998`; count/amount mismatch → `9999` | **both → `0000`** on the envelope. `G998` / `9999` still persist unchanged in `REG_RSLT_CD` and `TB_FIRM_BAT_API_TRAN.RS_CD` | `BulkReceptionResultService` (:269, :298 as numbered at audit time), + a new class-Javadoc section *"Envelope RC vs persisted result code"*. Two existing tests extended to pin `ack.rc() == 0000` alongside the persisted codes. |
> | **§5.5** — Base64 repair mechanism | unexplained legacy workaround | mechanism documented: the guide's own §6.2 `EncryptAesBase64` calls `UrlDecode` on the **encrypt** path | `EfsCryptoService.repairBase64Alphabet` Javadoc. The method is **kept**; the 2-pass decode is **not** added — **and as of 2026-08-03 that is a settled decision, no longer a B5 deferral** (see §5.4 addendum). |
> | **§6.5 / report item c** — `BT00` has no documentary basis | bare `TODO(B5)` comment | `RECEPTION_SUCCESS_CD` Javadoc now states explicitly that `BT00` / `BT01` / `C999` appear **zero times** in the guide and in [참조 2] | `BulkReceptionResultService` |
> | **F-4** (§9) — §5.4 FX block printed unconditionally Mandatory | audit finding only | recorded as SIT blocker **S-7** with a first-transfer diagnosis shortcut | [`../deployment/payment-dispatch-merge-sit-handoff.md`](../deployment/payment-dispatch-merge-sit-handoff.md) §7 |
>
> **Knock-on, also applied:** raising the read timeout moved `ResultCheckJob`'s worst-case pass from
> 43.3 min to **110 min** (100 rows × 66 s = 3 s connect + 60 s read + 3 s Woori pace), which
> `ResultCheckLockCeilingTest` correctly failed. `lockAtMostFor` was raised **PT60M → PT2H30M**
> (1.36× headroom). A `TODO` on that job records that a **per-pass deadline** is the right long-term
> fix rather than an ever-growing ceiling.
>
> ### Corrections to this document's own findings
>
> - **F-2 is only partially resolved, and this document under-reported it.** Eliminating `9999` and
>   `G998` from the wire leaves **two** unpublished codes still emitted, not one: `G004`
>   (`CallbackAck`, `DataAccessException`) — which F-2 named — **and `G003`** (VV-verify failure,
>   emitted from both callback services), **which F-2 missed entirely**. Both sit on genuinely
>   unprocessed paths, so their *shape* is correct; only the *values* are outside the [참조 2]
>   published set. Closing F-2 fully needs §10 questions 2 and 8.
>
> > ### Applied — 2026-08-01, from the "Needs VAN Center" legacy verification pass
>
> A downstream memo summarising this document was verified row-by-row against legacy source; four of its
> seven rows were wrong and two were masking vendor questions this document had already raised. **Nothing
> in the body below changed** — the corrections are to the summary, not to this analysis. One code change
> followed: the 01302 unmatched-callback branch now logs at `error` + raises an ops alert (signal only —
> the acknowledgement stays `0000`, and the legacy `G999` divergence is deferred pending §10 q1). Full
> record, including the corrected table and the 01301/01302 asymmetry:
> [`van-center-open-questions-verification-2026-08-01.md`](van-center-open-questions-verification-2026-08-01.md).
>
> Two items below are **re-confirmed and now have a named consumer**: **§10 q1** decides whether our
> 01302 unknown-file `0000` silently consumes a result legacy would have had resent; **§10 q6** blocks
> Gate B-2 (the operating-hours PO decision) — the 24 h window was verified to *match legacy* on the live
> JEX DEV DB, so B-2 is a legacy-vs-vendor conflict, not a seeding error.

### Still open — nothing below has been actioned
>
> - **F-2 residual** — `G003` and `G004` on the wire (above).
> - **F-4** — recorded as S-7, but the vendor confirmation (§10 q4) has not been requested.
> - **F-7** — operating hours. Escalated from "inert" to a live PO decision; see
>   [`../deployment/wecms-batch-activation-runbook.md`](../deployment/wecms-batch-activation-runbook.md).
> - **F-3, F-5, F-6, F-8, F-9** — unchanged, report-only.
> - **K3** — the `글로벌 VAN_펌뱅킹_배치파일 레이아웃.xlsx` request (§10 q3) has **not** been sent. It is
>   still wanted for the **`data_amt` 23-vs-22 width** question, but it **no longer blocks charset.**
> - **~~C-13~~ (charset) — CLOSED NEGATIVELY 2026-07-31, superseding §2.2/§2.3 below.** The file layer is
>   **ASCII by vendor contract and by observation**, on evidence that is not in this PDF:
>   handover §8.4 pitfall ④ (`08_기능추출_이관가이드.md:64`) — *"성조 제거 … 미적용 시 은행 측 거절"*
>   ("tones removed … if not applied, **the bank rejects it**") — plus the production record: **no
>   dispatched file has ever completed carrying a multibyte receiver name.** §2.3's "the
>   hypothesis is neither supported nor refuted" was correct *about this PDF* and is now overtaken;
>   §2.2's quote of `FixedLengthCodec`'s "accepted charset is unconfirmed" cites a Javadoc that has since
>   been rewritten to "RESOLVED for every entry path". Do not re-open charset on the strength of this
>   document's silence. See **D-L** in
>   [`legacy-batch-boundary-rule-sweep-2026-07-31.md`](legacy-batch-boundary-rule-sweep-2026-07-31.md) §1.
>   **Basis narrowed 2026-08-01 — closure unchanged.** An earlier wording of this bullet cited "exactly
>   two files ever carried multibyte receiver names and neither reached `PROC_STEP='3'`" as the
>   observational half. Those two files (`S_GV00000006_SHB_001/002_20251010`) are dated **19 days before**
>   tone stripping existed on this rail (`IDO.TB_FIRM_FILE_DATA_C001` v`20251029205935`) and are a
>   resubmission pair whose payload was edited between attempts — so they evidence a **data** rejection,
>   not a charset one, and must not be cited here. C-13 **stays CLOSED** on the two grounds that survive:
>   pitfall ④'s vendor statement, and the fact that **no multibyte file has ever completed**. The
>   correlation is retired; the conclusion is not.
> - **D-4 proper** — the applied change is the *safe default under an unknown*, not an answer. §10
>   question 1 still needs Mr. Kang. **B5 (a captured gateway sample) remains the critical path** for
>   D-4, the `result_cd` set, and the 2-pass question.
>
> *Banner added 2026-07-31. This project has three recorded near-misses from stale documents
> (`deployment-readiness.md:195`, `.env.batch.example` §6, handover §8.5.6); it is here so this is not
> the fourth. If you action anything above, update this banner in the same change.*

> **Date:** 2026-07-31 · **Read-only.** No source code, config, or DB object was changed by this pass.
> **Primary source:** `WECMS_BATCH DEV/handover/웹케시글로벌_베트남_전자금융서비스_연동가이드(REST API)_ver1.2.6.pdf` (44 pages, Coocon / VAN Center).
> **Secondary:** the nine `00~08_*.md` handover documents in the same folder.
> **Prior extractions reviewed:** [`efs-api-guide-1.2.6-diff-audit.md`](../../../WECMS_BATCH%20DEV/analysis/integration/efs-api-guide-1.2.6-diff-audit.md) · [`efs-service-mapping-from-pdf.md`](../../../WECMS_BATCH%20DEV/analysis/integration/efs-service-mapping-from-pdf.md)
> **Method:** `pdftotext -enc UTF-8 -layout` (1541 lines) + direct extraction and rendering of the 26 embedded image XObjects — §3.8's sequence diagram is a raster image and had never been read by any prior pass.
> **Scope:** answers the eight questions posed for C-7 / C-8 / C-13 / C-15 / D-4 / K3 and the 00302 timing item, each marked **CLOSED** or **STILL OPEN**.

---

## 0. Bottom line

| # | Question | Verdict |
|---|---|---|
| 1 | CHARSET (C-13 / K3) | **SPLIT** — API/JSON layer **CLOSED** (UTF-8, §1.2). Fixed-length H/D/T file **STILL OPEN** (explicitly delegated to another document). |
| 2 | HTTP headers (C-8) | **CLOSED (negatively)** — the spec requires `Content-Type` and nothing else. No `Authorization`, no `signature`, no `x-coocon-accessIp`, no IP allow-listing anywhere in 44 pages. Our client is conformant. |
| 3 | Timeouts / SLA (C-7) | **CLOSED — and our value is wrong.** §3.2 states a normative **30-second** limit. Our 20 s read timeout is *below* the vendor's own SLA and is sourced from a sample the PDF labels non-normative. |
| 4 | EV/VV encoding (C-15) | **CLOSED** *(upgraded from MOSTLY CLOSED 2026-08-03)* — single URL-encode, applied last, both directions. The 2-pass decode and the Base64 repair are **not** in the spec, but the vendor's own §6.2 sample contains the bug that *creates* the corruption the repair fixes. The pass-count question is now settled on its own merits — **not** deferred to B5 — and legacy's plaintext validation has been ported. See the §5.4 addendum. |
| 5 | Callback response contract (D-4) | **STILL OPEN** — the most valuable question is the one the PDF does not answer. No RC enumeration for the partner's response, no retry semantics, no unparseable-response behaviour. It neither authorises nor forbids our G998/9999. |
| 6 | 00302 timing / idempotency | **SPLIT** — timing **CLOSED** (a precondition, not a settling period; our AFTER_COMMIT fan-out satisfies it). Duplicate-00302 handling **STILL OPEN** (total silence). |
| 7 | Fixed-length layout | **STILL OPEN** — not in this PDF at all; §1.2 delegates to `글로벌 VAN_펌뱅킹_배치파일 레이아웃.xlsx`, which is **not in the handover folder**. The independent vendor-side check of `FixedLengthCodec` is not possible from this document. |
| 8 | Other contradictions | **9 findings** — 1 High (read timeout), 3 Medium, 5 Low/informational. See §9. |

**Net new value of this pass:** three items no prior document had — the **§3.2 30-second SLA**, the **§3.8 sequence diagram** (image-only, never transcribed), and the discovery that the **§6.2 sample's `EncryptAesBase64` URL-*decodes* on the encrypt path**, which is the documented mechanism behind legacy's Base64 repair map.

---

## 1. What the two existing docs already extracted — do not redo

Both were written from this same PDF on 2026-06-08. Between them they already cover the following; this pass verified their claims and found no errors of fact.

### 1.1 `efs-api-guide-1.2.6-diff-audit.md` (the v1.2.2 → v1.2.6 delta)

Already extracted, confirmed accurate:

- **Full revision history** 1.2.2 → 1.2.6 including the two undated in-version revisions (2025-08-25 sample-method rename; **2026-02-11** the substantive bulk-API revision).
- **Transport/envelope/crypto unchanged** from v1.2.2 — AES-256/CBC/PKCS5Padding, 16 × `0x00` IV, `EM=AES` / `VM=HmacSHA256`, `RC=0000` / `G999`.
- **Per-API deltas:** 201 (`finc_mda_owner_id` → `finc_mda_owner_nm`, response `ant_div_cd`, CITAD-not-possible memo); 202 (`ant_div_cd`, `tar_bank_nm`, the 8-field FX block); 203 (no structural change); **204 newly documented at §5.6**; 301/302 (`data_amt` 23 → 22, `bank_cd` re-defined as 펌뱅킹 계약 은행코드); 01301 (`result_cd` + `result_msg` added to **both** Request and Response).
- **SFTP PROD IP change** `42.96.55.10` → `42.96.55.18`.
- The finding that **legacy `WECMS_BATCH` already implements every v1.2.6 change** — the document was catching up to production, not the reverse.
- **OQ-PDF-1 (does 204 exist) and OQ-PDF-3 (SFTP DEV vs PROD)** — both closed, correctly.
- §6 records the refactor's 204 implementation (`Efs204Request/Response`, `EFS_204` mapping, the Woori/non-VND branch in `RealtimeTransferService`).

### 1.2 `efs-service-mapping-from-pdf.md` (the service/field reference)

Already extracted, confirmed accurate:

- **Complete service inventory** and the §-number → `.jct` → `API_TRAN_TP` → legacy-program matrix, including the correct clarification that WeCMS's `01301`/`01302` are **Return-URL callbacks**, not separate `.jct` endpoints.
- **DEV/PROD base URLs**, path pattern `/WGVN_EFS_{code}.jct`, SFTP hosts + `~/sdata/` / `~/rdata/`, operating hours 00:15–23:50.
- **Crypto §4 — definitive and correct**: EV = `urlEncode(Base64(AES(RQ_DTIME(14) + json)))`, VV = `urlEncode(Base64(HMAC-SHA256(json)))` (HMAC over JSON only), decrypt → `.substring(14)`, `CcSecurity_jdk1.8-1.0.3.jar`, the published TEST key.
- **Verbatim latin field tables** for every service (§5), including the 204 request/response set.
- The **§6.2 `VerifyMac` inverted-boolean quirk** (returns `false` on equal) — correctly flagged as "do not copy verbatim".
- **[참조 2] error-code appendix** and the correct observation that some `Gxxx` codes in our `WCConst` are **WeCMS-internal reinterpretations**, not gateway codes (OQ-PDF-4).
- Environment-specific values (partner ID, `CRYPT_KEY`, callback return URLs) and their B1/B2/B4/B7 dispositions.

### 1.3 What those two docs explicitly say the PDF does **NOT** cover

Reproduced here so this pass is not read as re-opening settled ground. All five were re-verified against the PDF text and **all five hold**:

| Their claim | Re-verified? | Notes from this pass |
|---|---|---|
| **K3 / OQ-PDF-5** — the PDF documents the API/JSON layer, **not** the SFTP fixed-length record layout | ✅ Holds | Confirmed and strengthened — see §7. The delegation is explicit, not merely an omission. |
| **K9 / OQ-PDF-6** — no gateway-side recovery if a 01301/01302 callback is lost (contrast 202→203) | ✅ Holds | Confirmed. Zero occurrences of 재시도 / 재전송 / retry in 44 pages. §3.8's diagram draws no retry arrow. |
| **OQ-PDF-4** — gateway `Gxxx` set vs internal `WCConst` needs reconciling | ✅ Holds | This pass makes it concrete and *outbound* — see §5 and finding F-2. |
| **OQ-PDF-2** — whether our partner instance is served by `dev-van-gw` + the IP whitelist | ✅ Holds | The PDF contains no IP allow-list concept at all — see §2. |
| **B2 / B4 / B7** — real `CRYPT_KEY`, SFTP private key, callback exposure | ✅ Holds | Unchanged; all three are provisioning items, not document items. |

### 1.4 One correction to the prior extraction method

`efs-service-mapping-from-pdf.md` carries a prominent caveat:

> "the PDF body is Korean rendered with embedded **CID subset fonts that carry no ToUnicode map**, so the Korean prose … is **NOT text-extractable** … Therefore … every 'Purpose' is *inferred*."

**That caveat is stale for v1.2.6.** The Korean in this file extracts cleanly and completely with `pdftotext -enc UTF-8 -layout`. The caveat was true of the v1.2.2 file and was carried forward when the doc was retitled. Consequences:

- Every *(inferred)* "Purpose" in that document's §3 matrix can now be replaced with the verbatim Korean one-liner that heads each §5 subsection (e.g. §5.6 = `우리은행 전용 환전 이체 전 적용 환율 정보 조회 용도` — "for inquiring the applied FX rate before a Woori-exclusive currency-exchange transfer").
- All Korean MEMO columns, the §2/§3 procedural sections, and the two 주의 boxes are readable — which is how this pass found §3.2 and §5.4.
- **The remaining genuinely unreadable content is vector/raster, not font-encoded**: §3.8's flow diagram is a 1070×1270 raster image, and **[참조 1] 은행코드 is an empty table** (see finding F-6).

No change is required to those documents for this pass to stand; the note is recorded so a future reader does not re-adopt the "Korean is unreadable" assumption.

---

## 2. Q1 — CHARSET (C-13 / K3)

### 2.1 Request/response bodies — **CLOSED: UTF-8**

**§1.2 기본정보** (p.6) states it in the basic-information table:

```
Protocol        HTTPS              데이터형식        String
Network         인터넷망 / 전용선     데이터 제공방식     Return Url
Charset         UTF-8                        Method  GET / POST
```

> **Translation:** Protocol HTTPS · Data format String · Network internet line / leased line · Data delivery method Return URL · **Charset UTF-8** · Method GET / POST.

Corroborated throughout §6:

- **§6.2 C#** (p.36) — `wReqFirst.ContentType = "application/x-www-form-urlencoded; charset=UTF-8";`
- **§6.2** — `int utf8Codepage = 65001; System.Text.Encoding euckrEncoding = System.Text.Encoding.GetEncoding(utf8Codepage); byte[] result = euckrEncoding.GetBytes(postParams);` (the variable is misnamed `euckr`; codepage 65001 **is** UTF-8)
- **§6.2** — response read as `new StreamReader(respPostStream, Encoding.UTF8)`
- **§6.2 crypto** — `aes.Key = Encoding.UTF8.GetBytes(key)`, `Encoding.UTF8.GetBytes(Input)`, `Encoding.UTF8.GetString(xBuff)`, `HttpUtility.UrlEncode(shaInput, Encoding.UTF8)`
- **§6.1 Java** — the C-comment block above `EncryptAesBase64` reads `* Base64 변환 및 URL 인코딩(UTF-8)` ("Base64 conversion and URL encoding (UTF-8)")

**Our implementation is conformant.** `EfsCryptoService` pins `StandardCharsets.UTF_8` for URL-encode/decode and HMAC/AES key bytes; `RestClientEfsApiClient` sends `MediaType.APPLICATION_FORM_URLENCODED`.

> One asymmetry in the vendor's own samples, for the record: **§6.1 Java omits the charset** — `new BufferedWriter(new OutputStreamWriter(con.getOutputStream()))` uses the JVM platform default, and the `Content-Type` it sets is `" application/x-www-form-urlencoded"` (with a stray leading space, no `charset` parameter). Only the C# sample declares UTF-8 explicitly. The §1.2 table is the normative statement; the Java sample is simply sloppy. Worth knowing because **legacy WECMS_BATCH copied that Java sample**, which is the root of the platform-default-charset uncertainty recorded in `FixedLengthCodec`'s Javadoc.

### 2.2 Fixed-length H/D/T file — **STILL OPEN**

The PDF says nothing about the file's encoding, because it says nothing about the file at all. **§1.2** ends with an explicit delegation (p.6):

```
※ SFTP 파일 처리 업무는 관련 문서(글로벌 VAN_펌뱅킹_배치파일 레이아웃 .xlsx) 문서 참조
```

> **Translation:** "※ For SFTP file-processing work, refer to the related document (Global VAN_Firm Banking_Batch File Layout.xlsx)."

That `.xlsx` is **not in `WECMS_BATCH DEV/handover/`** (the folder holds the PDF, the nine `00~08_*.md` files, and two HTML/MD handover exports — nothing else). So the codepage question the handover's §4.4 left open as "CP/UTF-8 byte 기준 패딩" is *not* closable from any document currently in our possession.

**C-13 therefore stands exactly where `FixedLengthCodec`'s Javadoc left it** — and that Javadoc's own framing is confirmed correct by this pass:

> "The bank's accepted charset on this channel is **unconfirmed**: the UAT samples prove only that real files have been ASCII, not that non-ASCII is refused."

### 2.3 Non-ASCII handling / is the channel ASCII-only by contract? — **PDF is silent; the hypothesis is neither supported nor refuted**

- The string `ASCII` appears **zero times** in the document.
- `특수문자` (special characters), `한글` (Korean text), and any statement restricting the character repertoire: **zero occurrences**.
- There is no "the file must be ASCII" clause, and no clause permitting non-ASCII either.

The **one** byte-related data point in the whole document is in the revision history (p.2), and it cuts *against* an ASCII-only reading of the API layer:

```
5.4. 펌뱅킹 입금이체 / 5.3 펌뱅킹 예금주조회 이름항목 길이 변경
(200->150Byte)
```

> **Translation:** "§5.4 Firm-banking deposit transfer / §5.3 firm-banking account-holder inquiry — name field length changed (200 → **150 Byte**)."

Field widths in this spec are therefore **byte** widths, not character widths, which is only a meaningful distinction if multi-byte content is contemplated. This is an API-layer statement (`finc_mda_owner_nm` in the EV JSON) and says nothing about the SFTP file — but it does mean **"ASCII-only by contract" cannot be asserted for the API layer**, and the `String(150)` DTO fields must be validated in **bytes**, not `String.length()`.

> **Action item for a later pass (not a finding — needs verification I did not do here):** confirm that the byte-width validation applied to `Efs202Request.fincMdaOwnerNm` / `tranUse` / the 203 memo fields measures UTF-8 bytes against 150/200, not characters. `FixedLengthCodec.put` does this correctly for the *file*; the JSON DTO path is a different surface.

**Verdict:** API/JSON bodies **CLOSED (UTF-8)**. Fixed-length file charset **STILL OPEN** — needs `글로벌 VAN_펌뱅킹_배치파일 레이아웃.xlsx`, which should be requested from Mr. Kang alongside the K3 sample-file work. ASCII-only-by-contract: **unsupported**; do not adopt it as an assumption.

---

## 3. Q2 — HTTP headers (C-8)

### 3.1 What the spec requires — **CLOSED**

**Exactly one header appears anywhere in the 44 pages.** §6.1 Java (p.34):

```java
con.setRequestMethod("POST");
con.setRequestProperty("Content-Type", " application/x-www-form-urlencoded");
```

and its §6.2 C# twin (p.36):

```csharp
wReqFirst.Method = "POST";
wReqFirst.ContentType = "application/x-www-form-urlencoded; charset=UTF-8";
```

### 3.2 The three headers you asked about — **all absent**

Exhaustive search over the extracted text:

| Token | Occurrences in the PDF |
|---|---|
| `x-coocon-accessIp` | **0** |
| `coocon` (any casing) | **0** |
| `Authorization` | **0** |
| `signature` / `Signature` | **0** |
| `whitelist` / `화이트리스트` / `허용` / `아이피` | **0** |
| `IP` (uppercase) | **1** — and it is not about access control |

The single `IP` hit is the revision-history line recording the SFTP host change (p.3):

```
SFTP 운영 Server IP 수정
42.96.55.10 -> 42.96.55.18
```

> **Translation:** "SFTP production Server IP corrected: 42.96.55.10 → 42.96.55.18."

### 3.3 Are any documented as validated?

No — because none are documented at all. The spec's **entire** stated security model is body-level plus transport, set out in §1 개요 (p.6):

```
웹케시 베트남 전자금융서비스는 ActiveX 등 부가적인 솔루션 없이 오픈브라우져에서 얼마든지
호출하여 사용할 수 있으며, 256bit 대칭키암호화와 SSL 구간 암호화를 통하여 2 중 보안 처리를
함으로써, 안전하고 신뢰성 있는 인터넷 전자금융서비스를 도와주는 것을 목적으로 하고 있습니다.
```

> **Translation:** "The Webcash Vietnam electronic-financial service can be called and used freely from an open browser without any additional solution such as ActiveX, and by applying **double security processing through 256-bit symmetric-key encryption and SSL-segment encryption**, it aims to support a safe and reliable internet electronic-financial service."

Two layers: **AES-256 on the body (EV) + HMAC integrity (VV)**, and **TLS on the wire**. Nothing else. §2.1 기관정보 생성 lists the four onboarding steps (p.7–8) and the security artifacts issued are, verbatim, `④ 기관정보 등록 및 암복호화 키 발급` ("register institution information and **issue the encryption/decryption key**") — a key, not a credential header and not an IP registration.

### 3.4 Source-IP allow-listing / registration

**No mention.** The only registration ceremony the document describes is **SFTP public-key registration** (§1.2, p.6):

```
※ 이용사 서버 인증서 생성 명령어.
Key 생성 명령어 : ssh–keygen –t rsa
Key 파일 확인 : ~/.ssh/id_rsa.pub
```

> **Translation:** "※ Command to generate the partner server's certificate. Key generation command: `ssh-keygen -t rsa`. Key file location: `~/.ssh/id_rsa.pub`."

That is SFTP authentication, not HTTPS source-IP control.

### 3.5 Verdict and implications

**CLOSED (negatively).** The spec requires `Content-Type: application/x-www-form-urlencoded` and nothing more. **`RestClientEfsApiClient` sending only `Content-Type` is spec-conformant** — this is not a gap to fill.

Three consequences worth recording:

1. **The handover's §4.1 claim is now corroborated at the document level.** `04_인터페이스_연동명세서.md:15` asserts *"`Authorization`/`signature` 헤더는 게이트웨이가 검증하지 않는다 … 두 헤더는 사실상 미사용(레거시 상수)이며 … 이관 시 제거해도 무방"* ("the gateway does not validate the Authorization/signature headers … both are effectively unused legacy constants … safe to remove on migration"). That was an empirical claim from reading legacy code; the vendor spec never asks for them, which is independent support. **Dropping them in the refactor was correct.**
2. **`08_기능추출_이관가이드.md:77` remains the honest position on the IP question**: *"항목 5의 `x-coocon-accessIp` IP 화이트리스트 여부도 '인프라 확인 권장'으로 남아 있어 **엄밀히는 미확인**이다"* ("whether there is an `x-coocon-accessIp` IP whitelist also remains at 'infrastructure check recommended' — **strictly speaking unconfirmed**"). **Absence from the guide is not proof of absence at the infrastructure layer.** A network-level allow-list can exist without appearing in an API integration guide. **OQ-PDF-2 stays open** and is an infra question for Webcash, not a document question.
3. **Do not conflate directions.** Our `CallbackIpWhitelistFilter` (+ `wecms.dispatch.callback.*` in the SIT/UAT/PROD profiles) protects the **inbound** 01301/01302 endpoints — that is our own defence and is unrelated to what the gateway demands of our **outbound** calls. Nothing in this section touches it.

---

## 4. Q3 — Timeouts and SLA (C-7)

This is the question where the PDF materially corrects us.

### 4.1 §3.2 — the SLA exists, and it is 30 seconds

**§3.2 전문 응답시간** (p.8–9), quoted in full:

```
3.2 전문 응답시간

웹케시 베트남 과 이용사의 전문교환을 신속하게 하기 위하여 시스템 특성에 맞는 감시시간 (전문전송 및 응답에
대한 한계 시간)을 다음과 같이 설정 운용한다.

 구분                                설명                      시간
이용사  요청전문 전송완료로부터 응답전문 수신 완료까지                             30 초
```

> **Translation:**
> **3.2 Message response time**
> "In order to keep message exchange between Webcash Vietnam and the partner fast, a **monitoring time** (the **limit time** for message transmission and response) suited to the system characteristics is configured and operated as follows."
>
> | Category | Description | Time |
> |---|---|---|
> | Partner (이용사) | From completion of request-message transmission to completion of response-message reception | **30 seconds** |

Two words matter: **감시시간** (monitoring time) and **한계 시간** (limit time). This is the vendor's stated response-time envelope for the partner side of the exchange — the only normative timing figure in the document.

### 4.2 §3.4 — the section named "Time Out" contains no number

**§3.4 URL 통신의 Time Out 처리** (p.9) is a diagram with one line of prose:

```
3.4 URL 통신의 Time Out 처리

웹케시 베트남으로 펌뱅킹 입금이체 API 전송한 후 응답이 경우

고객   이용사  무응답  웹케시글로벌                                 무응답  은행
                   베트남
```

> **Translation:** "**3.4 Time-out handling for URL communication** — When there is [no] response after sending the firm-banking deposit-transfer API to Webcash Vietnam. [Diagram:] Customer → Partner → *no response* → Webcash Global Vietnam → *no response* → Bank."
>
> (The prose is missing a word — almost certainly `응답이 **없는** 경우`, "when there is no response". The sentence is incomplete in the source PDF.)

The section titled "Time Out handling" **specifies no timeout value**. It illustrates the no-response propagation chain and hands off to §5.4/§5.5 for the recovery procedure. So §3.2's 30 s is the only number, and §3.4 confirms it is about the *round trip*, not a per-socket setting.

### 4.3 §6.1 — where our 3 s / 20 s actually came from

**§6.1 JAVA** (p.34):

```java
URL req = new URL(url);
con = (HttpURLConnection)req.openConnection();
con.setConnectTimeout(3 * 1000); // 3 초
con.setReadTimeout(20 * 1000); // 20 초
```

> **Translation of the comments:** `// 3 seconds`, `// 20 seconds`.

And the banner that opens §6 (p.34), which governs both samples:

```
***** 주의 *****
웹케시 베트남에서 제공하는 소스는 단지 샘플일 뿐이며, 반드시 그대로 사용하여야 하는 것은 아닙니다.
이 샘플을 참조하여 해당 웹 페이지 Code 등을 제작하시면 됩니다.
```

> **Translation:** "***** CAUTION ***** The source provided by Webcash Vietnam is **merely a sample and does not have to be used as-is**. Please refer to this sample when producing your own web-page code."

So `EfsProperties`' Javadoc — *"Connect timeout (ms). PDF §6 sample uses 3 s"* / *"Read timeout (ms). PDF §6 sample uses 20 s"* — cites its source accurately, but that source is the one the vendor explicitly marks non-binding, while **the binding number in §3.2 is not referenced anywhere in our code or in `application-{sit,uat,prod}.yml`**.

### 4.4 Retry guidance

**There is none, and that is deliberate.** Zero occurrences of `재시도`, `재전송`, `retry`, `Retry` in the document. The only recovery instruction in the entire spec is the 주의 box closing **§5.4** (p.20) — reproduced verbatim because it is the single most operationally important paragraph in the guide:

```
※[주의]
『펌뱅킹 입금이체(WGVN_EFS_202.jct) 』 요청 후 응답을 받지 못하거나 은행 무응답 코드 "G999" 를 수신하는 경우
반드시 『펌뱅킹 이체 처리 결과 조회(WGVN_EFS_203.jct)』를 수행하여 입금 이체 결과를 확인하여야 한다. 입금
이체의 결과가 확인 되지 않은 상태에서 재 거래 시 이중 이체 거래가 발생 할 수 있다.
```

> **Translation:** "**※[CAUTION]** If **no response is received** after a 『Firm-banking deposit transfer (WGVN_EFS_202.jct)』 request, **or** the bank no-response code **"G999"** is received, you **must** perform 『Firm-banking transfer processing-result inquiry (WGVN_EFS_203.jct)』 to confirm the deposit-transfer result. **Re-transacting while the deposit-transfer result is unconfirmed can produce a double-transfer transaction.**"

Repeated as the opening line of **§5.5** (p.21). Note the scope precisely: **"응답을 받지 못하거나"** — "when a response is *not received*" — a client-side timeout is explicitly one of the two triggers, ranked alongside an explicit `G999`. The prescribed action is **confirm via 203, never blind-resend**.

### 4.5 Verdict

**CLOSED — with a correction.**

| | Documented | Ours | Legacy |
|---|---|---|---|
| Round-trip limit (§3.2, normative) | **30 s** | — (not represented) | — |
| Connect timeout | 3 s (§6.1 sample, non-normative) | **3 s** | 30 s |
| Read timeout | 20 s (§6.1 sample, non-normative) | **20 s** | 60 s |
| Operating window (§3.3) | 00:15–23:50 daily | see F-7 | — |
| Retry | **none — confirm via 203 instead** | no resend ✅ | no resend ✅ |

**Our 20 s read timeout is below the vendor's own 30 s response-time limit.** That is not a style preference — it has a concrete failure mode the spec itself describes:

> A 202 the gateway answers at t = 25 s (inside its stated SLA) is aborted by our client at t = 20 s. We now hold an **indeterminate** transfer. §5.4 then obliges a 203 confirmation, and forbids resending. So the only effect of the short timeout is to **manufacture avoidable indeterminate states** on transfers that were about to succeed — each one costing a 203 round trip and a `PROC_STAT='6'` row that has to drain.

The `RealtimeTransportFailureNoResendTest` in the tree indicates we already honour the no-resend half of §5.4 correctly. The timeout is the remaining half.

**Recommendation (not applied — no code changed in this pass):** raise `read-timeout-ms` to **at least 30 000** in `EfsProperties` and the three profiles, so that a client-side abort means the gateway genuinely blew its SLA rather than that we were impatient. Legacy's 60 s sits safely above the limit; 30 s is the floor. `connect-timeout-ms: 3000` is fine as-is — §3.2 governs the message round trip, not TCP establishment. Update the `EfsProperties` Javadoc to cite **§3.2** as the source rather than the §6 sample.

---

## 5. Q4 — EV/VV encoding (C-15)

### 5.1 The normative statement — §4, both directions

**§4 전문구성 [Request DATA]** (p.11). The `Memo` column in the source lays out as a run-on block; mapped to its keys:

```
 EV       String      Mondatory   DateTime + JSONObject Data
 VV       String      Mondatory   -> 암호화
EM        String      Mondatory   -> BASE64
VM        String      Mondatory   -> URL Encoding
                                  HMAC hash value of JSON String Data.
                                  -> BASE64
                                  -> URL Encoding
                                  Algorithm type.
                                  AES
                                  MAC generration algorithm.
                                  HmacSHA256
```

> **Reading (the four memos in key order):**
> - **EV** — `DateTime + JSONObject Data` → **암호화** (encrypt) → **BASE64** → **URL Encoding**
> - **VV** — `HMAC hash value of JSON String Data.` → **BASE64** → **URL Encoding**
> - **EM** — `Algorithm type.` = `AES`
> - **VM** — `MAC generration algorithm.` = `HmacSHA256` *(vendor's typo for "generation")*

**§4 [Response DATA]** (p.12) repeats the identical memo block for `EV`/`VV`, plus `RC`/`RM`. So the encoding pipeline is stated to be **symmetric**: the same three steps, in the same order, in both directions.

**Answer to "single or double":** **single**, and applied **last** — after Base64, on the already-Base64'd string. Once for EV, once for VV.

### 5.2 The samples agree — single pass each way

**Outbound, §6.1 Java** (p.34) — the trailing `true` is `CcSecurityUtil`'s `isEncoding` flag:

```java
String EV = CcSecurityUtil.EncryptAes256Base64(trx_dt+trx_tm+param.toJSONString(), CRYPT_KEY, true);
String W  = CcSecurityUtil.getHmacSha256(param.toJSONString(), CRYPT_KEY, true);

String data = "ID=" + ID + "&RQ_DTIME=" + trx_dt+trx_tm + "&TNO=" + TNO + "&EV=" + EV
                         + "&VV=" + W + "&EM=AES" + "&VM=HmacSHA256";
```

**Inbound, §6.1 Java** (p.35):

```java
String decEV = CcSecurityUtil.DecryptAesBase64(rEV, CRYPT_KEY,true);
JSONObject jsonEV = JSONObject.fromObject(decEV.substring(14));
```

**Inbound, §6.2 C#** (p.38) — the decode is unambiguous and singular:

```csharp
public String DecryptAesBase64(String output, string key, bool isEncoding)
{
    string base64encStr = "";
    if (isEncoding)
       base64encStr = HttpUtility.UrlDecode(output, Encoding.UTF8); //URL디코딩
    else
       base64encStr = output;

    string aesOutput = AESDecrypt256(base64encStr, key);   //AES256복호화
    return aesOutput;
}
```

> **Translation of comments:** `//URL decoding`, `//AES256 decryption`.

**Exactly one `UrlDecode`, guarded by a boolean, then decrypt.** No second attempt, no fallback branch, no plaintext sanity check.

### 5.3 The one place double-encoding *does* arise — the `_JSON_` transport variant

§4 documents two body formats. The flat one is what the samples use. The other, **POST 방식** (p.11):

```
POST 방식
      키이름 '_JSON_'에 공통 전문구성데이터를 JSONString -> URLEncoding 하여 전송

      Ex) JSONObject param = new JSONObject();
     param.put("_JSON_", URLEncoding.encode({"ID":"00000000000000000000", "RQ_DTIME":"20240422103822",
"EV":"SADFASLJFSALJFALS908023", "VV":"2fsdf#$4534fsdffsd23JF92JFLSJFLSDFdf432F"}, "UTF-8"));
```

> **Translation:** "**POST method** — Send the common-envelope data under the key name `'_JSON_'` as a JSON string → URL-encoded."

In this variant the **whole envelope JSON — already containing once-URL-encoded EV and VV — is URL-encoded again**, so EV/VV arrive **double-encoded**. This is the only double-encoding the spec describes, and it is a property of the *transport variant*, not of EV/VV construction.

**We use the flat form.** `RestClientEfsApiClient.toForm(...)` adds `ID`/`RQ_DTIME`/`TNO`/`EV`/`VV`/`EM`/`VM` as discrete form fields, matching §6.1 exactly. **Correct — and do not switch to `_JSON_` without re-deriving the encoding depth**, since Spring's `FormHttpMessageConverter` would apply its own encoding pass on top of a manually pre-encoded value.

### 5.4 The 2-pass URL decode — **nothing in the spec matches it**

Legacy `SecurityUtil.DecryptAesBase64:263-319` attempts decryption twice (without URL-decoding, then with), validating the plaintext between attempts. **The PDF contains no analogue.** Its decrypt is the single unconditional path quoted in §5.2 above.

`08_기능추출_이관가이드.md` §8.4 pitfall ③ warns *"무결성 VV 2-pass: URL디코딩 유/무 두 번 시도. **한쪽만 구현하면 간헐적 검증실패**"* ("Integrity VV 2-pass: attempt twice, with/without URL-decoding. **Implement only one side and you get intermittent verification failures**"). The spec neither confirms nor contradicts it — the spec simply describes the clean case.

**`EfsCryptoService`'s current stance is correct and should not change on the strength of this pass**: single self-consistent pass, with the reasoning gated on B5 ("Do not speculatively add passes"). The PDF does not supply the evidence B5 needs.

#### Addendum 2026-08-03 — the pass count is settled; the validation is ported. **B5 no longer gates this.**

A dedicated legacy read (C-15, approved by Sonny) resolved the pass count **without** a captured sample, because the deciding fact is in the legacy source rather than on the wire.

**Finding — the ladder exists to compensate for an ordering defect we do not have.** Legacy repairs the Base64 alphabet **before** its decode (`SecurityUtil:269-271`, then `:411`); `EfsCryptoService` repairs **after** the URL-decode. For a plain, **not**-URL-encoded standard-Base64 `EV` containing a `'+'`:

| | pipeline | result |
|---|---|---|
| legacy pass 1 | repair (no-op) → no URL-decode → commons-codec | ✅ correct bytes |
| legacy pass 2 | repair (no-op) → `URLDecoder` `'+'`→space → commons-codec **discards** the space | ❌ **wrong bytes, no error** |
| ours | `urlDecode` `'+'`→space → repair space→`'+'` → strict decode | ✅ correct bytes |

So legacy's pass 2 is *broken* for the exact input class its pass 1 exists to serve. **Legacy needs two passes because each handles only one input class; ours handles both.** Six input classes were traced (URL-encoded; plain standard Base64; base64url ±URL-encoding; a `'+'` form-decoded to a space; a `%2B` double-form-decoded to a space) — all six decode correctly in the single pass, including the two legacy's pass 2 corrupts. Porting the ladder would mean reproducing legacy's ordering bug in order to reproduce its workaround.

**Also relevant: our decoder is stricter than legacy's.** `java.util.Base64.getDecoder()` **throws** on non-alphabet characters; commons-codec `decodeBase64` **discards** them. The specific silent success legacy's `:278-279` comment describes (a `%`-bearing URL-encoded payload decrypting "successfully" as-is) therefore cannot occur here — it fails at the decoder.

**What WAS ported: the plaintext validation** (`SecurityUtil:278-293` → `EfsCryptoService.assertPlaintextWellFormed`). Its value is independent of the encoding, and the dependency runs one way only: porting the *pass count* would have required the validation, but the validation stands alone. It asserts a 14-digit prefix (`Long.parseLong`, as legacy) plus a JSON **object** remainder (`ObjectMapper.readTree` + `isObject()`, the faithful equivalent of legacy's `JSONObject.fromObject`). It catches the class the strict decoder cannot see: **alphabet-clean, correctly padded, decrypts to garbage — a wrong key, a truncated ciphertext, a mis-split envelope field.**

Two things deliberately **not** ported:

- **Legacy's `isPaddingNum == false` branch** (`:291-293`) is defective — it validates the *whole* string as JSON, certifies it intact, and then every caller does `.substring(14)` and chops 14 characters off it. Only the `true` branch is ported.
- **`VerifyMac`'s nesting.** `VerifyMac:534-568` runs two passes, each calling `VerifyMac2:579-591`, which calls the already-2-pass `DecryptAesBase64` — up to **four** decrypt attempts per verify. Accreted patching, not a designed ladder: (i) `VerifyMac2:581` forwards `urldecode` into `DecryptAesBase64`, so the outer pass 1 *enables* the inner 2-pass while the outer pass 2 *disables* the inner pass 1 via the `else` at `:294` — asymmetric nesting depth between the outer passes; (ii) `:582` also uses `urldecode` as `getHmacSha256`'s `urlencode` flag, then `:585-586` `URLDecoder.decode`s **both** sides, largely neutralising the toggle it just introduced. `verifyVv` stays flat and inherits the validation via `catch (RuntimeException) → false`.

**Deliberate divergence recorded.** We now throw where legacy returned garbage: a response whose prefix is not 14 digits. **Watch item for the first SIT run** — if the gateway ever omits `RQ_DTIME` on a *response*, we begin rejecting envelopes the previous build accepted-and-garbled (surfacing as `G003` on the callback legs, gateway error on the outbound legs). Better outcome, but it should be *recognised* on the first SIT run rather than later debugged as a mystery `G003`.

**Pitfall ③ re-read.** `08_기능추출_이관가이드.md` §8.4's *"한쪽만 구현하면 간헐적 검증실패"* is a warning about **legacy's two-input-class design**, where implementing one pass genuinely does drop one class — **not about ours**, where one pass covers both. There is no unresolved intermittent-failure risk behind this item.

**Net effect on B5:** the pass count drops off B5's dependency list. A captured sample could only have revealed whether a counterparty sends `EV` not URL-encoded, which classes 2/4/5/6 already cover. B5's remaining dependencies — the `BT00`/`BT01`/`C999` reception result-code set and **D-4** — are unaffected and stay open.

Tests: `EfsCryptoServiceTest` 6 → 12, `EfsCryptoBase64RepairTest` 5 → 6 (the new repair case pins input class 2, which is also the guard against anyone moving the repair back ahead of the URL-decode). Full suite green — 2 699 tests, 0 failures.

### 5.5 The Base64 repair — not in the spec, but the spec's own sample explains it

Legacy applies `' '→'+'`, `'_'→'/'`, `'-'→'+'` before Base64-decoding. **Zero occurrences of any such repair in the PDF.** However — and this is new to this pass — **the vendor's §6.2 sample contains the bug that produces exactly that corruption.** §6.2 C# FUNCTION (p.38):

```csharp
/***********************************
* 전송할 데이터 생성
* Base64 변환 및 URL 인코딩(UTF-8)
***********************************/
public String EncryptAesBase64(String input, string key)
{
   string aesInput = AESEncrypt256(input, key);  //AES256암호화
   return HttpUtility.UrlDecode(aesInput, Encoding.UTF8); //URL디코딩
}
```

> **Translation:** Header comment: *"Generate the data to transmit — Base64 conversion and **URL encoding** (UTF-8)"*. Body comments: *"AES256 encryption"*, then *"**URL decoding**"*.

**The function's own header comment says URL *encoding*; the code performs URL *decoding*.** Running `UrlDecode` over a raw standard-Base64 string converts every `+` into a **space** (and would consume any `%XX` sequence). That is precisely the corruption `' ' → '+'` repairs.

The sibling function is inconsistent with it — `EncryptHmacSha256` (p.39) does `return HttpUtility.UrlEncode(shaInput, Encoding.UTF8);`, the correct direction. So in the vendor's reference implementation **EV and VV are encoded in opposite directions.**

This is a third defect in §6.2, joining the two already known (`VerifyMac` returning `false` on equality — p.41; the `euckr`-named UTF-8 variable — p.36). It does **not** change what §4 mandates. What it does is give a **documented mechanism** for the corruption legacy compensates for:

- **`repairBase64Alphabet` is well-founded.** Porting it (audit D-8) was right, and this finding is affirmative evidence for keeping it — a counterparty built on the vendor's own sample emits space-corrupted Base64.
- **It sharpens the 2-pass hypothesis without settling it.** Legacy's comment at `SecurityUtil:278-279` — *"데이터 보호화시 URLEncoding데이터가 그대로 복호화가 성공적으로 되는 경우가 있어서"* ("there are cases where URL-encoded data decrypts 'successfully' as-is") — is consistent with a counterparty whose encode-side URL handling is unreliable in exactly this way. Still not proof. **B5 remains the closer.**

### 5.6 Verdict

**MOSTLY CLOSED.**

| Item | Status |
|---|---|
| Encoding depth, outbound | **CLOSED** — single URL-encode, applied after Base64, on EV and VV independently (§4 memo; §6.1/§6.2) |
| Encoding depth, inbound | **CLOSED** — single URL-decode before Base64-decode (§6.2 `DecryptAesBase64`) |
| Order of operations | **CLOSED** — EV: `datetime(14) + json` → AES → Base64 → urlEncode. VV: `json` → HMAC-SHA256 → Base64 → urlEncode |
| Double encoding | **CLOSED** — occurs only in the `_JSON_` transport variant, which we do not use |
| Legacy 2-pass decode | **CLOSED** *(2026-08-03 — was "STILL OPEN … B5 decides")* — no analogue in the spec, and none needed: legacy's ladder compensates for its repair-**before**-decode ordering, which we do not share. Single pass kept; legacy's plaintext validation ported. See the §5.4 addendum. |
| Base64 alphabet repair | **Not specified — but now mechanistically explained** by the §6.2 `UrlDecode`-on-encrypt bug. Keep the port. |

---

## 6. Q5 — Callback response contract (D-4)

**The most valuable question is the one the PDF does not answer.**

### 6.1 What the spec does give

**§5.11 대량이체 파일 접수 결과 통지(이용사 수신 URL)** — 01301. Header line (p.30):

```
5.9 대량이체 파일 접수 요청에 대한 접수 결과.(이체 전 접수 결과와 요청 결과 확인 용도)
```

> **Translation:** "The reception result for the §5.9 bulk-transfer file reception request. (For confirming the pre-transfer reception result and the request result.)"

`[Response Data]` — what **we** send back (pp.30–31):

```
bank_cd         String(15)  Mondatory   펌뱅킹 계약 은행코드 / 은행기관코드
prtn_id         String(20)              은행에서 발급 받은 Partner ID
file_nm         String(50)  Mondatory   파일명
data_cnt        number(7)   Mondatory   접수 건수
data_amt        number(22)  Mondatory   접수 금액 정수
data_dcm_amt    number(3)   Mondatory   접수 금액 소수
result_cd       String(4)   Mondatory   접수 결과 코드
result_msg      String(150) Option      접수 결과 메시지
```

> **Translation:** bank code (firm-banking contract bank code / bank institution code) · Partner ID issued by the bank · file name · **reception count** · **reception amount, integer part** · **reception amount, decimal part** · **reception result code** · **reception result message**.

**§5.13 대량이체 실행 결과 수신 요청(이용사 수신 URL)** — 01302 (p.33). `[Response Data]` is the same six fields with `file_nm String(400)` and **no** `result_cd`/`result_msg`; `data_cnt`/`data_amt` are relabelled `정상 처리 건수` / `정상 처리 금액 정수` ("successfully-processed count / amount").

**Critical reading:** these are the **EV business-payload** fields we echo. In §5.11, `result_cd`/`result_msg` are labelled **접수 결과 코드/메시지 — "*reception* result code/message"**, i.e. the **echo of the gateway's own reception outcome**, not our processing verdict. The 2026-02-11 revision log confirms they were added to **Request and Response as the same pair**:

```
5.10 대량이체 파일 접수 결과 통지(이용사 수신 URL)
[Response Data] 항목 추가
- 접수 결과 코드(result_cd)
- 접수 결과 메세지(result_msg)
5.10 대량이체 파일 접수 결과 통지(이용사 수신 URL)
[Request Data] 항목 추가
- 접수 결과 코드(result_cd)
- 접수 결과 메세지(result_msg)
```

The envelope-level `RC` is separate, defined once in **§4 [Response DATA]** (p.12):

```
 RC       String(4)    Mondatory        응답코드
                                        0000-정상
                                        G999-무응답
                                        그외 오류
                                        3.7 OUTPUT EV 데이터 처리 로직 확인
RM        String       Mondatory        응답메세지
```

> **Translation:** "**RC** String(4) Mandatory — **Response code: 0000 = normal, G999 = no response, anything else = error.** Check the §3.7 OUTPUT EV data-processing logic. **RM** String Mandatory — Response message."

§4 is the generic 전문구성 (message composition) chapter and is not scoped to one direction, so a partner's callback response **does** carry `RC`/`RM`. Our `EfsEnvelope` shape is right.

### 6.2 Which RC values are defined, and what the gateway does with each

**Not answered.** §4 gives a three-way partition — `0000` / `G999` / "anything else" — written from the perspective of the **partner reading the gateway's** response. **[참조 2] 응답코드** (p.43) publishes the gateway's own code set:

```
코드                                       내용
0000  정상
G001  Basic information not registered.
G002  Basic information not registered.(Bank Info)
G997  An error occurred during banking communication.
G998  An error occurred during banking communication.
G999
```

> **Translation:** `0000` = 정상 (normal). The rest are printed in English as shown. **`G999`'s description cell is blank in the source** — §4 supplies its meaning (무응답 / no response).

**Nowhere does the document state which of these — if any — a partner may return in a callback response, nor what the gateway does on receiving each.**

### 6.3 Does a non-0000 RC cause a retry? How many times, at what interval?

**Not answered, and the silence is thorough:**

- `재시도` (retry), `재전송` (resend), `retry`/`Retry`: **0 occurrences** in 44 pages.
- No retry count, no interval, no back-off, no maximum-attempts figure anywhere.
- **§3.8's sequence diagram** (p.10 — a raster image, extracted and read for the first time in this pass) draws step 7 `접수 결과 통지 API (연동가이드 5.10 …)` from the G/W to the 이용사, and its response as a **plain dashed return arrow** back to the G/W and onward to the BLS Server. **No loop, no branch, no retry arrow, no error path.**

The diagram does establish one thing worth knowing: **our callback response is not fire-and-forget.** It is relayed 이용사 → 전자금융서비스 G/W Server → 전자금융서비스 BLS Server, i.e. it reaches the core system that owns the file's state. Whatever we put in `RC` is consumed by something.

### 6.4 What does the gateway do with an unparseable or non-envelope response?

**Not answered.** No statement about malformed responses, missing envelope fields, non-JSON bodies, or HTTP status codes on the callback direction. §3.6 defines HTTP-status semantics (`2XX` success, `4XX` partner's bad request, `5XX` Webcash error) but is written for the **partner-as-caller** direction and says nothing about the callback direction.

The **closest** thing to a usable inference is **§3.7 OUTPUT EV 데이터 처리** (p.9):

```
3.7 OUTPUT EV 데이터 처리

'공통전문 구성 OUTPUT DATA' 의 RC 값이 0000(정상)일 경우 '전문필드구성(EV)의 OUTPUT' 데이터가 유효하며,
그 외에는 'EV 값을 복호화 -> 데이터 파싱'은 불필요
```

> **Translation:** "**3.7 OUTPUT EV data processing** — When the `RC` value of the 'common-envelope OUTPUT DATA' is `0000` (normal), the 'message-field-composition (EV) OUTPUT' data **is valid**; **otherwise, 'decrypt the EV value → parse the data' is unnecessary**."

Written for the partner as receiver. **If the gateway applies the same rule symmetrically**, then a non-`0000` `RC` from us means the gateway **discards our echoed EV without parsing it**. That is an inference from a symmetry the document never asserts — record it as a hypothesis, not a finding.

### 6.5 A gap that matters more than the RC question: `result_cd`'s value set is undocumented

§5.11 marks `result_cd String(4)` **Mondatory** and names it 접수 결과 코드 — **but never enumerates its values.** Not in §5.11, not in [참조 2], not anywhere.

`BulkReceptionResultService:160` gates the **entire bulk flow** on one such value:

```java
private static final String RECEPTION_SUCCESS_CD = "BT00"; // TODO(B5): confirm full reception result-code set.
```

Search results for the 302-leg codes across the whole PDF:

| Token | Occurrences |
|---|---|
| `BT00` | **0** |
| `BT01` | **0** |
| `C999` | **0** |

**None of the three codes that drive our bulk state machine appear in the vendor specification.** They are legacy-observed literals from `FILE_DEPOSIT_TRANSFER_BATCH2.java`. The `TODO(B5)` on that line is exactly right, and this pass confirms the PDF cannot discharge it.

### 6.6 Verdict — **STILL OPEN**

The PDF **does not settle D-4**. It neither authorises nor forbids the non-`0000` branches. Stated plainly, so the decision is not mistaken for a closed one:

**Our current non-0000 outbound RCs, and what is known about each:**

| Branch | RC we send | In the vendor code set? | Meaning collision |
|---|---|---|---|
| Sibling fail-stop — pay-file already FAIL (`BulkReceptionResultService:269`) | **`G998`** | Yes | **Conflicts.** [참조 2] defines `G998` = *"An error occurred during banking communication."* Ours means "transfer-target ledger status failure". |
| Count/amount mismatch (`:298`) | **`9999`** | **No** | Not a gateway code at all — a WeCMS-internal `WCConst` value. |
| Reception `result_cd != BT00`, unmatched-and-not-progressed (`:279`, `:574`) | **`G999`** | Yes | Consistent (기타에러 / general error). |
| Escaped exception (`CallbackAck`) | `G002` / `G004` / `G999` | `G002` yes, **`G004` no** | `G002` wording aligns. `G004` is `WCConst`-only. |

**The specific risk, stated concretely:** two of these branches — `G998` (sibling fail-stop) and `9999` (amount mismatch) — fire on paths where **WeCMS successfully processed and durably committed the callback**. The file is already marked failed, the audit row is written, the transaction has committed. Answering non-`0000` tells the gateway *"I did not accept this notification"* about a notification we in fact fully accepted and acted on.

Both possible gateway behaviours are bad in different ways, and we cannot currently tell which we get:

- **If non-`0000` triggers a resend:** the duplicate callback arrives against a file no longer in the `(PROC_STEP=REQ, PROC_STAT=REQ)` state the handler looks up, so it falls through to `handleUnmatchedCallback`, which answers `0000` only when `alreadyProgressed` and otherwise `G999` — producing a second non-`0000` and, potentially, a loop. Every round is also a needless audit row.
- **If non-`0000` is terminal:** we have told the gateway something false. Harmless today, but it is a lie recorded in the counterparty's system for a file we handled correctly.
- **If §3.7 is symmetric:** the gateway drops the EV we carefully rebuilt over the six echo fields (`receptionAck(..., rebuildEv=true)`), making that work pointless on exactly the paths where it runs.

**Legacy's uniform `0000` is the safer default precisely because it makes our behaviour independent of the unknown.** It says "I received and processed your notification" — which on all four of our branches is true — and confines the *business* outcome to where it belongs.

**Recommendation (not applied — no code changed in this pass):** until B5 closes, return to legacy's shape on the envelope only:

- **`RC = 0000` whenever WeCMS durably recorded the callback**, including the sibling-fail-stop and amount-mismatch paths. Those are business outcomes about the *file*, not failures to accept the *message*.
- **Reserve non-`0000` for genuine "I could not process this"** — i.e. the `CallbackAck` escaped-exception path, where the transaction rolled back and the callback really is unprocessed and replayable.
- **Keep `G998` and `9999` exactly as they are in the persisted columns** — `TB_FIRM_FILE_MST.REG_RSLT_CD` and `TB_FIRM_BAT_API_TRAN.RS_CD`. They are correct, legacy-parity, and operationally valuable there. The question is *only* what goes in the envelope `RC`.
- If that change is made, `9999` and `G004` stop being emitted on the wire entirely, which also resolves finding **F-2** for free.

This is a behaviour change against a live counterparty and should go to Mr. Kang as a question before it goes into code. The precise question to ask is in §10.

---

## 7. Q6 — 00302 timing and idempotency

### 7.1 Is a settling period required between 01301 and 00302? — **No. CLOSED.**

**§5.12 대량이체 실행 요청 (WGVN_EFS_302.jct)**, header line (p.32):

```
5.9 대량이체 파일 접수 요청 건과 5.10 대량이체 파일 접수 결과 통지 수신 건의 확인 결과 정상인
경우 호출.
```

> **Translation:** "**Call when the verification result of the §5.9 bulk-transfer file reception request and of the received §5.10 bulk-transfer file reception-result notification is normal.**"

This is a **precondition on state**, not a delay. The trigger is "both confirmed normal" — nothing about elapsed time. Zero occurrences of `대기` (wait), `지연` (delay), or any minimum-interval figure.

> *(Note the section numbers in that sentence are the **v1.2.2** numbering. In v1.2.6 they are §5.10 and §5.11 — the prose was not renumbered when §5.6 was inserted. See finding **F-8**.)*

**§3.8's sequence diagram** (p.10) corroborates. The relevant span, transcribed from the image:

```
6.  접수 결과 통지 MSG            BLS Server ──▶ G/W Server
7.  접수 결과 통지 API            G/W Server ──▶ 이용사 Server      [= 01301]
      (연동가이드 5.10 대량이체 파일 접수 결과 통지)
    └── dashed response ─────────  이용사 ──▶ G/W ──▶ BLS
8.  대량이체 실행 요청 API         이용사 Server ──▶ G/W Server      [= 00302]
      (연동가이드 5.11 대량이체 실행 요청)
9.  대량이체 실행 요청 MSG         G/W Server ──▶ BLS Server
10. [note on BLS] 대량 이체 데이터 검증 - 건수, 금액 기준 대사
```

> **Translation of the step-10 note:** "Bulk-transfer data verification — **reconciliation on a count and amount basis**."

Steps 7 → 8 are drawn as **immediately consecutive**, with the 01301 response completing before 00302 is issued, and no wait annotation anywhere. Step 10 additionally confirms the gateway **re-reconciles `data_cnt`/`data_amt`** on the 302 leg.

**Our implementation is conformant and well-shaped.** `BulkReceptionResultService.scheduleExecutionFanOut(...)` registers the 00302 fan-out for **`afterCommit`**, so 302 is issued only once the reception row is durably `PROC_STEP=REQ_RECV` / `PROC_STAT=REQ` — which is exactly the spec's "확인 결과 정상" precondition, and matches the diagram's ordering (respond to 01301 first, then issue 00302 as a separate call). The K9 answer recorded from Mr. Kang on 2026-06-12 — *"normal 01301 API response = execution directive (inline 00302, no periodic sweep)"* — is consistent with both.

### 7.2 Duplicate 00302 requests — **STILL OPEN, total silence**

| Token | Occurrences in the PDF |
|---|---|
| `중복` (duplicate) | **0** |
| `멱등` (idempotent) | **0** |

Nothing states whether a repeated 00302 for the same `file_nm` is **deduplicated**, **rejected**, or **executed twice**. §5.12's Request/Response tables are symmetric six-field echoes with no transaction-id, no dedup key, and no "already processed" response code. `TNO` is described in §4 as *"제휴기관코드별 일자별 unique 한 Number"* ("a number unique per partner-institution code, per date") — it is a **message** identifier, not a business idempotency key, and nothing says the gateway dedups on it.

**The asymmetry is the finding.** The vendor:

- **warns explicitly** about double execution on the **realtime** leg — §5.4's *"재 거래 시 이중 이체 거래가 발생 할 수 있다"* ("re-transacting can produce a double-transfer transaction"); **and**
- **provides a tool** for it on that leg — `WGVN_EFS_203`, "did my 202 actually happen?"

For the **bulk** leg it provides **neither**. There is no warning and there is **no equivalent inquiry service** — no "was this file already executed?" query anywhere in the service list. Combined with §3.8's absence of any retry mechanism, this reinforces **OQ-PDF-6 / K9** in its strongest form: **duplicate suppression on the bulk leg is entirely the partner's responsibility, and the partner has no way to ask the gateway what state a file is in.**

Our guard is therefore the only guard. It is state-based — the fan-out filters siblings on `ProcStep.REQ_RECV` + `ProcStat.REQ` before issuing, and `BulkReceptionResultService.handle` looks up the file by `(PROC_STEP=REQ, PROC_STAT=REQ)` so a replayed 01301 cannot re-trigger. Given the gateway offers nothing, **local state is the right and only shape** — this is a case where the refactor's design is sound *because* the spec is silent, not in spite of it. `BulkExecutionIdempotencyDateRolloverTest` and `BulkReceptionConcurrentClaimTest` are guarding real risk.

### 7.3 Verdict

| | Status |
|---|---|
| Settling period between 01301 and 00302 | **CLOSED — none required.** Precondition is state ("both confirmed normal"), not elapsed time. Our AFTER_COMMIT fan-out satisfies it. |
| Duplicate 00302 handling (dedup / reject / double-execute) | **STILL OPEN — the PDF is silent.** No dedup statement, no idempotency key, and — unlike the realtime leg — no inquiry service to ask. Local state-based suppression remains mandatory. |

---

## 8. Q7 — Fixed-length H/D/T layout

### 8.1 Does the PDF contain the byte spec? — **No. STILL OPEN.**

It does not, and it says so. **§1.2**, quoted again for this context (p.6):

```
※ SFTP 파일 처리 업무는 관련 문서(글로벌 VAN_펌뱅킹_배치파일 레이아웃 .xlsx) 문서 참조
```

> **Translation:** "※ For SFTP file-processing work, refer to the related document (**Global VAN_Firm Banking_Batch File Layout.xlsx**)."

Corroborating search results across all 44 pages:

| Token | Occurrences |
|---|---|
| `Header` / `Trailer` (as record types) | **0** |
| `고정길이` (fixed length) | **0** |
| `레이아웃` (layout) | **1** — the delegation line above, and nothing else |
| Any record-length or field-offset figure | **0** |

The closest the document comes is **§3.8** step 5, a note on the BLS Server lifeline:

```
5. 대량이체파일 처리
   - 파일 포맷 및 데이터 체크
```

> **Translation:** "Bulk-transfer file processing — **file format and data check**."

Confirming that the gateway validates the format, without stating what the format is.

**The `.xlsx` is not in the handover folder.** Contents of `WECMS_BATCH DEV/handover/`: the PDF, the nine `00~08_*.md` files, `WECMS_BATCH_Handover.{md,html}`, `WECMS_BATCH_Technical_Analysis.md`, `WECMS_BATCH_인수인계.html`. No spreadsheet.

**Therefore the independent check you asked for — `FixedLengthCodec` against the *vendor contract* — cannot be performed from this document.** `OQ-PDF-5` / `K3` stand exactly as `efs-service-mapping-from-pdf.md` §11 recorded them.

### 8.2 Substitute check performed — clearly labelled as *not* the vendor contract

Since the vendor-side comparison is impossible, I ran the next-best available check: **`FixedLengthCodec` against `04_인터페이스_연동명세서.md` §4.4**, which was itself derived from the legacy `Deposit_Header` / `Deposit_Data` / `Deposit_Trailer` Java classes. This validates the refactor against **legacy source**, which is what the existing K3 work already claimed — it is a regression check, **not** independent confirmation against the bank.

**Result: exact match on all three records — zero discrepancies.**

**Header (`H`) — 9 fields**

| # | Handover §4.4 field | Len | Pad | `FixedLengthCodec.encodeHeader` | Match |
|---|---|---|---|---|---|
| 1 | 식별코드 | 1 | R(' ') | `ID_HEADER` "H" | ✅ |
| 2 | 은행코드 | 15 | R | `bankCode` | ✅ |
| 3 | 기관코드 | 20 | R | `institutionCode` | ✅ |
| 4 | 거래일자 | 8 | L('0') | `tranDt` | ✅ |
| 5 | 출금모계좌번호 | 30 | R | `withdrawalMoAccountNumber` | ✅ |
| 6 | 자금종류 | 1 | R | `fundType` | ✅ |
| 7 | 거래종류 | 1 | R | `transactionType` | ✅ |
| 8 | 회차정보 | 3 | L('0') | `iterationInfo` | ✅ |
| 9 | 예비(filler) | 919 | R | `filler` | ✅ |
| | **Σ** | **998** | | **998** + `\r\n` = 1000 | ✅ |

**Data (`D`) — 19 fields**

| # | Handover §4.4 field | Len | Pad | `FixedLengthCodec.encodeData` | Match |
|---|---|---|---|---|---|
| 1 | 식별코드 | 1 | R | `ID_DATA` "D" | ✅ |
| 2 | 순번 | 4 | L('0') | `seq` | ✅ |
| 3 | 입금계좌번호 | 30 | R | `depositAccountNumber` | ✅ |
| 4 | 통화코드 | 3 | R | `currencyCode` | ✅ |
| 5 | 이체금액 | 23 | L('0') | `transferAmount` | ✅ |
| 6 | 당타행구분 | 1 | R | `sameBankFlag` | ✅ |
| 7 | 타행망코드 | 1 | R | `otherBankNetworkCode` | ✅ |
| 8 | 카드계좌구분 | 1 | R | `cardAccountType` | ✅ |
| 9 | 입금은행코드 | 15 | R | `depositBankCode` | ✅ |
| 10 | 송금인성명 | 150 | R | `senderName` | ✅ |
| 11 | 수신인성명 | 150 | R | `receiverName` | ✅ |
| 12 | 입금계좌적요 | 210 | R | `depositAccountMemo` | ✅ |
| 13 | 출금계좌적요 | 210 | R | `withdrawalAccountMemo` | ✅ |
| 14 | 처리결과 | 1 | R | `processingResult` | ✅ |
| 15 | 응답코드 | 10 | R | `responseCode` | ✅ |
| 16 | 수취은행국가코드 | 2 | R | `receiptBankCountryCode` | ✅ |
| 17 | 송금통화코드 | 3 | R | `remittanceCurrencyCode` | ✅ |
| 18 | 수수료부담구분 | 3 | R | `feeBrdnClsCd` | ✅ |
| 19 | 수취인주소 | 180 | R | `rcptUseAddr` | ✅ |
| | **Σ** | **998** | | **998** + `\r\n` = 1000 | ✅ |

Field order is identical, and the handover's note that the 183-byte filler was **removed 2025-10-29** is consistent — the codec has no filler in the `D` record and still sums to 998.

**Trailer (`T`) — 8 fields**

| # | Handover §4.4 field | Len | Pad | `FixedLengthCodec.encodeTrailer` | Match |
|---|---|---|---|---|---|
| 1 | 식별코드 | 1 | R | `ID_TRAILER` "T" | ✅ |
| 2 | 요청 의뢰 건수 | 7 | L('0') | `requestCnt` | ✅ |
| 3 | 요청 의뢰 금액 | 23 | L('0') | `requestAmt` | ✅ |
| 4 | 정상 처리 건수 | 7 | L('0') | `succProcessingCnt` | ✅ |
| 5 | 정상 처리 금액 | 23 | L('0') | `succProcessingAmt` | ✅ |
| 6 | 미처리 건수 | 7 | L('0') | `nonProcessingCnt` | ✅ |
| 7 | 미처리 금액 | 23 | L('0') | `nonProcessingAmt` | ✅ |
| 8 | 예비(filler) | 907 | R | `filler` | ✅ |
| | **Σ** | **998** | | **998** + `\r\n` = 1000 | ✅ |

**So the layout now has two independent confirmations — legacy source and the six UAT sample files (`WECMS_BATCH DEV/samples/`, byte-verified per the codec's Javadoc) — but still zero vendor-document confirmation.** For a byte-alignment contract with a bank, two out of three is a reasonable position to hold, and the remaining risk is concentrated in exactly the place the codec already flags: the **charset** (§2.2) and any **future format revision** the vendor ships in the `.xlsx` without telling us.

### 8.3 One numeric cross-check worth raising with the vendor

The `D` record's **이체금액 (transfer amount) is 23 bytes**. The API's `data_amt` was **tightened 23 → 22** in the 2026-02-11 revision (p.3: `- data_amt 사이즈 변경(23 -> 22)` — "data_amt size changed (23 → 22)").

These are different surfaces — a fixed-length file field versus a JSON `number(22)` in the 301/302 envelope — so this is **not necessarily a conflict**. But it is the **one place** where a number in the PDF and a number in our file layout differ, and the revision that changed it is recent. See finding **F-3**; the question is in §10.

---

## 9. Q8 — Other contradictions with our implementation

Nine findings. Severity reflects operational risk, not effort.

### F-1 · HIGH · Read timeout is below the vendor's stated SLA

`EfsProperties.readTimeoutMs = 20_000` (and `read-timeout-ms: 20000` in all three of `application-{sit,uat,prod}.yml`) is **below the 30-second limit §3.2 states as normative**. The source cited in the Javadoc (§6 sample) is one the PDF explicitly marks as non-binding; the binding figure is not referenced anywhere in our code or config. Full analysis and recommendation in **§4.5**. Legacy used 60 s read, comfortably above.

### F-2 · MEDIUM · Two of our outbound RC values are outside the vendor's published code set

[참조 2] publishes exactly `0000` / `G001` / `G002` / `G997` / `G998` / `G999`. On callback responses we emit:

- **`9999`** (amount mismatch) — **not a gateway code at all**; a WeCMS-internal `WCConst` value.
- **`G004`** (`CallbackAck`, `DataAccessException`) — **not in the published set**.
- **`G998`** (sibling fail-stop) — published, but with a **conflicting meaning**: the appendix reads *"An error occurred during banking communication."*, whereas `FirmBankingResultCd.MSG_TARGET_STAT_ERR` reads *"Unable to process the status of the transfer target (TB_PAY_FILE_MST) transaction as failed."*

This is **OQ-PDF-4 biting on the outbound surface** — previously framed as an inbound *interpretation* problem, it is also an outbound *emission* problem. `G001`/`G002`/`G999` are consistent with the appendix and are fine. Adopting the §6.6 recommendation (envelope `RC = 0000` on all durably-recorded paths) removes `9999` and `G004` from the wire entirely and resolves this.

### F-3 · MEDIUM · `data_amt` 23 → 22 vs the file's 23-byte 이체금액

See **§8.3**. Different surfaces, probably fine, but worth one question to the vendor given the revision is recent and we cannot check the `.xlsx`. If the file field also moved to 22, `FixedLengthCodec`'s `transferAmount` width is stale and every `D` record is misaligned by one byte from field 6 onward — a high-impact failure from a low-probability cause, which is exactly the kind worth a cheap confirmation.

### F-4 · MEDIUM · The §5.4 FX block is printed **Mandatory** unconditionally

In §5.4's Request table, six of the eight FX fields carry **Mondatory**:

```
withd_curren_cd  String(3)   Mondatory  출금통화코드: USD 고정
dep_curren_cd    String(3)   Mondatory  입금통화코드 VND
apl_exc_rate_itg Number(22)  Mondatory  적용환율 정수
apl_exc_rate_dcm Number(3)   Mondatory  적용환율 소수
withd_amt_itg    Number(22)  Mondatory  출금금액 정수
withd_amt_dcm    Number(3)   Option     출금금액 소수
dep_amt_itg      Number(22)  Mondatory  입금금액 정수
dep_amt_dcm      Number(3)   Option     입금금액 소수
```

> **Translation:** withdrawal currency code (**fixed USD**) · deposit currency code (VND) · applied exchange rate, integer / decimal · withdrawal amount, integer / decimal · deposit amount, integer / decimal.

**No conditional qualifier is printed** — no "Woori only", no "when FX". `RealtimeTransferService` populates this block only on the Woori (`970457`) / non-VND branch and omits it for domestic VND, matching legacy.

Almost certainly the **table is mis-marked**: the whole block was appended in the 2026-02-11 revision, the `withd_curren_cd` memo says *USD 고정* (fixed USD) which cannot apply to a domestic VND transfer, and §5.6's 204 — the only source for these values — is stated as *우리은행 전용* (Woori-exclusive). Legacy has run this way in production for years.

**But if the gateway actually enforces Mandatory, every domestic VND 202 fails.** That surfaces on the first SIT transfer, so it is cheap to find — flagging it so it is *recognised* immediately rather than debugged from scratch. **No code change recommended**; match legacy, and confirm with Mr. Kang.

### F-5 · MEDIUM · `tar_bank_nm` applies precisely to the one FCY scenario we run

§5.4's memo (p.18):

```
tar_bank_nm  String(150) Option  VCB : 국내(베트남)타행외화송금(우리은행 사용가능)
                                 VCB 망 사용시 사용됨..
                                 VCB 망에 미 입력시 자동 매칭
```

> **Translation:** "VCB: domestic (Vietnam) other-bank **foreign-currency** remittance (**usable by Woori Bank**) · Used when the VCB network is used · **Auto-matched if not entered on the VCB network.**"

The diff-audit's assessment — optional, gateway auto-matches, legacy omits it, "optional future enhancement, not a defect" — is **correct**. The refinement: the memo ties this field specifically to **Woori + VCB-network foreign-currency remittance**, which is exactly the 204 → 202 FX path we implemented in June. So it is not a hypothetical future enhancement; it is the field that becomes relevant in **our one live FCY scenario**. Still not a defect (auto-match is documented), but "future enhancement" understates the adjacency. Worth listing in the SIT test plan for the Woori FCY leg.

### F-6 · LOW · [참조 1] 은행코드 is an **empty table** — the guide is not the source of our bank codes

Page 42 renders only the header cells:

```
[참조 1] 은행코드    은행코드
                     은행명
           순번
```

> **Translation:** "[Reference 1] Bank codes — 순번 (No.) · 은행코드 (bank code) · 은행명 (bank name)." **Three column headers, zero data rows.** (Verified not to be an image: all 26 embedded image XObjects were extracted; the only content-bearing one is §3.8's diagram — the rest are clip-art icons.)

So the bank-code table **cannot be cited from this document**. Codes do appear in prose — §5.6 pins `우리은행 : 970457` ("Woori Bank: 970457") and the §6 samples use `970424` — but VCB's `970436` appears nowhere. Our hardcoded `970457` / `970424` / `970436` are therefore only **partially** document-backed. Not a defect; a provenance gap. If a canonical bank-code list is ever needed, request the populated [참조 1] or `NAPAS_BANK_INFO` as the source of record.

### F-7 · LOW–MEDIUM · Operating window 00:15–23:50 vs our 24 h configuration

**§3.3 온라인 처리 시간** (p.9):

```
웹케시 베트남 시스템 시간 기준으로 처리한다.
온라인 처리 시간은 매일 00:15 ~ 23:50 이다.(은행에 따라 차이가 발생할 수 있음)
```

> **Translation:** "Processing is based on **Webcash Vietnam system time**. Online processing hours are **00:15 – 23:50 daily** (differences may occur depending on the bank)."

Per memory `project_batch_event_driven_tobe_recovery` the cut-off gate is currently **inert with a 24 h DB configuration**, pending a PO decision to drop it. That is a divergence from a **stated vendor constraint**: dispatch attempted in the 23:50–00:15 window is outside the gateway's declared service hours. Two points for whoever takes the PO decision:

1. The window is **vendor-normative**, not a WeCMS invention — dropping the gate entirely means knowingly calling outside stated hours for 25 minutes a day.
2. "웹케시 베트남 시스템 시간 기준" — **the gateway's** system time, not ours. Per memory `project_batch_schedule_tz_antdiv_fixes` the JEX DB runs `Asia/Seoul` while operations are ICT; a 25-minute window is small enough that a timezone error swallows it whole. If the gate is kept, pin which zone 00:15/23:50 are expressed in.

### F-8 · LOW · In-document section cross-references are stale — resolve by `.jct`, never by §-number

When §5.6 (204) was inserted, the subsequent sections were renumbered but the **cross-references were not**. Two places still cite v1.2.2 numbering:

- **§5.12's own header line** says *"5.9 … 접수 요청 건과 5.10 … 접수 결과 통지 수신 건"* — in v1.2.6 those are §5.10 and §5.11.
- **§3.8's diagram labels** read `(연동가이드 5.9 대량이체 접수 요청)`, `(연동가이드 5.10 …)`, `(연동가이드 5.11 대량이체 실행 요청)`, `(연동가이드 5.12 대량이체 실행 결과 수신 요청)` — all one step behind.

`efs-service-mapping-from-pdf.md` already documents the renumbering correctly in its §3 note. Recording it here as a standing rule: **when citing this guide in code comments or docs, anchor on the `.jct` code or the Korean service name, never the section number** — the numbers are unstable across revisions *and* internally inconsistent within v1.2.6.

### F-9 · LOW · `G997` is published but absent from `FirmBankingResultCd`

[참조 2] lists `G997` with the same wording as `G998` (*"An error occurred during banking communication."*). `FirmBankingResultCd` defines `0000`, `0001`, `9999`, `G001`–`G004`, `G998`, `G999`, `BT00`, `BT01`, `C999` — **no `G997`**. An inbound `G997` falls through `isSuccess()` → treated as a generic failure, which is behaviourally correct. Adding the constant would improve log/audit classification and complete coverage of the published set. Cosmetic.

### Things this pass confirms we got **right**

Worth stating, so the findings list is not read as a verdict on the whole integration:

- **Flat `ID=…&EV=…&VV=…` form body**, not the `_JSON_` variant — matches §6.1 exactly, and avoids the double-encoding trap (§5.3).
- **Single URL-encode / single URL-decode** — matches the §4 memo and both samples (§5.1–5.2).
- **Zero 16-byte IV, AES-256/CBC/PKCS5, HMAC over the JSON only, `.substring(14)`** — all confirmed verbatim.
- **`Content-Type` as the only header** — spec-conformant; dropping `Authorization`/`signature` was correct (§3.5).
- **UTF-8 throughout the API layer** (§2.1).
- **No blind resend after a 202 no-response** — satisfies the §5.4 주의, the single most safety-critical instruction in the guide.
- **00302 issued AFTER_COMMIT, gated on durable reception state** — matches §5.12's precondition and §3.8's ordering (§7.1).
- **`repairBase64Alphabet` ported** — now mechanistically justified by the §6.2 sample bug (§5.5).
- **`FixedLengthCodec` layout** — exact match against the legacy-derived spec on all three records (§8.2).

---

## 10. Questions for Mr. Kang / VAN Center

Ordered by value. The first three are the ones worth a meeting.

1. **(D-4, §6) Callback response contract.** For the 01301 and 01302 Return-URL callbacks: **(a)** which `RC` values may the partner return, and what does the gateway do with each? **(b)** Does a non-`0000` `RC` cause the gateway to resend the callback — if so, how many times and at what interval? **(c)** What does the gateway do with an unparseable or non-envelope response? **(d)** Does §3.7's "skip EV parsing when RC ≠ 0000" apply symmetrically to responses the gateway receives from us? *Blocks the G998/9999 decision in §6.6.*
2. **(§6.5) `result_cd` value set.** §5.11 marks `result_cd String(4)` Mandatory but never enumerates it. Please provide the full set. Specifically confirm **`BT00`** = reception success, and the meaning of **`BT01`** and **`C999`**, which our legacy batch treats as no-response/communication errors on the 302 leg. None of the three appear in the guide or in [참조 2]. *Blocks B5 and the bulk state machine's correctness.*
3. **(K3, §8) `글로벌 VAN_펌뱅킹_배치파일 레이아웃.xlsx`.** §1.2 delegates the SFTP file spec to this document; we do not have it. Please send the current version. Two specific questions it must answer: **(a)** the **character encoding** of the `H`/`D`/`T` text fields (송금인성명, 수신인성명, 적요, 수취인주소) — is non-ASCII accepted, and in which codepage? **(b)** the current width of the `D` record's **이체금액** field — our layout uses **23** bytes; the API's `data_amt` was tightened 23 → 22 on 2026-02-11 (F-3), and we need to know whether the file field moved with it.
4. **(F-4) §5.4 FX block conditionality.** `withd_curren_cd` … `dep_amt_itg` are printed **Mondatory** with no condition, but `withd_curren_cd` is documented as *USD 고정* and the source 204 is Woori-exclusive. Confirm these are **conditional on the Woori FX path** and may be omitted for domestic VND transfers (as legacy has done in production).
5. **(§7.2 / K9) Bulk-leg duplicate handling.** If a 00302 is sent twice for the same `file_nm`, is it deduplicated, rejected, or executed twice? Is there any inquiry service for the bulk leg equivalent to 203 for realtime — i.e. a way to ask "has this file already been executed?" *If neither exists, please confirm in writing that duplicate suppression is entirely the partner's responsibility.*
6. **(C-7 / §4) Timeout confirmation.** Confirm §3.2's **30 초** is the response-time limit we should size client timeouts against, and that the §6.1 sample's 3 s / 20 s are illustrative only. Is there a different limit for the bulk legs (301/302), which may involve larger files?
7. **(F-6) [참조 1] 은행코드 is empty** in the copy we hold (ver 1.2.6) — only the three column headers render, no rows. Please send a populated version, or confirm `NAPAS_BANK_INFO` is the source of record for bank codes.
8. **(F-2 / OQ-PDF-4) Code-set reconciliation.** [참조 2] publishes `0000`/`G001`/`G002`/`G997`/`G998`/`G999`. Our legacy `WCConst` additionally defines `0001`, `9999`, `G003`, `G004`, and assigns `G998` the meaning "transfer-target ledger status failure" rather than the appendix's "banking communication error". Please confirm which are gateway codes and which are WeCMS-internal, so we stop emitting the internal ones on the wire.
9. **(§6.2 sample defects, informational)** Three defects in the §6.2 C# reference implementation, for a future revision: `EncryptAesBase64` calls `HttpUtility.UrlDecode` on the encrypt path while its own header comment says URL *encoding* (p.38) — this corrupts `+` to space in the Base64 output and is the likely origin of the alphabet-repair logic every integrator carries; `VerifyMac` returns `false` when the HMACs are **equal** (p.41); and the UTF-8 encoding object is named `euckrEncoding` (p.36). Also, §3.4's prose is missing a word (`응답이 [없는] 경우`, p.9).

---

## 11. Effect on the open-item register

| Item | Before | After this pass |
|---|---|---|
| **C-7** (timeouts / SLA) | Values sourced from the §6 sample; no known SLA | 🟢 **CLOSED** — §3.2 = 30 s, normative. **Our 20 s read timeout is below it (F-1).** Fix + re-cite the Javadoc to §3.2. |
| **C-8** (HTTP headers) | Unknown whether the gateway requires/validates headers | 🟢 **CLOSED (negatively)** — `Content-Type` only; no `Authorization`/`signature`/`accessIp`/IP allow-list in 44 pages. Our client is conformant. Infra-level IP allow-listing stays an infra question (OQ-PDF-2). |
| **C-13** (charset) | "CP/UTF-8 byte 기준 패딩", no codepage named | 🟢 **CLOSED** *(updated 2026-07-31, after this pass)* — API/JSON layer **UTF-8** (§1.2). File layer **ASCII, negatively confirmed** from evidence outside this PDF: handover §8.4 pitfall ④ (`미적용 시 은행 측 거절` — non-application ⇒ bank rejects) + zero multibyte files ever reaching `PROC_STEP='3'` (2 attempts, both failed). The row's prior "ASCII-only-by-contract is unsupported by the spec" verdict was a statement about *this document's silence* and is superseded, not contradicted. See **D-L**. |
| **C-15** (EV/VV encoding) | Single vs double encoding unresolved; 2-pass + repair unexplained | 🟢 **CLOSED** *(upgraded from MOSTLY CLOSED 2026-08-03)* — single encode/decode, both directions, applied last. Double encoding only in the unused `_JSON_` variant. Repair **mechanistically explained** by the §6.2 `UrlDecode` bug — keep it. **2-pass resolved without B5**: legacy's ladder compensates for its repair-before-decode ordering; our repair-after-decode covers both input classes in one pass. Legacy's plaintext validation **ported**; its defective `else` branch and `VerifyMac`'s nesting deliberately not. §5.4 addendum. |
| **D-4** (callback response contract) | Unknown | 🔴 **STILL OPEN** — the PDF does not settle it. Non-`0000` branches are neither authorised nor forbidden. **Recommendation in §6.6: revert the envelope `RC` to legacy's uniform `0000` on durably-recorded paths, keep `G998`/`9999` in the persisted columns.** Question 1 in §10. |
| **00302 timing** | Unknown whether a settling period is required | 🟢 **CLOSED** — precondition on state, not elapsed time. Our AFTER_COMMIT fan-out is conformant. |
| **00302 idempotency** | Unknown | 🔴 **STILL OPEN** — total silence. No dedup statement, no idempotency key, and **no bulk-leg equivalent of 203**. Local state-based suppression is the only guard and must stay. |
| **K3** (fixed-length layout) | Blocked on the file spec; UAT samples answered the offsets | 🟡 **NARROWED** *(updated 2026-07-31)* — still open for the vendor-document check (the `.xlsx` is absent; layout has 2 confirmations, 0 vendor-document ones), but **charset is no longer the live risk** — see C-13 above. The residual value of the `.xlsx` is the **`data_amt` 23-vs-22 width** question. |
| **B5** (real captured EFS sample) | Open | 🔴 **Still the critical path, on a narrower list** *(updated 2026-08-03)* — remaining closer for **D-4** and the `result_cd` set (`BT00`/`BT01`/`C999`). **The 2-pass question was removed** from this list: it was settled from the legacy source, not the wire (§5.4 addendum). Do not re-attach it. |
| **OQ-PDF-2** (partner instance / IP whitelist) | Open | 🔴 Unchanged — infra question. The guide has no IP allow-list concept at all, which is not evidence either way. |
| **OQ-PDF-4** (`Gxxx` vs `WCConst`) | Open, framed as inbound interpretation | 🔴 Open, **now also outbound** — we **emit** `9999` and `G004`, neither of which is a gateway code (F-2). |
| **OQ-PDF-5 / OQ-PDF-6** | Open | 🔴 Unchanged; both **reinforced** — see §8.1 and §7.2. |

---

## 12. Reproducing this pass

```bash
# The filename is Korean; Git Bash cannot pass it to pdftotext directly.
cp "WECMS_BATCH DEV/handover/"*ver1.2.6.pdf /tmp/efs126.pdf
pdftotext -enc UTF-8 -layout /tmp/efs126.pdf /tmp/efs126.txt   # 1541 lines, Korean extracts cleanly

# §3.8's sequence diagram is a raster image with no text layer, and poppler's
# renderer is unavailable in this environment. The 26 image XObjects are
# FlateDecode DeviceRGB/DeviceGray bitmaps and can be reconstructed as PNGs
# directly from the PDF byte stream with zlib — object 155 (1070x1270) is the
# only content-bearing one; the other 25 are clip-art icons.

# Key section anchors in the extracted text:
#   §1.2 기본정보 (charset/URLs/SFTP)  → lines 204-229
#   §3.2 전문 응답시간 (the 30s SLA)     → lines 313-322
#   §3.3 온라인 처리 시간                → lines 324-327
#   §3.4 Time Out 처리                  → lines 329-334
#   §3.7 OUTPUT EV 데이터 처리           → lines 350-353
#   §3.8 대량이체 데이터 흐름도 (image)   → line 358 (heading only)
#   §4  전문구성 Request/Response       → lines 363-446
#   §5.4 주의 (double-transfer warning)  → lines 705-708
#   §5.11 01301 Response Data           → lines 1058-1079
#   §5.12 00302 precondition            → lines 1084-1087
#   §5.13 01302 Response Data           → lines 1146-1160
#   §6.1 Java sample (3s/20s timeouts)  → lines 1171-1273
#   §6.2 C# sample (EncryptAesBase64)   → lines 1275-1517
#   [참조 1] 은행코드 (EMPTY)            → lines 1520-1523
#   [참조 2] 응답코드                    → lines 1527-1539

# Negative-result verification (all confirmed 0 occurrences):
#   Authorization · signature · accessIp · coocon · whitelist · 화이트리스트
#   retry · Retry · 재시도 · 재전송 · 중복 · 멱등
#   Header · Trailer · 고정길이 · ASCII · BT00 · BT01 · C999 · 9999
```

> **Note:** multi-pattern `grep -e` with Korean literals crashes in this Git Bash build (there is a `grep.exe.stackdump` in the handover folder from a prior attempt). Use the Grep tool or single-pattern alternation instead.

---

*End of closure pass. Read-only with respect to source code, configuration, and database. Six recommendations are stated in §4.5, §6.6, §7.2, and §9 (F-1 – F-9); none were applied. Nine questions for the vendor are consolidated in §10.*
