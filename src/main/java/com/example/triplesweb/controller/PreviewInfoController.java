package com.example.triplesweb.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@Profile("preview")
@RestController
public class PreviewInfoController {
    private final int pr;
    private final String revision;
    private final String snapshotAt;

    public PreviewInfoController(@Value("${app.preview.number}") int pr,
                                 @Value("${app.preview.revision}") String revision,
                                 @Value("${app.preview.snapshot-at}") String snapshotAt) {
        this.pr = pr;
        this.revision = revision;
        this.snapshotAt = snapshotAt;
    }

    @GetMapping("/api/preview/info")
    public Map<String, Object> info() {
        return Map.of("pr", pr, "revision", revision, "snapshotAt", snapshotAt);
    }
}
