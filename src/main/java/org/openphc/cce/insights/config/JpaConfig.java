package org.openphc.cce.insights.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
public class JpaConfig {
    // Read-only transaction defaults enforced via @Transactional(readOnly = true) on service methods.
    // HikariCP read-only is set in application.yml.
}
