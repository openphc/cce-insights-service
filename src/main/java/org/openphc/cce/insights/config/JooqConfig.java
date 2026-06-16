package org.openphc.cce.insights.config;

import org.jooq.conf.RenderNameCase;
import org.jooq.conf.RenderQuotedNames;
import org.springframework.boot.autoconfigure.jooq.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JooqConfig {

    @Bean
    public DefaultConfigurationCustomizer jooqConfigCustomizer() {
        return c -> c.settings()
                // ClickHouse does not use a schema prefix in queries
                .withRenderSchema(false)
                // No quotes around identifiers — ClickHouse is case-insensitive for
                // unquoted names and the FINAL clause syntax requires unquoted table refs
                .withRenderQuotedNames(RenderQuotedNames.NEVER)
                .withRenderNameCase(RenderNameCase.LOWER);
    }
}
