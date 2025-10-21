package com.suhoi.persistence.repo;

import com.suhoi.persistence.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByAssetAndTsBetweenOrderByTsAsc(String asset, Instant from, Instant to);
}
