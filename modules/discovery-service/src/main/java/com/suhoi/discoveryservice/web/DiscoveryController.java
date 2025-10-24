package com.suhoi.discoveryservice.web;

import com.suhoi.discoveryservice.DiscoveryRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/control/discovery")
class DiscoveryController {
    private final DiscoveryRunner runner;

    @PostMapping("/refresh")
    public void refresh() { runner.refresh(); }
}

