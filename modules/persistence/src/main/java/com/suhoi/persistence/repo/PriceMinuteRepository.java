package com.suhoi.persistence.repo;

import com.suhoi.persistence.entity.PriceMinute;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceMinuteRepository extends JpaRepository<PriceMinute, PriceMinute.Id> {}

