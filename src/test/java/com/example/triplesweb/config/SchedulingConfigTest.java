package com.example.triplesweb.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingConfigTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfig.class);

    @Test
    void schedulingIsEnabledByDefault() {
        context.run(result -> assertThat(result).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    void previewCanDisableScheduling() {
        context.withPropertyValues("app.scheduling.enabled=false")
                .run(result -> assertThat(result).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
    }
}
