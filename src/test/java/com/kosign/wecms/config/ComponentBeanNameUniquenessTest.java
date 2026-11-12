package com.kosign.wecms.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression for the 2026-11-12 startup failure (docs/bugs/weinvoice-notification-service-bean-name-conflict-fix-2026-11-12.md):
 * two {@code @Service} classes with the same simple name in different packages get the same default bean name and the
 * application refuses to start. Unit tests that build classes directly never see it, so this test runs the same
 * component scan as {@code @SpringBootApplication} and fails on any duplicate bean name.
 */
class ComponentBeanNameUniquenessTest {

    @Test
    void everyScannedComponentHasAUniqueBeanName() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(true);
        AnnotationBeanNameGenerator names = AnnotationBeanNameGenerator.INSTANCE;
        SimpleBeanDefinitionRegistry registry = new SimpleBeanDefinitionRegistry();
        Map<String, Set<String>> byName = new TreeMap<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.kosign.wecms")) {
            if (!(candidate instanceof AnnotatedBeanDefinition)) continue;
            String name = names.generateBeanName(candidate, registry);
            byName.computeIfAbsent(name, k -> new TreeSet<>()).add(candidate.getBeanClassName());
        }
        Map<String, Set<String>> duplicates = new TreeMap<>();
        byName.forEach((name, classes) -> {
            if (classes.size() > 1) duplicates.put(name, classes);
        });
        assertThat(byName).isNotEmpty();
        assertThat(duplicates).as("components sharing a bean name").isEmpty();
    }
}
