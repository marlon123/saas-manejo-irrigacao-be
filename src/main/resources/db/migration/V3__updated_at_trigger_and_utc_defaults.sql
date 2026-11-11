-- ============================================================================
-- V3: manutenção automática de updated_at e datas de auditoria em UTC
--
-- As colunas created_at/updated_at (V1) não são mapeadas pelas entidades JPA; o banco
-- é o único responsável por elas.
--
-- 1. Defaults em UTC. As colunas são TIMESTAMP (sem fuso) e now() converte para o fuso
--    da sessão, que o driver JDBC define a partir da JVM. "now() AT TIME ZONE 'UTC'"
--    grava sempre UTC, alinhado ao restante da aplicação. Linhas já existentes não são
--    convertidas: o fuso em que foram gravadas não é conhecido.
-- 2. Trigger BEFORE UPDATE que atualiza updated_at apenas quando o conteúdo da linha muda.
--
-- Limitação conhecida: em crops, alterar somente os estágios (tabela crop_stages) não
-- altera a linha da cultura e, portanto, não atualiza crops.updated_at.
-- ============================================================================

ALTER TABLE areas
    ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'UTC'),
    ALTER COLUMN updated_at SET DEFAULT (now() AT TIME ZONE 'UTC');

ALTER TABLE crops
    ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'UTC'),
    ALTER COLUMN updated_at SET DEFAULT (now() AT TIME ZONE 'UTC');

ALTER TABLE soil_profiles
    ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'UTC'),
    ALTER COLUMN updated_at SET DEFAULT (now() AT TIME ZONE 'UTC');

CREATE FUNCTION set_updated_at() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    NEW.updated_at := now() AT TIME ZONE 'UTC';
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_areas_updated_at
    BEFORE UPDATE ON areas
    FOR EACH ROW
    WHEN (OLD.* IS DISTINCT FROM NEW.*)
    EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_crops_updated_at
    BEFORE UPDATE ON crops
    FOR EACH ROW
    WHEN (OLD.* IS DISTINCT FROM NEW.*)
    EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_soil_profiles_updated_at
    BEFORE UPDATE ON soil_profiles
    FOR EACH ROW
    WHEN (OLD.* IS DISTINCT FROM NEW.*)
    EXECUTE FUNCTION set_updated_at();
