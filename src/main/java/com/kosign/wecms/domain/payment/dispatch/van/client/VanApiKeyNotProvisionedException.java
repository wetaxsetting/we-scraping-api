package com.kosign.wecms.domain.payment.dispatch.van.client;

/**
 * No {@code TB_VAN_API_INFO.API_KEY} row exists for the requesting VAN org code.
 *
 * <h2>Why this is its own type</h2>
 * On a callback this must acknowledge with {@code G003}, not the generic {@code G999}, and it has to
 * be distinguishable from the other {@link IllegalStateException}s the callback services throw
 * (EFS payload serialise / parse failures) which are <i>not</i> key-provisioning faults.
 *
 * <h2>Legacy behaviour — G003 on BOTH legs</h2>
 * Legacy never checks the row count on the key lookup. It tests only {@code DomainUtil.isError(...)},
 * which is a <b>DB-fault</b> test, not a zero-rows test — proven by
 * {@code FILE_DEPOSIT_TRANSFER_API1_act.jsp} itself, which twenty lines later runs a
 * <i>separate</i> {@code getResultCount(...) == 0} check on the file lookup. If {@code isError}
 * covered zero rows that second check would be dead code.
 *
 * <p>So an unprovisioned key yields {@code CRYPT_KEY = null} and falls into the MAC step:
 * <ul>
 *   <li><b>01301</b> — {@code API1_act.jsp:99-110}: {@code VerifyMac(null, …)} returns false (or
 *       throws) inside the {@code try}; both routes end at
 *       {@code throw new JexBIZException(WCConst.CD_ENC_VRFY_ERR)} → <b>{@code G003}</b>.</li>
 *   <li><b>01302</b> — {@code API2_act.jsp:105-119}: {@code CommUtil.getApiKey} returns {@code null}
 *       ({@code CommUtil.java:66-70} throws only on {@code isError}), then the same {@code VerifyMac}
 *       failure is caught by the enclosing {@code catch (Exception e)} → <b>{@code G003}</b>.</li>
 * </ul>
 *
 * <p><b>Corrected 2026-08-03.</b> An earlier reading had 01301 answering {@code G004} on the theory
 * that a missing key is "a DB error there". It is not — {@code G004} requires
 * {@code DomainUtil.isError} to be true, i.e. an actual DB fault. Both legs are {@code G003}.
 *
 * <p>Extends {@link IllegalStateException} so existing handling is unchanged.
 *
 * <p><b>TODO(B2):</b> the table ships empty (V248 = schema only). This exception is the designed
 * outcome until the per-VAN key is provisioned out-of-band.
 */
public class VanApiKeyNotProvisionedException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public VanApiKeyNotProvisionedException(String message) {
        super(message);
    }
}
