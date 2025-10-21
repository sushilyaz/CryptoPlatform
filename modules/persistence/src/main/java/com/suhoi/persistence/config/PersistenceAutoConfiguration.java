package com.suhoi.persistence.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@EntityScan(basePackages = "com.suhoi.persistence.entity")
@EnableJpaRepositories(basePackages = "com.suhoi.persistence.repo")
public class PersistenceAutoConfiguration {
}

