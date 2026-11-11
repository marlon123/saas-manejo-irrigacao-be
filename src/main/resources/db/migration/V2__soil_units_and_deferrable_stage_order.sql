-- ============================================================================
-- V2 — Unidades explícitas de solo e unicidade adiada de ordem de estágio
-- ============================================================================

-- Req 3.14: cada parâmetro de solo carrega uma unidade de medida explícita.
-- Os DEFAULTs existem apenas para preencher linhas pré-existentes e são removidos
-- em seguida, para que novas linhas sempre informem a unidade.
ALTER TABLE soil_profiles
    ADD COLUMN soil_water_unit      VARCHAR(50) NOT NULL DEFAULT 'VOLUMETRIC_WATER_CONTENT',
    ADD COLUMN bulk_density_unit    VARCHAR(50) NOT NULL DEFAULT 'GRAMS_PER_CUBIC_CENTIMETER',
    ADD COLUMN root_depth_unit      VARCHAR(50) NOT NULL DEFAULT 'MILLIMETERS',
    ADD COLUMN available_water_unit VARCHAR(50) NOT NULL DEFAULT 'MILLIMETERS';

ALTER TABLE soil_profiles
    ALTER COLUMN soil_water_unit      DROP DEFAULT,
    ALTER COLUMN bulk_density_unit    DROP DEFAULT,
    ALTER COLUMN root_depth_unit      DROP DEFAULT,
    ALTER COLUMN available_water_unit DROP DEFAULT;

-- Ao atualizar os estágios de uma cultura, o Hibernate executa INSERTs antes dos
-- DELETEs. A unicidade (crop_id, stage_order) passa a ser verificada no commit.
ALTER TABLE crop_stages DROP CONSTRAINT uq_crop_stage_order;
ALTER TABLE crop_stages
    ADD CONSTRAINT uq_crop_stage_order UNIQUE (crop_id, stage_order)
    DEFERRABLE INITIALLY DEFERRED;
