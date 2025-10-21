package com.suhoi.persistence.repo;

import com.suhoi.market.MarketKind;
import com.suhoi.persistence.entity.Market;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketRepository extends JpaRepository<Market, Long> {
    Optional<Market> findByAssetAndVenueAndKind(String asset, String venue, MarketKind kind);
    List<Market> findByAsset(String asset);
}
