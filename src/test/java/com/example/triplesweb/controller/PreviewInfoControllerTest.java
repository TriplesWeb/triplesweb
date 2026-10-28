package com.example.triplesweb.controller;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class PreviewInfoControllerTest {
    @Test
    void previewExposesOnlyPublicBuildMetadata() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("preview");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "app.preview.number", "26",
                    "app.preview.revision", "abc1234",
                    "app.preview.snapshot-at", "2026-10-28T00:00:00Z",
                    "app.csrf.cookie-name", "PR26-XSRF-TOKEN")));
            context.register(PreviewInfoController.class, PreviewModelAdvice.class);
            context.refresh();
            assertThat(context.getBean(PreviewInfoController.class).info()).containsExactlyInAnyOrderEntriesOf(
                    Map.of("pr", 26, "revision", "abc1234", "snapshotAt", "2026-10-28T00:00:00Z"));
            assertThat(context.getBean(PreviewModelAdvice.class).csrfCookieName()).isEqualTo("PR26-XSRF-TOKEN");
        }
    }

    @Test
    void productionHasNoPreviewControllerOrModel() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(PreviewInfoController.class, PreviewModelAdvice.class);
            context.refresh();
            assertThat(context.getBeansOfType(PreviewInfoController.class)).isEmpty();
            assertThat(context.getBeansOfType(PreviewModelAdvice.class)).isEmpty();
        }
    }
}
