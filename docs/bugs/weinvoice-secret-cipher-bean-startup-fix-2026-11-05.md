# WeInvoice Secret Cipher Bean Breaks Application Startup

**Date:** 2026-11-05
**Type:** BUG FIX
**Current Work Classification:** BUG
**Status:** READY FOR MANUAL COMMIT
**Investigation Status:** COMPLETE · **Approval Status:** NOT REQUIRED (user asked to get the local run working; defect is in the WeInvoice change itself) · **Implementation Status:** COMPLETE · **Test Status:** PASS
**Repository:** wecms-api (checked out as `we-scraping-api`)
**Issue:** WeInvoice P1 — follow-up to [weinvoice-api-implementation-2026-11-02.md](../features/addon-service/weinvoice/weinvoice-api-implementation-2026-11-02.md)

## Problem

The application does not start. Commit `8823f7f` (WeInvoice P1), already on `origin/sit`, is affected:
every environment that deploys it fails at context refresh, whatever `WECMS_WEINVOICE_ENABLED` says.

## Current Behavior

Found while bringing up a local run (`gradlew bootRun`, profile `local`, `.env.local` complete enough to
reach bean creation):

```
BeanInstantiationException: Failed to instantiate
  [com.kosign.wecms.domain.weinvoice.common.WeInvoiceSecretCipher]: No default constructor found
Caused by: NoSuchMethodException: ...WeInvoiceSecretCipher.<init>()
```

## Expected Behavior

The context starts; with WeInvoice disabled the cipher bean exists but holds no key.

## Root Cause

`WeInvoiceSecretCipher` is a `@Component` with two constructors — `public (WeInvoiceProperties)` for
Spring and a package-private `(byte[] rawKey)` for unit tests — and neither is marked `@Autowired`.
With more than one constructor and no marker, Spring falls back to a no-arg constructor, which does not
exist. Every other bean in `domain/weinvoice` has a single constructor (checked: only this class has
two).

The unit suite missed it: `WeInvoiceSecretCipherTest` calls the constructors directly, and no WeInvoice
test builds a Spring context.

## Implementation

Mark the `WeInvoiceProperties` constructor `@Autowired`. No behaviour change otherwise.

## Database Impact

NONE.

## Files Changed

| File | Change |
|---|---|
| `src/main/java/com/kosign/wecms/domain/weinvoice/common/WeInvoiceSecretCipher.java` | `@Autowired` on the `WeInvoiceProperties` constructor |
| `src/test/java/com/kosign/wecms/domain/weinvoice/WeInvoiceBeanConstructionTest.java` | New regression test (2 cases) |

## API Impact · UI Impact · Integration Impact

None — the bean behaves exactly as before once constructed.

## Regression Test

Test file:          `src/test/java/com/kosign/wecms/domain/weinvoice/WeInvoiceBeanConstructionTest.java`
Test case:          `springInstantiatesSecretCipher`, `everyWeInvoiceBeanHasAConstructorSpringCanChoose`
Behavior protected: Spring can construct every WeInvoice bean; a bean with several constructors must mark one `@Autowired`.

Both cases fail without the fix and pass with it.

## Verification

### Automated Tests

- Test: `./gradlew test --tests 'com.kosign.wecms.domain.weinvoice.*'`
- Result: PASS — 87 tests, 0 failures (fails 2/2 on the new test with the fix removed).

### Manual Verification

- Environment: LOCAL (`bootRun`, shared dev DB, WeInvoice disabled).
- Steps: start the application before and after the fix.
- Result: PASS — before `No default constructor found`; after `Started WecmsApiMainApplication in 32.982 seconds`.

## Test Evidence

[weinvoice-secret-cipher-bean-startup-test-result-2026-11-05.md](../testing/results/weinvoice-secret-cipher-bean-startup-test-result-2026-11-05.md)

Final test result: PASS · Regression coverage: PASS

## Result

```
Code: COMPLETE · Tests: PASS (87/87) · Local startup: PASS
Commit / push / SIT deploy: PENDING (developer) — origin/sit (8823f7f) does not start until this lands
Overall: READY FOR MANUAL COMMIT
```

## Known Limitations / Follow-up

- The WeInvoice suite still has no full `@SpringBootTest`; the new scan test covers constructor
  resolution only, not wiring of every dependency.
