-- Migração propositalmente inválida, usada só por WaterBalanceMigrationIntegrationTest (Req 16.5).
-- O primeiro comando é válido e o segundo falha: o PostgreSQL executa a migração numa
-- transação, então a coluna abaixo não pode permanecer no schema.
ALTER TABLE areas ADD COLUMN should_not_persist INTEGER;
ALTER TABLE tabela_que_nao_existe ADD COLUMN coluna INTEGER;
