package com.suhoi.discoveryservice.web;

import com.suhoi.discoveryservice.core.DiscoveryOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/discovery")
public class DiscoveryController {
    private final DiscoveryOrchestrator orchestrator;

    @PostMapping("/reload")
    public ResponseEntity<String> reload() {
        orchestrator.runOnce();
        return ResponseEntity.ok("Discovery reload triggered");
    }
}

