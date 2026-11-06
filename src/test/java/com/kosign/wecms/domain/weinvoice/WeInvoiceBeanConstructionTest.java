package com.kosign.wecms.domain.weinvoice;

import com.kosign.wecms.domain.weinvoice.common.WeInvoiceSecretCipher;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The application context must be able to build every WeInvoice bean. Unit tests call constructors
 * directly, so a bean Spring cannot instantiate passes them and only fails at startup — which is how
 * {@link WeInvoiceSecretCipher} (two constructors, none {@code @Autowired}) reached {@code sit}
 * (docs/bugs/weinvoice-secret-cipher-bean-startup-fix-2026-11-05.md).
 */
class WeInvoiceBeanConstructionTest {

    @Test
    void springInstantiatesSecretCipher() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(WeInvoiceProperties.class, WeInvoiceTestProps::disabled);
            ctx.register(WeInvoiceSecretCipher.class);
            ctx.refresh();
            assertThat(ctx.getBean(WeInvoiceSecretCipher.class)).isNotNull();
        }
    }

    /** Spring needs a single constructor, or exactly one marked {@code @Autowired}, or a no-arg one. */
    @Test
    void everyWeInvoiceBeanHasAConstructorSpringCanChoose() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class)); // also matches @Service, @Repository, controllers
        List<String> unresolvable = new ArrayList<>();
        int scanned = 0;
        for (BeanDefinition bd : scanner.findCandidateComponents("com.kosign.wecms.domain.weinvoice")) {
            Class<?> type = Class.forName(bd.getBeanClassName());
            scanned++;
            Constructor<?>[] ctors = type.getDeclaredConstructors();
            long autowired = Arrays.stream(ctors).filter(c -> c.isAnnotationPresent(Autowired.class)).count();
            boolean noArg = Arrays.stream(ctors).anyMatch(c -> c.getParameterCount() == 0);
            if (ctors.length > 1 && autowired != 1 && !noArg) {
                unresolvable.add(type.getSimpleName());
            }
        }
        assertThat(scanned).as("WeInvoice beans found by the scan").isGreaterThan(30);
        assertThat(unresolvable).as("beans with several constructors and no @Autowired one").isEmpty();
    }
}
