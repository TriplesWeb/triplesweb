package com.example.triplesweb.controller;

import com.example.triplesweb.config.InFlightRequestTracker;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 배포 스크립트가 컨테이너 IP로 직접 호출한다 (Nginx 전환 후 이전 슬롯 drain).
 */
@RestController
public class DeployDrainController {

    private final InFlightRequestTracker tracker;

    public DeployDrainController(InFlightRequestTracker tracker) {
        this.tracker = tracker;
    }

    @GetMapping(InFlightRequestTracker.DRAIN_PATH)
    public Map<String, Integer> inflight() {
        return Map.of("inFlight", tracker.current());
    }
}
