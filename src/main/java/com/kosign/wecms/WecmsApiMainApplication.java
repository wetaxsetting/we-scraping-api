package com.kosign.wecms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Application entry point.
 *
 * <p>Extends {@link SpringBootServletInitializer} so the WAR can be
 * deployed into an external servlet container (Tomcat / JBoss / etc.).
 * The {@code main(...)} method still works for local development — Spring
 * Boot's auto-configured Tomcat is pulled in via the {@code providedRuntime}
 * scope at compile time, but excluded from the packaged WAR so the host
 * server's container is the only servlet provider at runtime.
 */
@SpringBootApplication
public class WecmsApiMainApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(WecmsApiMainApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(WecmsApiMainApplication.class, args);
    }
}
