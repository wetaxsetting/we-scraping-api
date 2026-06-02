# wecms-api-docs — Folder Structure & Placement Guide

Authoritative map for where backend docs go under `wecms-api/docs/`. Read alongside `api-doc-rules.md`.

## Directory map

```
wecms-api/docs/
├── README.md                      # docs root index
├── WORKFLOW.md
├── error-codes.md
├── glossary.md
├── permissions.md
│
├── features/                      # FEATURE-FIRST specs — primary home for feature docs
│   ├── README.md
│   ├── auth/
│   ├── common/
│   ├── banking/                   # e.g. bank-credentials.md, own-account-transfer/, reports/
│   ├── payment/
│   ├── addon-service/
│   └── settings/
│
├── api-spec/                      # REST endpoint contracts
├── architecture/                  # backend system architecture
├── business-rules/                # entities, repositories, services, security, settings
│
├── references/
│   ├── README.md
│   ├── api/                       # API reference notes
│   ├── api-spec/                  # endpoint contract references
│   ├── audits/                    # parity audits (refactor vs JEX)
│   ├── database/                  # SCHEMA references
│   │   ├── README.md
│   │   ├── table-mapping/
│   │   ├── indexing/
│   │   ├── query-analysis/
│   │   ├── flyway/
│   │   ├── database-coverage-matrix.md
│   │   ├── {domain}/              # banking/, payment/, settings/, addon/
│   │   └── erd/                   # ERDs
│   │       ├── README.md
│   │       ├── {domain}-erd.md    # banking-erd.md, payment-erd.md, …
│   │       ├── full-project-erd.md
│   │       └── diagrams/
│   ├── external/
│   ├── figma/
│   ├── jex/                       # JEX reference notes (read-only spec capture)
│   └── migration/                 # JEX → Spring service-by-service mapping + playbook
│
├── planning/
│   ├── README.md
│   ├── open-questions/            # OQ tracking — DO NOT auto-close
│   │   ├── documentation-follow-up.md   # OQ-DOC items
│   │   ├── database-follow-up.md         # OQ-DB items
│   │   └── ...
│   ├── roadmap/  phase-1/  phase-2/  settings/  addon/
│
├── testing/
└── archive/                       # retired docs (do not author new content here)
```

## Placement decision guide

| You are documenting… | Put it under… |
|---|---|
| A feature's behavior, services, rules | `features/{domain}/` |
| A REST endpoint contract (request/response/DTO/errors) | `api-spec/` (link from the feature doc) |
| Entity / repository / service design | `business-rules/` |
| A table, schema, or table-mapping | `references/database/` (+ `{domain}/` subfolder) |
| An ERD | `references/database/erd/` |
| A JEX → Spring mapping | `references/migration/` |
| A parity gap vs JEX | `references/audits/` |
| An unresolved question | `planning/open-questions/` (OQ-DOC or OQ-DB) |
| Sprint / phase / roadmap planning | `planning/` |
| A test plan | `testing/` |

## Feature domains

The six top-level domains are fixed: **auth, common, banking, payment, addon-service, settings**. Every feature doc maps to exactly one. If a feature seems cross-domain, document its primary home under one domain and cross-link from the other.

## Placement rules

- A new feature doc → `features/{domain}/{feature-slug}.md` (or a subfolder for multi-doc features). Keep the slug identical to its JEX and frontend counterparts.
- Never duplicate the same content in two places — pick the canonical home and cross-link.
- `archive/` is read-only history. Do not author new docs there; move-to-archive is a confirmed, explicit action only.
- Always update the nearest `README.md` index when adding a file.
