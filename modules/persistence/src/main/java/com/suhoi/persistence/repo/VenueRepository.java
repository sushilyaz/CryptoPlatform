package com.suhoi.persistence.repo;

import com.suhoi.persistence.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueRepository extends JpaRepository<Venue, String> {}
