package com.example.triplesweb.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@ConditionalOnProperty(name = "objekt.ingest.enabled", havingValue = "true", matchIfMissing = true)
public class ObjektIngestSchedulingConfig {
    @Bean("taskScheduler")
    ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("scheduled-");
        return scheduler;
    }

    @Bean("objektIngestScheduler")
    ThreadPoolTaskScheduler objektIngestScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("objekt-ingest-");
        return scheduler;
    }
}
