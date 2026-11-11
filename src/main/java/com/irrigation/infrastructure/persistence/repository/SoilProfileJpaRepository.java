package com.irrigation.infrastructure.persistence.repository;

import com.irrigation.infrastructure.persistence.entity.SoilProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SoilProfileJpaRepository extends JpaRepository<SoilProfileEntity, UUID> {
}
