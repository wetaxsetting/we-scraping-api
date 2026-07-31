package com.kosign.wecms.domain.payment.common;

import com.kosign.wecms.common.i18n.MessageResolver;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Test factory for a <b>real</b> {@link PaymentWireWidthGuard} wired over the real
 * {@code messages.properties} bundle.
 *
 * <p>Deliberately not a Mockito mock. A mocked guard returns {@code null} from
 * {@link PaymentWireWidthGuard#check}, which both NPEs at the call site and — worse — makes every
 * parser test pass whether or not the guard is wired in at all. Using the real collaborator means the
 * existing parser tests keep exercising the real width logic, and the message keys are resolved
 * against the real bundle so a missing / renamed key fails a test instead of shipping.
 */
public final class WireWidthGuards {

    private WireWidthGuards() {
    }

    /** A guard backed by the production {@code messages} bundle (EN, code-as-fallback). */
    public static PaymentWireWidthGuard real() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        return new PaymentWireWidthGuard(new MessageResolver(source));
    }
}
