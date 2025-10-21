package com.suhoi.discoveryservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DiscoveryProperties.class)
public class DiscoveryConfig {}

