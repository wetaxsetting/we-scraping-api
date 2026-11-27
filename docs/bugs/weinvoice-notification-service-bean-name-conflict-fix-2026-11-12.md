# WeInvoice: Notification Service Bean Name Conflicts at Startup

**Date:** 2026-11-12
**Type:** BUG FIX
**Current Work Classification:** BUG
**Status:** READY FOR MANUAL COMMIT
**Implementation Status:** COMPLETE · **Test Status:** PASS (unit)
**Approval Status:** NOT REQUIRED (developer request 2026-11-12 "giúp check vs" on the startup log, implementation of the just-shipped feature)
**Repository:** wecms-api (`we-scraping-api`)
**Related:** [weinvoice-notifications-2026-11-12](../features/addon-service/weinvoice/weinvoice-notifications-2026-11-12.md) (commit `eeea5d6`)

## Problem

`bootRun` fails: `ConflictingBeanDefinitionException: Annotation-specified bean name 'notificationService' for bean class
[com.kosign.wecms.domain.weinvoice.service.NotificationService] conflicts with existing, non-compatible bean definition of
same name and class [com.kosign.wecms.domain.payment.notification.service.NotificationService]`.

## Root Cause

Spring names a `@Service` after its simple class name. `eeea5d6` added `weinvoice.service.NotificationService`; the payment
module already has `payment.notification.service.NotificationService` → both are `notificationService`. Unit tests construct
the class directly, so no test started the Spring context (same blind spot as the 2026-11-05 `WeInvoiceSecretCipher` fix).
Only this name collides (checked every class added by the mailbox and notification work).

## Implementation

- Renamed (with `git mv`) `weinvoice.service.NotificationService` → `WeInvoiceNotificationService` and, to keep the pair
  aligned and away from the same trap, `weinvoice.repository.NotificationRepository` → `WeInvoiceNotificationRepository`
  (tests renamed alike); references in the controller, tests and the feature / test-result docs updated. The payment
  module is untouched.
- New regression test `config/ComponentBeanNameUniquenessTest`: runs the same component scan as `@SpringBootApplication`
  over `com.kosign.wecms` and fails on any duplicate bean name.

## Files Changed

`service/WeInvoiceNotificationService.java`, `repository/WeInvoiceNotificationRepository.java` (renamed),
`controller/WeInvoiceNotificationController.java`, `WeInvoiceNotificationServiceTest`, `WeInvoiceNotificationRepositoryTest`,
`WeInvoiceNotificationControllerTest`, `config/ComponentBeanNameUniquenessTest.java` (new), the notifications feature record
and API test-result record (class names), this record.

## Database Impact

NONE.

## Verification

### Regression Test

Test file: `src/test/java/com/kosign/wecms/config/ComponentBeanNameUniquenessTest.java` · Test case:
`everyScannedComponentHasAUniqueBeanName` · Behavior protected: no two scanned components share a bean name.
Reproduced: with a `weinvoice.service.NotificationService` put back temporarily the test FAILS with
`{"notificationService"=[…payment.notification.service.NotificationService, …weinvoice.service.NotificationService]}`;
after the rename it PASSES.

### Automated Tests

`./gradlew test --offline --tests "com.kosign.wecms.domain.weinvoice.*" --tests "…EndpointAuthorizationCoverageTest"
--tests "…ComponentBeanNameUniquenessTest"` — 191 tests, 0 failures.

### Manual Verification

`bootRun` with the developer's environment: NOT RUN here (the developer restarts the backend).
