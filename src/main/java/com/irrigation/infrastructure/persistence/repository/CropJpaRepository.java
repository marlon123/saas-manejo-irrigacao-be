package com.irrigation.infrastructure.persistence.repository;

import com.irrigation.infrastructure.persistence.entity.CropEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CropJpaRepository extends JpaRepository<CropEntity, UUID> {
}
