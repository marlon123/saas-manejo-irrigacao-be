-- ============================================================================
-- V5: plano de manejo, meteorologia diária e Irrigação Real (módulo de balanço hídrico)
-- Cria: management_plans, daily_weather, applied_irrigation.
--
-- - Chaves estrangeiras com ON DELETE RESTRICT: área, solo e cultura referenciados não
--   podem ser excluídos (a aplicação traduz a violação em erro 422).
-- - created_at/updated_at são mantidas só pelo banco: defaults em UTC (como na V3) e
--   trigger set_updated_at() da V3, reutilizada aqui.
-- - O banco garante as invariantes de concorrência: um plano ativo por área e uma linha
--   de meteorologia e de Irrigação Real por área e data.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Tabela: management_plans
-- Plano ativo: end_date IS NULL. F (fração de depleção) e taxa de aplicação do aspersor.
-- ----------------------------------------------------------------------------
CREATE TABLE management_plans (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    area_id             UUID            NOT NULL,
    crop_id             UUID            NOT NULL,
    soil_profile_id     UUID            NOT NULL,
    start_date          DATE            NOT NULL,
    end_date            DATE            NULL,
    f_factor            NUMERIC(5, 4)   NOT NULL,
    sprinkler_rate_mm_h NUMERIC(10, 4)  NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    updated_at          TIMESTAMP       NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),

    CONSTRAINT fk_management_plans_area FOREIGN KEY (area_id)
        REFERENCES areas (id) ON DELETE RESTRICT,
    CONSTRAINT fk_management_plans_crop FOREIGN KEY (crop_id)
        REFERENCES crops (id) ON DELETE RESTRICT,
    CONSTRAINT fk_management_plans_soil_profile FOREIGN KEY (soil_profile_id)
        REFERENCES soil_profiles (id) ON DELETE RESTRICT,
    CONSTRAINT chk_management_plans_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT chk_management_plans_f_factor CHECK (f_factor > 0 AND f_factor <= 1),
    CONSTRAINT chk_management_plans_sprinkler_rate CHECK (sprinkler_rate_mm_h > 0 AND sprinkler_rate_mm_h <= 500)
);

-- No máximo um plano ativo (sem data de término) por área.
CREATE UNIQUE INDEX uq_management_plans_active_area
    ON management_plans (area_id)
    WHERE end_date IS NULL;

CREATE INDEX idx_management_plans_area_id ON management_plans (area_id);
CREATE INDEX idx_management_plans_crop_id ON management_plans (crop_id);
CREATE INDEX idx_management_plans_soil_profile_id ON management_plans (soil_profile_id);

CREATE TRIGGER trg_management_plans_updated_at
    BEFORE UPDATE ON management_plans
    FOR EACH ROW
    WHEN (OLD.* IS DISTINCT FROM NEW.*)
    EXECUTE FUNCTION set_updated_at();

-- ----------------------------------------------------------------------------
-- Tabela: daily_weather
-- Meteorologia informada manualmente: ETP e precipitação (mm), uma linha por área e data.
-- ----------------------------------------------------------------------------
CREATE TABLE daily_weather (
    id               UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    area_id          UUID            NOT NULL,
    weather_date     DATE            NOT NULL,
    etp_mm           NUMERIC(8, 3)   NOT NULL,
    precipitation_mm NUMERIC(8, 3)   NOT NULL,
    created_at       TIMESTAMP       NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    updated_at       TIMESTAMP       NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),

    CONSTRAINT fk_daily_weather_area FOREIGN KEY (area_id)
        REFERENCES areas (id) ON DELETE RESTRICT,
    CONSTRAINT uq_daily_weather_area_date UNIQUE (area_id, weather_date),
    CONSTRAINT chk_daily_weather_etp CHECK (etp_mm >= 0),
    CONSTRAINT chk_daily_weather_precipitation CHECK (precipitation_mm >= 0)
);

CREATE TRIGGER trg_daily_weather_updated_at
    BEFORE UPDATE ON daily_weather
    FOR EACH ROW
    WHEN (OLD.* IS DISTINCT FROM NEW.*)
    EXECUTE FUNCTION set_updated_at();

-- ----------------------------------------------------------------------------
-- Tabela: applied_irrigation
-- Irrigação Real (mm) informada manualmente, uma linha por área e data.
-- ----------------------------------------------------------------------------
CREATE TABLE applied_irrigation (
    id                    UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    area_id               UUID            NOT NULL,
    irrigation_date       DATE            NOT NULL,
    applied_irrigation_mm NUMERIC(8, 3)   NOT NULL,
    created_at            TIMESTAMP       NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    updated_at            TIMESTAMP       NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),

    CONSTRAINT fk_applied_irrigation_area FOREIGN KEY (area_id)
        REFERENCES areas (id) ON DELETE RESTRICT,
    CONSTRAINT uq_applied_irrigation_area_date UNIQUE (area_id, irrigation_date),
    CONSTRAINT chk_applied_irrigation_mm CHECK (applied_irrigation_mm >= 0)
);

CREATE TRIGGER trg_applied_irrigation_updated_at
    BEFORE UPDATE ON applied_irrigation
    FOR EACH ROW
    WHEN (OLD.* IS DISTINCT FROM NEW.*)
    EXECUTE FUNCTION set_updated_at();
