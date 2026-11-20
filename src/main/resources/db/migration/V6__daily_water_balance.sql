-- ============================================================================
-- V6: balanço hídrico diário imutável (módulo de balanço hídrico)
-- Cria: daily_water_balance, com triggers de imutabilidade.
--
-- - Cada recálculo grava uma nova revisão; a revisão anterior apenas deixa de ser a corrente
--   (is_current passa de true para false). Nenhuma outra alteração e nenhuma exclusão é aceita.
-- - Não há updated_at: o registro não é atualizado.
-- - Entradas são gravadas como snapshot, com a mesma escala da coluna de origem.
-- - ETc, CAD, AFDi, AFDf e I usam NUMERIC sem escala, que o PostgreSQL grava de forma exata
--   (a escala exata de AFDi e AFDf chega a 13 casas). Podem ser negativos: sem CHECK de sinal.
-- - Chaves estrangeiras com ON DELETE RESTRICT. Os nomes fk_* contêm 'water_balance' (o
--   ForeignKeyViolationTranslator usa isso) e não contêm 'management_plan', 'daily_weather'
--   nem 'applied_irrigation', que o tradutor verifica antes.
-- ============================================================================

CREATE TABLE daily_water_balance (
    -- Identificação
    id                       UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    area_id                  UUID            NOT NULL,
    balance_date             DATE            NOT NULL,
    crop_id                  UUID            NOT NULL,
    soil_profile_id          UUID            NOT NULL,
    management_plan_id       UUID            NOT NULL,
    cycle_day_tr             INTEGER         NOT NULL,

    -- Entradas (snapshot, mesma escala da coluna de origem)
    etp_mm                   NUMERIC(8, 3)   NOT NULL,
    precipitation_mm         NUMERIC(8, 3)   NOT NULL,
    applied_irrigation_mm    NUMERIC(8, 3)   NOT NULL,
    field_capacity_pct       NUMERIC(5, 2)   NOT NULL,
    wilting_point_pct        NUMERIC(5, 2)   NOT NULL,
    bulk_density_g_cm3       NUMERIC(6, 3)   NOT NULL,
    root_depth_mm            NUMERIC(10, 2)  NOT NULL,
    kc                       NUMERIC(4, 2)   NOT NULL,
    f_factor                 NUMERIC(5, 4)   NOT NULL,
    sprinkler_rate_mm_h      NUMERIC(10, 4)  NOT NULL,

    -- Resultados
    etc_mm                   NUMERIC         NOT NULL,
    cad_mm                   NUMERIC         NOT NULL,
    afd_initial_mm           NUMERIC         NOT NULL,
    afd_final_mm             NUMERIC         NOT NULL,
    ideal_irrigation_mm      NUMERIC         NOT NULL,
    irrigation_recommended   BOOLEAN         NOT NULL,
    no_recommendation_reason VARCHAR(50)     NULL,
    operating_time_hours     NUMERIC(24, 10) NOT NULL,

    -- Controle
    calculated_at            TIMESTAMP       NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    methodology_id           VARCHAR(100)    NOT NULL,
    methodology_version      VARCHAR(50)     NOT NULL,
    revision                 INTEGER         NOT NULL,
    is_current               BOOLEAN         NOT NULL,

    CONSTRAINT fk_daily_water_balance_area FOREIGN KEY (area_id)
        REFERENCES areas (id) ON DELETE RESTRICT,
    CONSTRAINT fk_daily_water_balance_crop FOREIGN KEY (crop_id)
        REFERENCES crops (id) ON DELETE RESTRICT,
    CONSTRAINT fk_daily_water_balance_soil_profile FOREIGN KEY (soil_profile_id)
        REFERENCES soil_profiles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_daily_water_balance_plan FOREIGN KEY (management_plan_id)
        REFERENCES management_plans (id) ON DELETE RESTRICT,
    CONSTRAINT uq_daily_water_balance_revision UNIQUE (area_id, balance_date, revision),
    CONSTRAINT chk_daily_water_balance_cycle_day CHECK (cycle_day_tr >= 1),
    CONSTRAINT chk_daily_water_balance_operating_time CHECK (operating_time_hours >= 0),
    CONSTRAINT chk_daily_water_balance_revision CHECK (revision >= 1)
);

-- No máximo uma revisão corrente por área e data.
CREATE UNIQUE INDEX uq_daily_water_balance_current
    ON daily_water_balance (area_id, balance_date)
    WHERE is_current;

-- Consulta por área e período.
CREATE INDEX idx_daily_water_balance_area_date
    ON daily_water_balance (area_id, balance_date);

-- ----------------------------------------------------------------------------
-- Imutabilidade (Req 12.7 e 12.8)
-- ERRCODE 23000 (integrity_constraint_violation): classe de violação de restrição,
-- diferente de 23503 (foreign_key_violation), para o adaptador distinguir os dois casos.
-- Mensagem identificável: immutable_daily_water_balance.
-- ----------------------------------------------------------------------------
CREATE FUNCTION reject_daily_water_balance_delete() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'immutable_daily_water_balance: registros de balanço hídrico não podem ser excluídos'
        USING ERRCODE = '23000';
END;
$$ LANGUAGE plpgsql;

-- Aceita somente a passagem de is_current de true para false, sem mudar nenhuma outra coluna.
CREATE FUNCTION reject_daily_water_balance_update() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.is_current IS TRUE
       AND NEW.is_current IS FALSE
       AND (to_jsonb(OLD) - 'is_current') = (to_jsonb(NEW) - 'is_current') THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'immutable_daily_water_balance: registros de balanço hídrico não podem ser alterados'
        USING ERRCODE = '23000';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_daily_water_balance_no_delete
    BEFORE DELETE ON daily_water_balance
    FOR EACH ROW
    EXECUTE FUNCTION reject_daily_water_balance_delete();

CREATE TRIGGER trg_daily_water_balance_no_update
    BEFORE UPDATE ON daily_water_balance
    FOR EACH ROW
    EXECUTE FUNCTION reject_daily_water_balance_update();
