package org.openphc.cce.insights.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Timer complianceSummaryTimer(MeterRegistry registry) {
        return Timer.builder("insights.compliance.summary")
                .description("Time to compute compliance summary")
                .register(registry);
    }

    @Bean
    public Timer deviationTrendsTimer(MeterRegistry registry) {
        return Timer.builder("insights.deviations.trends")
                .description("Time to compute deviation trends")
                .register(registry);
    }

    @Bean
    public Timer eventVolumeSummaryTimer(MeterRegistry registry) {
        return Timer.builder("insights.events.summary")
                .description("Time to compute event volume summary")
                .register(registry);
    }
}
