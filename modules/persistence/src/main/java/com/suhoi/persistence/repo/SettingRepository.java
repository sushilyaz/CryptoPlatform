package com.suhoi.persistence.repo;

import com.suhoi.persistence.entity.Setting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<Setting, String> {}
