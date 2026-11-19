-- ============================================================================
-- V4 — Campos opcionais e novos campos para o módulo de balanço hídrico
-- Migração apenas aditiva e de relaxamento: não altera valores existentes.
-- As constraints CHECK de V1 (chk_fc_range, chk_pwp_range, chk_fc_gt_pwp,
-- chk_root_depth_*, chk_area_ha_*, chk_flow_rate_*) continuam valendo, pois um
-- CHECK com valor nulo é aceito pelo PostgreSQL.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- soil_profiles: CC e PMP gravimétricos (em %), opcionais
-- ----------------------------------------------------------------------------
ALTER TABLE soil_profiles
    ADD COLUMN field_capacity_gravimetric_pct           NUMERIC(5, 2) NULL,
    ADD COLUMN permanent_wilting_point_gravimetric_pct  NUMERIC(5, 2) NULL;

ALTER TABLE soil_profiles
    ADD CONSTRAINT chk_fc_gravimetric_range
        CHECK (field_capacity_gravimetric_pct IS NULL
               OR (field_capacity_gravimetric_pct >= 0 AND field_capacity_gravimetric_pct <= 100)),
    ADD CONSTRAINT chk_pwp_gravimetric_range
        CHECK (permanent_wilting_point_gravimetric_pct IS NULL
               OR (permanent_wilting_point_gravimetric_pct >= 0 AND permanent_wilting_point_gravimetric_pct <= 100)),
    ADD CONSTRAINT chk_gravimetric_pair
        CHECK ((field_capacity_gravimetric_pct IS NULL) = (permanent_wilting_point_gravimetric_pct IS NULL)),
    ADD CONSTRAINT chk_fc_gt_pwp_gravimetric
        CHECK (field_capacity_gravimetric_pct IS NULL
               OR field_capacity_gravimetric_pct > permanent_wilting_point_gravimetric_pct);

-- ----------------------------------------------------------------------------
-- soil_profiles: campos volumétricos, profundidade e unidades passam a ser opcionais
-- ----------------------------------------------------------------------------
ALTER TABLE soil_profiles
    ALTER COLUMN field_capacity           DROP NOT NULL,
    ALTER COLUMN permanent_wilting_point  DROP NOT NULL,
    ALTER COLUMN effective_root_depth     DROP NOT NULL,
    ALTER COLUMN soil_water_unit          DROP NOT NULL,
    ALTER COLUMN bulk_density_unit        DROP NOT NULL,
    ALTER COLUMN root_depth_unit          DROP NOT NULL,
    ALTER COLUMN available_water_unit     DROP NOT NULL;

ALTER TABLE soil_profiles
    ADD CONSTRAINT chk_volumetric_pair
        CHECK ((field_capacity IS NULL) = (permanent_wilting_point IS NULL));

-- ----------------------------------------------------------------------------
-- crops: Kc único e profundidade radicular (mm), opcionais
-- ----------------------------------------------------------------------------
ALTER TABLE crops
    ADD COLUMN kc            NUMERIC(4, 2)  NULL,
    ADD COLUMN root_depth_mm NUMERIC(10, 2) NULL;

ALTER TABLE crops
    ADD CONSTRAINT chk_crop_kc_range
        CHECK (kc IS NULL OR (kc > 0 AND kc <= 2.0)),
    ADD CONSTRAINT chk_crop_root_depth_mm_range
        CHECK (root_depth_mm IS NULL OR (root_depth_mm > 0 AND root_depth_mm <= 5000));

-- ----------------------------------------------------------------------------
-- areas: área, tipo de sistema e vazão passam a ser opcionais
-- ----------------------------------------------------------------------------
ALTER TABLE areas
    ALTER COLUMN area_ha                 DROP NOT NULL,
    ALTER COLUMN irrigation_system_type  DROP NOT NULL,
    ALTER COLUMN system_flow_rate        DROP NOT NULL;
