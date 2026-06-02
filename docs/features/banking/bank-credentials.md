# Banking > Bank Credentials & Scraping — Refactor Feature Spec (Backend)

**Domain:** `banking`
**Refactor slug:** `bank-credentials`
**Backend packages:** `com.kosign.wecms.domain.banking.bankcredentials` (CRUD) + `com.kosign.wecms.domain.banking.nxisas` (scraping flow)
**Status:** **Working** — CRUD over `BANK_CERT_INFO` + multi-step NXISAS/Coocon scraping session.
**Refactor routes:** `/api/v1/banking/credentials` (CRUD), `/api/v1/banking/scraping/*` (scrape flow)
**Frontend route:** `/banking/credentials`

> Created 2026-06-01 (documentation audit round 2). Documents an **already-implemented** feature that
> had no feature doc. No new scope, no source change.
>
> **Relationship to Settings → Login Credentials is open — see OQ-DOC-7.** This feature shares the
> `BANK_CERT_INFO` table and the NXISAS scraping flow with the Settings → Login Credentials panel
> ([../settings/login-credentials.md](../settings/login-credentials.md)). The canonical ownership / boundary
> between the two surfaces needs PO confirmation; this doc describes the `banking/credentials` surface
> as implemented and does not assert it supersedes Login Credentials.

## Overview

Manages a tenant's saved bank login credentials (`BANK_CERT_INFO`) and drives the live bank-scraping
session used to pull the account/card list from the bank via the Coocon/NXISAS smart gateway. The
plaintext password is **never** returned on the wire — list responses surface only username + inquiry
status; the password is read server-side only by the scraping flow.

## Endpoints

### Credential CRUD — `/api/v1/banking/credentials` (`BankCredentialController`)

| Method | Path | Returns |
|---|---|---|
| GET | `/api/v1/banking/credentials` | `BankCredentialListResponse` (username + status; no ciphertext) |
| POST | `/api/v1/banking/credentials` | `BankCredentialRow` (create) |
| PUT | `/api/v1/banking/credentials/{seqNo}` | `BankCredentialRow` (update) |
| DELETE | `/api/v1/banking/credentials/{seqNo}` | `Void` |

- Tenant scope rides on the JWT via `TenantContext` (route carries no `bizKey`).
- Password ciphertext handled by `BankCertCryptoService`; read for scraping only via `BankCredentialService.loadForScraping`.

### Scraping session — `/api/v1/banking/scraping` (`BankScrapingController`)

| Method | Path | Purpose |
|---|---|---|
| POST | `/account-list` | Start a scrape from a saved `BANK_CERT_INFO` row |
| POST | `/verify` | Verify inline credentials **before** saving (used by the Login Credentials "Login" button; persists to `BANK_CERT_INFO` only after the user picks accounts) |
| POST | `/{sessionId}/otp` | Submit an OTP challenge |
| POST | `/{sessionId}/qna` | Submit a security-question (QNA) answer |
| POST | `/{sessionId}/captcha` | Submit a typed CAPTCHA value |

- The gateway LOGIN response may complete (`ACCT_LIST` → `ScrapedAccount` / `ScrapedCard`) or return a
  challenge (`OTP_REQUIRED` / QNA / CAPTCHA). The session is a server-issued `UUID` bound to the issuing
  `(bizKey, userId)` pair so it cannot be replayed across principals.
- Mirrors the legacy `enter_login_popup → _v2nxisas.callNxisas` flow (`comm_0218_nxisas.ts:508`).

## Implementation

| Layer | File |
|---|---|
| Credential controller | `domain/banking/bankcredentials/controller/BankCredentialController.java` |
| Credential service / crypto | `domain/banking/bankcredentials/service/BankCredentialService.java`, `BankCertCryptoService.java` |
| Entity | `domain/banking/bankcredentials/entity/BankCertInfo.java` (+ `BankCertInfoId.java`) — table `BANK_CERT_INFO` |
| Scraping controller / service | `domain/banking/nxisas/controller/BankScrapingController.java`, `nxisas/BankScrapingService.java` |
| Scrape session | `domain/banking/nxisas/session/NxisasScrapeSession.java`, `ScrapeChallengeKind.java` |
| Scrape DTOs | `domain/banking/nxisas/dto/` (`StartScrapeRequest`, `VerifyCredentialsRequest`, `SubmitChallengeRequest`, `SubmitQnaRequest`, `ScrapeSessionResponse`, `ScrapedAccount`, `ScrapedCard`) |

## Gateway

Bank-scraping runs through the Coocon smart gateway (per memory `reference_nxisas_gateway_urls` /
`reference_nxisas_local_engine_origin`): DEV/SIT `dev-sgw…:8443`, UAT/PROD `sgw.webcashvietnam.com`,
configured via `wecms.coocon.smart-api-*`. Local-origin licensing caveats apply (ErrorCode `80000015`
= unlicensed origin).

## Related

- Frontend spec: [../../../../wecms-web/docs/features/banking/bank-credentials.md](../../../../wecms-web/docs/features/banking/bank-credentials.md)
- Settings → Login Credentials (shared `BANK_CERT_INFO` + scraping; boundary OQ-DOC-7): [../settings/login-credentials.md](../settings/login-credentials.md)
- Endpoint index: [../../references/api-spec/api-mapping.md](../../references/api-spec/api-mapping.md)
- Open question: [../../planning/open-questions/documentation-follow-up.md](../../planning/open-questions/documentation-follow-up.md) (OQ-DOC-7)
