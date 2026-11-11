-- ============================================================================
-- Migração inicial — V1__init_schema.sql
-- Cria as tabelas: areas, crops, crop_stages, soil_profiles, recommendations
-- (Req 8.3)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Tabela: areas
-- Armazena áreas de cultivo (Req 1.12)
-- ----------------------------------------------------------------------------
CREATE TABLE areas (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name                    VARCHAR(255)    NOT NULL,
    area_ha                 NUMERIC(12, 2)  NOT NULL,
    latitude                NUMERIC(9, 6)   NULL,
    longitude               NUMERIC(9, 6)   NULL,
    general_characteristics TEXT            NULL,
    irrigation_system_type  VARCHAR(50)     NOT NULL,
    system_flow_rate        NUMERIC(12, 2)  NOT NULL,
    irrigation_efficiency   NUMERIC(5, 4)   NULL,
    created_at              TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT now(),

    CONSTRAINT chk_area_ha_positive CHECK (area_ha > 0),
    CONSTRAINT chk_area_ha_max CHECK (area_ha <= 1000000.00),
    CONSTRAINT chk_flow_rate_positive CHECK (system_flow_rate > 0),
    CONSTRAINT chk_flow_rate_max CHECK (system_flow_rate <= 1000000.00),
    CONSTRAINT chk_latitude_range CHECK (latitude IS NULL OR (latitude >= -90 AND latitude <= 90)),
    CONSTRAINT chk_longitude_range CHECK (longitude IS NULL OR (longitude >= -180 AND longitude <= 180)),
    CONSTRAINT chk_efficiency_range CHECK (irrigation_efficiency IS NULL OR (irrigation_efficiency >= 0.01 AND irrigation_efficiency <= 1))
);

-- ----------------------------------------------------------------------------
-- Tabela: crops
-- Armazena culturas agrícolas (Req 2.11)
-- ----------------------------------------------------------------------------
CREATE TABLE crops (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name                    VARCHAR(255)    NOT NULL,
    variety                 VARCHAR(255)    NULL,
    cultivation_cycle_days  INTEGER         NULL,
    created_at              TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT now()
);

-- ----------------------------------------------------------------------------
-- Tabela: crop_stages
-- Armazena estágios de desenvolvimento de culturas com Kc (Req 2.12)
-- ----------------------------------------------------------------------------
CREATE TABLE crop_stages (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    crop_id     UUID            NOT NULL REFERENCES crops(id) ON DELETE CASCADE,
    name        VARCHAR(100)    NOT NULL,
    kc          NUMERIC(4, 2)   NOT NULL,
    stage_order INTEGER         NOT NULL,

    CONSTRAINT chk_kc_positive CHECK (kc > 0),
    CONSTRAINT chk_kc_max CHECK (kc <= 2.0),
    CONSTRAINT chk_stage_order_positive CHECK (stage_order > 0),
    CONSTRAINT uq_crop_stage_order UNIQUE (crop_id, stage_order)
);

-- ----------------------------------------------------------------------------
-- Tabela: soil_profiles
-- Armazena parâmetros de solo (Req 3.10, 3.14)
-- ----------------------------------------------------------------------------
CREATE TABLE soil_profiles (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    soil_type               VARCHAR(255)    NOT NULL,
    field_capacity          NUMERIC(5, 4)   NOT NULL,
    permanent_wilting_point NUMERIC(5, 4)   NOT NULL,
    bulk_density            NUMERIC(6, 3)   NOT NULL,
    effective_root_depth    NUMERIC(10, 2)  NOT NULL,
    available_water         NUMERIC(10, 2)  NULL,
    created_at              TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT now(),

    CONSTRAINT chk_fc_range CHECK (field_capacity >= 0 AND field_capacity <= 1),
    CONSTRAINT chk_pwp_range CHECK (permanent_wilting_point >= 0 AND permanent_wilting_point <= 1),
    CONSTRAINT chk_fc_gt_pwp CHECK (field_capacity > permanent_wilting_point),
    CONSTRAINT chk_bulk_density_positive CHECK (bulk_density > 0),
    CONSTRAINT chk_bulk_density_max CHECK (bulk_density <= 3.00),
    CONSTRAINT chk_root_depth_positive CHECK (effective_root_depth > 0),
    CONSTRAINT chk_root_depth_max CHECK (effective_root_depth <= 5000.00)
);

-- ----------------------------------------------------------------------------
-- Tabela: recommendations
-- Armazena recomendações de irrigação (registros de cálculo) (Req 5.1, 5.7, 8.3)
-- input_data e results como JSONB para snapshot imutável
-- ----------------------------------------------------------------------------
CREATE TABLE recommendations (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    area_id             UUID            NOT NULL REFERENCES areas(id) ON DELETE RESTRICT,
    date_time           TIMESTAMP       NOT NULL,
    input_data          JSONB           NOT NULL,
    results             JSONB           NOT NULL,
    methodology_id      VARCHAR(100)    NOT NULL,
    methodology_version VARCHAR(50)     NOT NULL,

    CONSTRAINT fk_recommendation_area FOREIGN KEY (area_id) REFERENCES areas(id)
);

-- ----------------------------------------------------------------------------
-- Índices
-- ----------------------------------------------------------------------------
CREATE INDEX idx_crop_stages_crop_id ON crop_stages(crop_id);
CREATE INDEX idx_recommendations_area_id ON recommendations(area_id);
CREATE INDEX idx_recommendations_date_time ON recommendations(date_time);
