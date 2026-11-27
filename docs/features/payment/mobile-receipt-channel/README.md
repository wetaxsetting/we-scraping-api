# Mobile Receipt Channel

**Status:** implemented (list · detail · create-with-channel · update) · photo staging deferred
**Domain:** Payment — the receipt endpoints live on the Card Transactions resource
**Spring package:** `com.kosign.wecms.domain.payment.cardtransactions`

The receipt-capture flow of the rebuilt WeCMS mobile app, served over the refactor REST API. The
mobile client speaks `/api/v1/**` directly — the backend does **not** re-implement the legacy
single-gateway envelope (decision recorded 2026-09-03).

## Documents

| Doc | What it is |
|---|---|
| [mobile-receipt-channel-2026-09-03.md](mobile-receipt-channel-2026-09-03.md) | The dated **feature record** — requirement, verified current behaviour, scope, implementation, verification, known limitations. Start here. |
| [client-integration-guide.md](client-integration-guide.md) | **How the client calls it** — auth, envelope gotchas, per-endpoint examples, the capture flow, error handling, what is not available yet. |

## Related

- API contract: [../../../references/api-spec/payment/mobile-receipt-api.md](../../../references/api-spec/payment/mobile-receipt-api.md)
- Legacy field-level spec (all 76 mobile services): [../../../references/migration/mobile-app/README.md](../../../references/migration/mobile-app/README.md) · receipt group: [receipt.md](../../../references/migration/mobile-app/receipt.md)
- Test evidence: [../../../testing/results/mobile-receipt-channel-test-result-2026-09-03.md](../../../testing/results/mobile-receipt-channel-test-result-2026-09-03.md)
- Sibling feature that owns the same tables: [../card-transactions.md](../card-transactions.md)

## Open decision

Photo staging — uploading a photo **before** the receipt exists — is not implemented. It needs
`mrec_pic_store` / `mrec_pic_store_file` (legacy-but-inactive tables) to be brought into scope, or an
alternative design. Three options with trade-offs are in
[mobile-receipt-api.md §5](../../../references/api-spec/payment/mobile-receipt-api.md); the
consequence for the client is in
[client-integration-guide.md §6](client-integration-guide.md).
