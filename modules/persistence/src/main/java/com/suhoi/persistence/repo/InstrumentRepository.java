package com.suhoi.persistence.repo;

import com.suhoi.persistence.entity.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstrumentRepository extends JpaRepository<Instrument, String> {}
