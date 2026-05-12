package com.kosign.wecms.common.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class MessageResolver {

    private final MessageSource messageSource;

    public String resolve(String code) {
        return resolve(code, new Object[0]);
    }

    public String resolve(String code, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(code, args, code, locale);
    }

    /**
     * Resolve a message in an explicit locale, ignoring the
     * request-scoped {@link LocaleContextHolder}. Used by callers that
     * need to fan a single key out across multiple locales — e.g. the
     * shared {@code AuditLogService} which writes one row per
     * supported locale (EN / KO / VI / KM) per business event so the
     * audit history reads correctly in every user's language without
     * server-side re-translation.
     *
     * <p>Falls back to the message code itself when the bundle has no
     * entry for the key (matches the request-scoped overload's
     * fallback). Callers that want a different fallback should pass
     * one explicitly.
     *
     * @param code   message-bundle key
     * @param locale target locale; never {@code null}
     * @param args   {@link java.text.MessageFormat} args
     */
    public String resolve(String code, Locale locale, Object... args) {
        return messageSource.getMessage(code, args, code, locale);
    }
}
