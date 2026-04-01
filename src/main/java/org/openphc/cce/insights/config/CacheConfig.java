package org.openphc.cce.insights.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String LOOKUPS = "lookups";
    public static final String ANALYTICS = "analytics";
    public static final String METRICS = "metrics";

    @Value("${cce.cache.ttl.lookups:60}")
    private long lookupsTtl;

    @Value("${cce.cache.ttl.analytics:30}")
    private long analyticsTtl;

    @Value("${cce.cache.ttl.metrics:15}")
    private long metricsTtl;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager() {
            @Override
            protected com.github.benmanes.caffeine.cache.Cache<Object, Object> createNativeCaffeineCache(String name) {
                Caffeine<Object, Object> builder = Caffeine.newBuilder()
                        .recordStats();
                switch (name) {
                    case LOOKUPS -> builder.expireAfterWrite(lookupsTtl, TimeUnit.MINUTES).maximumSize(50);
                    case ANALYTICS -> builder.expireAfterWrite(analyticsTtl, TimeUnit.MINUTES).maximumSize(200);
                    case METRICS -> builder.expireAfterWrite(metricsTtl, TimeUnit.MINUTES).maximumSize(500);
                    default -> builder.expireAfterWrite(analyticsTtl, TimeUnit.MINUTES).maximumSize(100);
                }
                return builder.build();
            }
        };
        manager.setCacheNames(java.util.List.of(LOOKUPS, ANALYTICS, METRICS));
        return manager;
    }
}
