package com.suhoi.persistence.repo;

import com.suhoi.persistence.entity.FairMinute;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FairMinuteRepository extends JpaRepository<FairMinute, FairMinute.Id> {}

