-- Migração propositalmente inválida, usada só por MigrationIntegrationTest (Req 8.5).
-- Fica fora de db/migration e só entra quando o teste adiciona esta pasta às locations.
ALTER TABLE tabela_que_nao_existe ADD COLUMN coluna INTEGER;
