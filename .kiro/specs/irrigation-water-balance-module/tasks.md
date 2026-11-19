# Implementation Plan: irrigation-water-balance-module

> **Idioma:** Português (pt-br) | **Workflow:** Requirements-first | **Spec type:** Feature

## Overview

O plano tem 7 fases, na ordem do item 8 do `requirements.md` e do "Plano de entrega em fases" do `design.md`. Cada fase termina em um checkpoint com build, testes e relatórios conferidos (contagens nos XML de `build/test-results`, relatório do JaCoCo, `integrationTest` quando a fase mexe em banco). Depois do checkpoint, a próxima fase só começa com a aprovação do usuário.

- [x] Fase 1: Cadastros opcionais e migração V4 (Solo, Cultura e Área), com a revisão dos testes afetados (A-01)
- [ ] Fase 2: Value objects, `WaterBalanceCalculator` puro e `PresentationRounding`
- [ ] Fase 3: Plano de Manejo, meteorologia e Irrigação Real (migração V5, domínio, persistência e serviços)
- [ ] Fase 4: Balanço diário imutável (migração V6), cálculo, reprocessamento e consulta (domínio, persistência e serviços)
- [ ] Fase 5: Controladores, DTOs, tratamento de erros e OpenAPI
- [ ] Fase 6: Testes de integração ponta a ponta, ArchUnit e cobertura
- [ ] Fase 7: README e ADR-006

### Regras de execução de cada fase

- **Fórmulas:** somente as de `requirements.md` (seção 4). Não inventar regra agronômica. A `FakeCalculationMethod` dos testes não é metodologia, e `CalculationEngine`, `CalculationMethod`, `Recommendation` e `/api/v1/recommendations/calculate` não são alterados (Requisito 16.2).
- **Idioma:** documentação nova em pt-br. Identificadores de código em inglês.
- **Ambiente:** definir `JAVA_HOME` antes do Gradle e usar o wrapper. Para `integrationTest`, o Docker Desktop precisa estar aberto (`docker version` deve mostrar a linha do servidor).
- **Comando de verificação completa** (cerca de 1 min 45 s, PowerShell):
  `$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.13.0.3"; $env:GRADLE_EXIT_CONSOLE = "1"; .\gradlew.bat clean test integrationTest jacocoTestCoverageVerification --no-daemon --console=plain`
- **Fases sem banco (Fase 2):** basta `clean test jacocoTestCoverageVerification`. Mesmo assim, o checkpoint da Fase 2 roda o `integrationTest` de regressão se o Docker estiver aberto.
- **Status:** uma fase só é marcada como concluída depois de conferir os números nos relatórios, e não só o `BUILD SUCCESSFUL`.

---

## Tasks

**Fase 1: Cadastros opcionais e migração V4**

- [x] 1. Migração V4 (relaxamento e colunas novas)
  - [x] 1.1 Criar `src/main/resources/db/migration/V4__water_balance_optional_fields.sql`
    - `soil_profiles`: adicionar `field_capacity_gravimetric_pct` e `permanent_wilting_point_gravimetric_pct` (`NUMERIC(5,2) NULL`), com CHECK de 0 a 100, "ambos nulos ou ambos preenchidos" e `cc > pmp` quando preenchidos
    - `soil_profiles`: `DROP NOT NULL` em `field_capacity`, `permanent_wilting_point`, `effective_root_depth` e nas quatro colunas de unidade, mais CHECK de "par volumétrico ambos nulos ou ambos preenchidos"
    - `crops`: adicionar `kc NUMERIC(4,2) NULL` (CHECK `> 0 AND <= 2.0`) e `root_depth_mm NUMERIC(10,2) NULL` (CHECK `> 0 AND <= 5000`)
    - `areas`: `DROP NOT NULL` em `area_ha`, `irrigation_system_type` e `system_flow_rate`
    - Nenhum valor existente é alterado. Não editar V1, V2 nem V3
    - _Requisitos: 1.1, 1.6, 1.7, 1.8, 2.1, 3.1, 16.3, 16.4_
  - [x] 1.2 Estender o teste de integração de migração com dados preexistentes
    - Em `MigrationIntegrationTest` (ou classe nova na mesma pasta), migrar até V3, inserir áreas, solos, culturas com estágios e recomendações, migrar até V4 e comparar contagem por tabela e valores de todas as colunas antigas
    - Conferir colunas novas nulas, CHECKs rejeitando valores inválidos, e que os checksums de V1, V2 e V3 em `flyway_schema_history` não mudaram
    - Manter o padrão da migração com falha (`db/failing-migration`): falha não deixa alteração parcial
    - _Requisitos: 16.3, 16.4, 16.5, 1.11, 2.7, 3.4_

- [x] 2. Domínio dos cadastros
  - [x] 2.1 Criar o value object `GravimetricWaterContent` e a regra de escala (A-08)
    - Record validado em `domain.model.valueobjects`: 0 a 100 inclusive, no máximo 2 casas decimais
    - Antes de mudar `Kc` e `RootDepth`, conferir as regras atuais e os testes que dependem delas. Se mudar o value object quebrar testes existentes, aplicar a regra de "no máximo 2 casas" na validação de `Crop` (campos `kc` e `rootDepth`) e de `SoilProfile`
    - _Requisitos: 1.1, 1.2, 2.2, 2.3_
  - [x] 2.2 Alterar `SoilProfile`
    - Campos novos `fieldCapacityGravimetric` e `permanentWiltingPointGravimetric`. Campos volumétricos, profundidade efetiva, água disponível e as quatro unidades passam a aceitar nulo
    - Regra "ambos ou nenhum" para cada par e `CC > PMP` quando presente (A-07). `BulkDensity` continua obrigatório
    - Manter construtores de compatibilidade e os métodos estáticos `validateX`, para cada erro sair no campo certo
    - _Requisitos: 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10_
  - [x] 2.3 Alterar `Crop`
    - Campos opcionais `kc` (`Kc`) e `rootDepth` (`RootDepth`). `validateStages` aceita de 0 a 20 estágios. Manter construtores de compatibilidade
    - _Requisitos: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.11_
  - [x] 2.4 Alterar `Area`
    - `areaHectares`, `irrigationSystemType` e `systemFlowRate` aceitam nulo. O nome continua obrigatório (não vazio, até 255 caracteres). Valores presentes seguem validados
    - _Requisitos: 3.1, 3.2, 3.3_

- [x] 3. Persistência, aplicação e apresentação dos cadastros
  - [x] 3.1 Atualizar entidades JPA e mapeadores manuais de solo, cultura e área
    - Colunas novas e nulos nas entidades. `ddl-auto: validate` deve continuar passando. `created_at` e `updated_at` continuam fora do mapeamento (V3)
    - _Requisitos: 1.1, 1.6, 2.1, 3.1, 1.11, 2.7, 3.4_
  - [x] 3.2 Ordenar áreas por `areaHectares` com `nullsLast`
    - Ajustar `AreaRepositoryAdapter` para o campo agora nulo, sem mudar o contrato da porta `AreaRepository`
    - _Requisitos: 3.1, 16.1_
  - [x] 3.3 Atualizar DTOs e serviços de aplicação de solo, cultura e área
    - `SoilProfileInput/Output`, `CropInput/Output` e `AreaInput/Output` com os campos novos, preservando os construtores atuais dos records
    - Serviços acumulam todas as violações com `ViolationCollector` (par volumétrico incompleto gera 400 com os campos do par)
    - Os construtores dos serviços não mudam (DD-06)
    - _Requisitos: 1.4, 1.5, 1.8, 1.9, 2.4, 2.10, 2.11, 3.1, 3.3_
  - [x] 3.4 Atualizar DTOs, MapStruct e anotações de Bean Validation da apresentação
    - Nomes, tipos e significados dos campos atuais permanecem. Campos novos de CC e PMP gravimétricos em solo, e `kc` e `rootDepthMm` em cultura (nomes de campo com unidade)
    - Remover somente as obrigatoriedades que o Requisito 3.1, 1.6 e 2.5 retiram. Manter `unmappedTargetPolicy = ERROR` e a documentação OpenAPI
    - _Requisitos: 1.10, 2.6, 3.1, 16.1_

- [x] 4. Testes da Fase 1
  - [x] 4.1 Revisar os testes existentes afetados (A-01)
    - Rodar `test` e `integrationTest` antes de editar. Alterar **somente** asserções que codificam uma obrigatoriedade removida pelos Requisitos 1.6, 2.5 e 3.1, uma a uma, com a justificativa em comentário curto no teste e registrada na seção "Registro da revisão A-01" das Notes deste arquivo. Os demais testes ficam intactos
    - Candidatos encontrados por busca (a lista final é a dos testes que de fato falham por essa causa):
      - `AreaServiceTest.createRejectsMissingRequiredFields`: passa a exigir só o nome (Req 3.1)
      - `AreaControllerTest.createRejectsInvalidDataWithProblemDetailsListingEachField`: `irrigationSystemType` nulo deixa de ser violação (Req 3.1)
      - `SoilProfileServiceTest.createRequiresEveryUnit` e `DomainPropertiesTest.soilProfileRejectsAnyMissingUnit` (Propriedade 5 da spec original): unidades deixam de ser obrigatórias (Req 1.6)
      - `CropServiceTest.createRejectsAnEmptyStageList` e `AggregatesTest` (`rejectsEmptyStageList`): zero estágios passa a ser válido (Req 2.5)
      - `DomainPropertiesTest.cropAcceptsOneToTwentyStagesWithUniquePositiveOrder`: a faixa passa de 1 a 20 para 0 a 20 (Req 2.5)
      - Possíveis: testes dos controladores de solo e cultura, `MigrationVersioningTest` e `MigrationIntegrationTest` (se assumirem V3 como última versão)
    - Qualquer outro teste que falhe por causa diferente indica regressão e deve ser corrigido no código, e não no teste
    - _Requisitos: 16.8, 1.6, 2.5, 3.1_
  - [x] 4.2 Escrever testes unitários dos cadastros
    - Solo só com tipo, CC, PMP e D (201 e 200); par volumétrico incompleto (400 com os campos); par gravimétrico incompleto (A-07); CC ≤ PMP; valores com mais de 2 casas (A-08); cultura só com nome, KC e Z e zero estágios; 21 estágios recusados; área só com nome; leitura de registro antigo devolve nulos e preserva valores
    - _Requisitos: 1.4, 1.5, 1.8, 1.9, 1.11, 2.4, 2.7, 2.10, 2.11, 3.1, 3.3, 3.4_
  - [x] 4.3 Escrever teste de propriedade para pares de umidade e campos opcionais
    - **Propriedade 12: Pares de umidade e campos opcionais**
    - **Valida: Requisitos 1.4, 1.5, 1.6, 1.7, 1.8, 2.4, 2.5, 2.11, 3.1**
    - jqwik, mínimo 100 iterações, comentário `// Feature: irrigation-water-balance-module, Property 12: Pares de umidade e campos opcionais`
  - [x] 4.4 Escrever testes dos mapeadores e de regressão de integração
    - Estender `PersistenceMappersTest` e `RepositoryRoundTripIntegrationTest` para solo, cultura e área com campos nulos e com os campos novos
    - Garantir que `ApiLifecycleIntegrationTest`, `RecommendationFilterIntegrationTest` e demais testes de integração passam
    - _Requisitos: 1.10, 1.11, 2.7, 3.4, 16.1, 16.8_

- [x] 5. Checkpoint da Fase 1: build, testes e relatórios
  - Rodar `clean test integrationTest jacocoTestCoverageVerification` e conferir nos XML de `build/test-results` as contagens (comparar com o baseline de 219 unitários e 41 de integração, mais os testes novos), nenhuma falha e nenhum teste pulado
  - Conferir no relatório do JaCoCo que a cobertura de linha continua em pelo menos 80%
  - Informar ao usuário o que foi feito, as decisões, a lista final da revisão A-01 e as pendências. **Aguardar aprovação antes da Fase 2**

---

**Fase 2: Value objects, calculador puro e arredondamento de apresentação**

- [ ] 6. Value objects do módulo
  - [ ] 6.1 Criar os value objects em `domain.model.valueobjects`
    - `AvailabilityFactor` (F): maior que 0 e no máximo 1, até 4 casas, nunca dividido por 100
    - `SprinklerRate`: maior que 0 e no máximo 500 mm/h, até 4 casas
    - `EtpDepth`, `PrecipitationDepth` e `AppliedIrrigationDepth`: 0 a 1.000 mm inclusive, até 2 casas (três tipos, para a mensagem citar o campo certo)
    - `CycleDay` (TR): inteiro maior ou igual a 1, com o limite em uma constante única (A-04)
    - Records validados, com `IllegalArgumentException` fora da faixa, como `Kc` e `BulkDensity`
    - _Requisitos: 4.3, 4.4, 5.2, 6.3, 7.4, 10.6_

- [ ] 7. Calculador de domínio e política de apresentação
  - [ ] 7.1 Criar os tipos de `com.irrigation.domain.waterbalance`
    - `WaterBalanceInputs` (ETP, P, IR, CC, PMP, D, Z, KC, F, Sprinkler_Rate, `firstDayOfPlan`, `previousAfdFinal`; combinação inválida lança `IllegalArgumentException` no construtor), `WaterBalanceResult` (ETc, CAD, AFDi, AFDf, I, `recommended`, `noRecommendationReason`, `operatingTimeHours`) e o enum `NoRecommendationReason` (`FIRST_DAY_OF_FIRST_CYCLE`)
    - Constantes `METHODOLOGY_ID = "WATER_BALANCE_AFD"` e `METHODOLOGY_VERSION = "1.0"`
    - Somente `BigDecimal`. Nenhuma dependência de Spring, Jakarta, Hibernate, Jackson, `domain.repository` nem `domain.calculation`
    - _Requisitos: 7.9, 7.10, 15.1_
  - [ ] 7.2 Implementar `WaterBalanceCalculator`
    - `ETc = KC × ETP`; `CAD = 0,01 × (CC − PMP) × D × Z`; `AFDi = CAD × F` no primeiro dia e `previousAfdFinal` nos demais; `AFDf = AFDi + (P + IR − ETc)` sem limitar; `I = AFDi − AFDf` com sinal
    - `recommended = (I > 0) E (não é o primeiro dia)`; `Operating_Time = I ÷ Sprinkler_Rate` com escala 10 e `HALF_EVEN` quando recomendado, zero caso contrário; motivo `FIRST_DAY_OF_FIRST_CYCLE` no primeiro dia, independentemente do sinal de I
    - Divisor zero lança `IllegalArgumentException` ("divisor inválido"). Sem arredondamento de ETc, CAD, AFDi, AFDf e I
    - _Requisitos: 7.1, 8.1, 8.2, 8.3, 8.5, 8.6, 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 10.1, 10.2, 15.1, 15.2, 15.3, 15.9, 15.10_
  - [ ] 7.3 Implementar `PresentationRounding` em `application.waterbalance`
    - mm com 2 casas `HALF_UP`; horas com 4 casas `HALF_UP`; minutos totais `round(horas × 60, HALF_UP)` a partir do valor persistido, distribuídos em horas inteiras e minutos de 0 a 59. Tipo de saída para o tempo de funcionamento (`hours`, `wholeHours`, `minutes`)
    - Nunca usado em cálculo
    - _Requisitos: 10.3, 15.4, 15.5, 15.6, 15.7, 15.8, 14.6_
  - [ ] 7.4 Acrescentar regras de ArchUnit ao `ArchitectureTest`
    - `domain.waterbalance` não depende de Spring, Jakarta, Hibernate, `..application..`, `..infrastructure..`, `..presentation..`, `..domain.repository..` nem `..domain.calculation..`
    - Nenhuma classe de `domain.waterbalance` usa `double` ou `float` em campos ou assinaturas públicas
    - Não remover nem enfraquecer nenhuma regra existente
    - _Requisitos: 7.10, 15.1, 16.7_

- [ ] 8. Testes da Fase 2
  - [ ] 8.1 Escrever testes de limites dos value objects
    - Estender `RangeSpec` e os testes parametrizados existentes para `GravimetricWaterContent`, `AvailabilityFactor`, `SprinklerRate`, `EtpDepth`, `PrecipitationDepth`, `AppliedIrrigationDepth` e `CycleDay` (limite inferior, limite superior, imediatamente fora, escala)
    - _Requisitos: 1.1, 4.3, 4.4, 5.2, 6.3, 7.4_
  - [ ] 8.2 Escrever os testes dos Cenários A, B, C e E contra o calculador
    - Cenário A: CAD = 72 mm; AFDi do dia 1 = 36 mm; os 7 dias linha a linha (ETc, AFDi, AFDf, I, recomendado, tempo em horas, horas e minutos); dia 1 com `FIRST_DAY_OF_FIRST_CYCLE`; dia 4 com AFDi = 28; dias 3 e 5 com I negativo e tempo zero; dia 7 com 12 ÷ 6 = 2 h
    - Cenário B: F = 0,90, AFDi = 64,80, AFDf do dia 1 = 80,80 mantida acima do CAD e usada no dia 2
    - Cenário C: 12/6, 1/6, 7/6, 2,5/4 (37,5 min vira 38) e I não positivo
    - Cenário E: ETP do dia 2 de 6,25 para 7,50 gera AFDf = 26, I = 6, 1,0000 h; dia 3 com AFDi = 26 e AFDf = 27; dia 4 com AFDi = 27 e AFDf = 22. Valores comparados com `compareTo`, sem arredondamento intermediário
    - _Requisitos: 7.1, 8.1, 8.2, 8.3, 8.5, 9.2, 9.3, 9.4, 9.6, 10.1, 10.3, 15.2, 15.6_
  - [ ] 8.3 Escrever testes unitários de casos de borda do calculador e do arredondamento
    - Combinação inválida de `WaterBalanceInputs`; divisor zero; carry de 59,6 minutos para 1 h 00 min; `HALF_UP` para negativos em mm; I exatamente zero; segundo dia com I > 0 recomendado
    - _Requisitos: 9.3, 9.6, 10.3, 15.4, 15.6, 15.10_
  - [ ] 8.4 Escrever teste de propriedade para as fórmulas exatas de ETc e CAD
    - **Propriedade 1: Fórmulas exatas de ETc e CAD**
    - **Valida: Requisitos 7.1, 15.1, 15.2**
  - [ ] 8.5 Escrever teste de propriedade para a conservação do saldo de água
    - **Propriedade 2: Conservação do saldo de água**
    - **Valida: Requisitos 8.5, 8.6, 9.1, 15.2**
  - [ ] 8.6 Escrever teste de propriedade para a AFDi do primeiro dia
    - **Propriedade 3: AFDi do primeiro dia**
    - **Valida: Requisitos 8.1**
  - [ ] 8.7 Escrever teste de propriedade para a continuidade da AFDi em sequências de dias
    - **Propriedade 4: Continuidade da AFDi em sequências de dias**
    - **Valida: Requisitos 8.2, 8.3, 15.8**
    - Sequências de 1 a 30 dias com TR arbitrário, inclusive voltando a 1
  - [ ] 8.8 Escrever teste de propriedade para a regra de recomendação
    - **Propriedade 5: Regra de recomendação**
    - **Valida: Requisitos 9.2, 9.3, 9.4, 9.5, 9.6, 10.2, 6.8**
  - [ ] 8.9 Escrever teste de propriedade para o tempo de funcionamento
    - **Propriedade 6: Tempo de funcionamento**
    - **Valida: Requisitos 10.1, 15.3**
  - [ ] 8.10 Escrever teste de propriedade para a conversão para horas e minutos
    - **Propriedade 7: Conversão para horas e minutos**
    - **Valida: Requisitos 10.3, 15.6**
  - [ ] 8.11 Escrever teste de propriedade para a política de apresentação consistente
    - **Propriedade 8: Política de apresentação consistente**
    - **Valida: Requisitos 15.4, 15.5, 15.7, 15.8, 14.6**
    - Inclui idempotência da política e a verificação de que o valor persistido não muda
  - [ ] 8.12 Escrever teste de propriedade para o determinismo do calculador
    - **Propriedade 9: Determinismo do calculador**
    - **Valida: Requisitos 15.9**

- [ ] 9. Checkpoint da Fase 2: build, testes e relatórios
  - Rodar `clean test jacocoTestCoverageVerification` (e `integrationTest` se o Docker estiver aberto) e conferir contagens nos XML, ausência de falhas e de testes pulados, e a cobertura de linha do JaCoCo (inclusive dos pacotes `domain.waterbalance` e `application.waterbalance`)
  - Informar o que foi feito, as decisões e as pendências. **Aguardar aprovação antes da Fase 3**

---

**Fase 3: Plano de Manejo, meteorologia e Irrigação Real**

- [ ] 10. Migração V5
  - [ ] 10.1 Criar `V5__management_plan_and_daily_inputs.sql`
    - `management_plans`: FKs `RESTRICT` para área, cultura e solo; `end_date >= start_date`; `f_factor NUMERIC(5,4)` com CHECK `> 0 AND <= 1`; `sprinkler_rate_mm_h NUMERIC(10,4)` com CHECK `> 0 AND <= 500`; índice único parcial `uq_management_plans_active_area ON (area_id) WHERE end_date IS NULL`; trigger `set_updated_at()` reutilizado da V3
    - `daily_weather` (`UNIQUE (area_id, weather_date)`, ETP e P `NUMERIC(8,3)` `>= 0`) e `applied_irrigation` (`UNIQUE (area_id, irrigation_date)`, `NUMERIC(8,3)` `>= 0`), FKs `RESTRICT`, defaults UTC e trigger
    - _Requisitos: 4.1, 4.7, 5.7, 6.7, 16.3_

- [ ] 11. Domínio e portas
  - [ ] 11.1 Criar o agregado `ManagementPlan`
    - `id`, `areaId`, `cropId`, `soilProfileId`, `startDate`, `endDate` opcional, F e Sprinkler_Rate. Operações `updateParameters(f, rate)` e `close(endDate)`. Métodos estáticos `validateX`. `endDate` não pode ser anterior a `startDate` (A-02)
    - _Requisitos: 4.1, 4.2, 4.3, 4.4, 4.5_
  - [ ] 11.2 Criar `DailyWeather` e `AppliedIrrigation`
    - `id`, `areaId`, `date` e os valores em `EtpDepth`, `PrecipitationDepth` e `AppliedIrrigationDepth`
    - _Requisitos: 5.1, 5.2, 6.1, 6.3_
  - [ ] 11.3 Criar as portas em `domain.repository`
    - `ManagementPlanRepository` (`save`, `findById`, `findByAreaOrderByStartDateDesc`, `findActiveByArea`, `findCoveringDate`, `hasBalances`, `lastCalculatedDate`), `DailyWeatherRepository` e `AppliedIrrigationRepository` (`upsert`, `findByAreaAndDate`, `findByAreaAndPeriod` em ordem crescente)
    - `AreaRepository`, `CropRepository` e `SoilProfileRepository` não ganham métodos (DD-06)
    - _Requisitos: 4.11, 5.7, 5.9, 6.7, 6.10_

- [ ] 12. Infraestrutura
  - [ ] 12.1 Criar entidades JPA, interfaces `*JpaRepository` e mapeadores manuais
    - `ManagementPlanEntity`, `DailyWeatherEntity`, `AppliedIrrigationEntity`, sem mapear `created_at` e `updated_at`
    - _Requisitos: 4.1, 5.1, 6.1_
  - [ ] 12.2 Criar os adaptadores `*RepositoryAdapter`
    - `upsert` com `INSERT ... ON CONFLICT (area_id, <data>) DO UPDATE` nativo, seguido de leitura na mesma transação
    - Violação do índice de plano ativo vira `BusinessRuleException` (422)
    - _Requisitos: 4.7, 4.8, 5.7, 6.7_
  - [ ] 12.3 Traduzir violação de `FOREIGN KEY` na exclusão de área, solo e cultura
    - Nos adaptadores existentes, `deleteById` chama `flush()` e traduz `23503` em `BusinessRuleException` (422) com mensagem que cita o tipo de referência. Não alterar os construtores dos serviços nem as portas
    - _Requisitos: 1.13, 2.9, 3.5_

- [ ] 13. Aplicação
  - [ ] 13.1 Criar DTOs de aplicação
    - `ManagementPlanInput/Output`, `DailyWeatherInput/Output` e `AppliedIrrigationInput/Output` em `application.dto`
    - _Requisitos: 4.1, 5.1, 6.1_
  - [ ] 13.2 Implementar `ManagementPlanService`
    - Criar (201), atualizar F, taxa e `endDate`, consultar por área (ativo e encerrados, início decrescente, lista vazia sem planos). 404 para área, cultura ou solo inexistentes; 400 acumulado com `ViolationCollector`; 422 para segundo plano ativo, sobreposição e `endDate` anterior ao último dia calculado (A-02); 422 ao trocar cultura ou solo com balanço existente
    - _Requisitos: 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 4.10, 4.11, 4.12_
  - [ ] 13.3 Implementar `DailyWeatherService` e `AppliedIrrigationService`
    - Upsert (200 com o registro persistido), consulta por período (ordem crescente, máximo 366 dias, data inicial não posterior à final), 404 para área inexistente em escrita e consulta, data não posterior à data atual em UTC com `Clock` injetável, violações acumuladas (A-05)
    - _Requisitos: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.9, 5.10, 5.11, 5.12, 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8, 6.9, 6.10, 6.11, 6.12_

- [ ] 14. Testes da Fase 3
  - [ ] 14.1 Criar repositórios em memória e fixtures de teste
    - `InMemoryManagementPlanRepository`, `InMemoryDailyWeatherRepository` e `InMemoryAppliedIrrigationRepository` no estilo de `InMemoryAreaRepository`, com `Clock` fixo. Estender `TestData`
    - _Requisitos: 4.1, 5.1, 6.1_
  - [ ] 14.2 Escrever testes unitários dos serviços e do domínio
    - Ordem 400, 404, 422; plano com recurso inexistente (404); segundo plano ativo; sobreposição; `endDate` (A-02); troca de cultura com balanço (422); F e taxa atualizados sem mudar balanços já gravados; IR zero aceita; omitir IR recusado sem assumir zero; data futura recusada; consulta por período vazia (200) e de área inexistente (404)
    - _Requisitos: 4.5, 4.6, 4.8, 4.9, 4.10, 5.3, 5.4, 5.5, 5.6, 5.10, 5.11, 6.2, 6.4, 6.5, 6.6, 6.11, 6.12_
  - [ ] 14.3 Escrever testes dos mapeadores de persistência
    - Plano, meteorologia e IR (ida e volta)
    - _Requisitos: 4.1, 5.1, 6.1_
  - [ ] 14.4 Escrever teste de propriedade para os limites dos value objects e dos cadastros
    - **Propriedade 11: Limites dos value objects e dos cadastros**
    - **Valida: Requisitos 1.2, 1.3, 1.9, 2.2, 2.3, 2.10, 3.2, 3.3, 4.3, 4.4, 5.2, 5.5, 6.2, 6.3, 7.4, 10.5**
    - Parametrizado por `RangeSpec`, com valores nas bordas. Nos serviços de cadastro, plano, meteorologia e IR, verifica violação com o nome do campo e nenhuma persistência
  - [ ] 14.5 Escrever teste de propriedade para um registro por área e data nas entradas diárias
    - **Propriedade 14: Um registro por área e data nas entradas diárias**
    - **Valida: Requisitos 5.1, 5.7, 6.1, 6.7**
  - [ ] 14.6 Escrever teste de propriedade para um plano ativo por área
    - **Propriedade 23: Um plano ativo por área**
    - **Valida: Requisitos 4.7, 4.8, 4.11**
  - [ ] 14.7 Escrever testes de integração da V5 e da persistência
    - Migração V4 para V5 com dados, CHECKs, índice único parcial e `UNIQUE`; round trip dos três agregados; duas gravações concorrentes de meteorologia e de IR para a mesma chave (uma linha, `CountDownLatch`); duas criações concorrentes de plano ativo (no máximo um 201); exclusão de área, solo e cultura referenciados por plano gera `BusinessRuleException`
    - _Requisitos: 4.7, 4.8, 5.7, 6.7, 1.13, 2.9, 3.5, 16.4_

- [ ] 15. Checkpoint da Fase 3: build, testes e relatórios
  - Rodar `clean test integrationTest jacocoTestCoverageVerification`, conferir contagens nos XML, ausência de falhas e de testes pulados, e cobertura de linha do JaCoCo (mínimo de 80%)
  - Informar o que foi feito, as decisões (inclusive A-02, A-03 e A-05) e as pendências. **Aguardar aprovação antes da Fase 4**

---

**Fase 4: Balanço diário imutável, cálculo e reprocessamento**

- [ ] 16. Migração V6
  - [ ] 16.1 Criar `V6__daily_water_balance.sql`
    - Tabela `daily_water_balance` sem `updated_at`, com identificação, snapshot das entradas (mesma escala da coluna de origem), resultados (ETc, CAD, AFDi, AFDf e I em `NUMERIC` sem escala e sem CHECK de sinal; `operating_time_hours NUMERIC(24,10)` com `>= 0`) e controle (`calculated_at`, metodologia, `revision >= 1`, `is_current`). FKs `RESTRICT`
    - Índice único parcial `uq_daily_water_balance_current ON (area_id, balance_date) WHERE is_current`, `UNIQUE (area_id, balance_date, revision)` e índice `(area_id, balance_date)`
    - Trigger `BEFORE DELETE` que sempre rejeita e `BEFORE UPDATE` que rejeita qualquer mudança, exceto a passagem de `is_current` de `true` para `false`, com `ERRCODE` de violação de restrição
    - _Requisitos: 12.1, 12.2, 12.3, 12.4, 12.7, 12.8, 12.9, 12.11, 16.3_

- [ ] 17. Domínio e portas
  - [ ] 17.1 Criar as exceções de domínio
    - `PreconditionsNotMetException` (data e lista de pré-condições com `code`, `message` e `fields`; códigos `WEATHER_MISSING`, `APPLIED_IRRIGATION_MISSING`, `SOIL_FIELDS_MISSING`, `CROP_FIELDS_MISSING`), `ConflictException` e `ImmutableRecordException`
    - _Requisitos: 11.1, 11.2, 11.3, 11.4, 11.5, 12.8, 12.10, 13.8_
  - [ ] 17.2 Criar o agregado imutável `DailyWaterBalance`
    - Identificação, `WaterBalanceInputs` como snapshot, `WaterBalanceResult`, `calculatedAt`, `methodologyId`, `methodologyVersion`, `revision` e `current`. Única mudança permitida: `markNotCurrent()`, que devolve uma cópia
    - _Requisitos: 12.1, 12.2, 12.3, 12.4, 12.7, 13.2_
  - [ ] 17.3 Criar as portas `DailyWaterBalanceRepository` e `AreaOperationLock`
    - `insert`, `insertAll`, `findCurrent`, `findCurrentFrom`, `findCurrentByPeriod` com paginação, `countCurrentByPeriod`, `findRevisions` (revisão decrescente), `markNotCurrent(areaId, fromDate)` em lote e `firstMissingDate`. Sem operação de atualização de valores nem de exclusão. `AreaOperationLock.tryLock(areaId)`
    - _Requisitos: 12.7, 13.2, 13.6, 14.1_

- [ ] 18. Infraestrutura
  - [ ] 18.1 Criar `DailyWaterBalanceEntity`, `DailyWaterBalanceJpaRepository` e o mapeador manual
    - Todas as colunas `updatable = false`, exceto `is_current`, que só muda por consulta em lote. ETc, CAD, AFDi, AFDf e I sem escala fixa; leitura sem perda de casas
    - _Requisitos: 12.1, 12.2, 12.3, 12.4, 12.11_
  - [ ] 18.2 Criar `DailyWaterBalanceRepositoryAdapter`
    - `firstMissingDate` com `generate_series` e `LEFT JOIN` nos balanços correntes com `LIMIT 1`. Violação do índice de balanço corrente vira `ConflictException`. Erro do trigger de imutabilidade vira `ImmutableRecordException`
    - _Requisitos: 7.8, 8.4, 12.8, 12.9, 12.10_
  - [ ] 18.3 Criar o `AreaOperationLock` baseado em `pg_try_advisory_xact_lock`
    - Chave de duas partes (inteiro fixo do módulo e hash do UUID da área), com escopo de transação
    - _Requisitos: 12.10, 13.8_

- [ ] 19. Aplicação
  - [ ] 19.1 Criar DTOs de saída do balanço
    - `WaterBalanceOutput`, `WaterBalanceInputsOutput` e demais records de `application.dto`, aplicando `PresentationRounding`. `idealIrrigationMm` e `appliedIrrigationMm` em campos distintos
    - _Requisitos: 9.5, 14.6, 15.7_
  - [ ] 19.2 Implementar `WaterBalanceService.calculate`
    - Ordem: formato (400, `ViolationCollector`), área (404), bloqueio (409), plano da data (A-03: 400 antes do primeiro plano, 422 sem plano), duplicidade (422), pré-condições de dados agregadas em uma resposta 422 (IR zero conta como presente), lacuna de histórico (422 com a primeira data sem cálculo), cálculo e gravação como revisão 1 corrente
    - `Clock` injetável para `calculated_at`. Valores gravados sem arredondamento. Nada parcial persistido em caso de falha
    - _Requisitos: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8, 7.9, 8.4, 10.4, 10.5, 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7, 11.8, 12.1, 12.2, 12.3, 12.4, 12.5, 15.8_
  - [ ] 19.3 Implementar `WaterBalanceService.reprocess` (tudo-ou-nada)
    - Formato (400), área (404), bloqueio (409), data inicial com revisão corrente (422). Cálculo da cadeia inteira em memória, com cadastros, meteorologia e IR atuais. Qualquer pré-condição violada aborta com 422 e a data violadora, sem gravar nada. Em sucesso, uma transação marca as correntes como não correntes e insere as revisões n+1. Dias nunca calculados não são calculados
    - _Requisitos: 13.1, 13.2, 13.3, 13.4, 13.5, 13.7, 13.8, 15.10_
  - [ ] 19.4 Implementar `WaterBalanceQueryService`
    - Consulta por período (correntes, paginada, `from` e `to` opcionais, página 0, tamanho 20, máximo 100, valores inválidos geram 400 sem ajuste), por data (404 sem cálculo) e histórico de revisões (ordem decrescente). 404 para área inexistente
    - _Requisitos: 13.6, 14.1, 14.2, 14.3, 14.4, 14.5, 14.6, 14.7, 14.8_

- [ ] 20. Testes da Fase 4
  - [ ] 20.1 Criar repositórios em memória do balanço, `AreaOperationLock` falso e falha injetável
    - `InMemoryDailyWaterBalanceRepository` que modela o índice único parcial, aceita falha injetada em ponto escolhido da gravação e desfaz tudo ao falhar (para a Propriedade 21)
    - _Requisitos: 12.5, 13.5_
  - [ ] 20.2 Escrever testes unitários de `WaterBalanceService` e `WaterBalanceQueryService`
    - Ordem 400, 404, 409, 422; cada pré-condição isolada e agregada; duplicidade; histórico ausente; A-03; Cenário A completo pelo serviço (7 dias, valores persistidos conferidos com `compareTo`); Cenário B; Cenário D; Cenário E (revisões antigas preservadas e não correntes); reprocesso com data não calculada; F alterado depois do cálculo não muda o balanço
    - _Requisitos: 7.1, 7.2, 7.5, 7.6, 7.7, 7.8, 8.4, 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7, 12.6, 13.1, 13.2, 13.4, 13.7_
  - [ ] 20.3 Escrever testes do mapeador de persistência do balanço
    - Valores com escala 13 em AFDi e AFDf, `compareTo` em vez de `equals`
    - _Requisitos: 12.11_
  - [ ] 20.4 Escrever teste de propriedade para o round trip de persistência do balanço
    - **Propriedade 10: Round trip de persistência do balanço**
    - **Valida: Requisitos 12.1, 12.2, 12.3, 12.4, 12.11**
  - [ ] 20.5 Escrever teste de propriedade para a coleta completa de violações
    - **Propriedade 13: Coleta completa de violações**
    - **Valida: Requisitos 4.5, 5.4, 5.5, 6.2, 6.6, 7.4, 1.9, 2.10**
    - Requisições de plano, meteorologia, IR e cálculo com subconjuntos arbitrários de campos inválidos ou ausentes
  - [ ] 20.6 Escrever teste de propriedade para a consulta por período
    - **Propriedade 15: Consulta por período**
    - **Valida: Requisitos 5.9, 5.10, 6.10, 13.6, 14.1, 14.4, 14.8**
  - [ ] 20.7 Escrever teste de propriedade para as pré-condições agregadas do cálculo
    - **Propriedade 16: Pré-condições agregadas do cálculo**
    - **Valida: Requisitos 1.12, 2.8, 7.7, 11.1, 11.2, 11.3, 11.4, 11.5**
  - [ ] 20.8 Escrever teste de propriedade para a lacuna no histórico
    - **Propriedade 17: Lacuna no histórico**
    - **Valida: Requisitos 7.8, 8.4**
  - [ ] 20.9 Escrever teste de propriedade para o cálculo repetido que não duplica o corrente
    - **Propriedade 18: Cálculo repetido não duplica o corrente**
    - **Valida: Requisitos 11.7, 12.9, 13.2**
  - [ ] 20.10 Escrever teste de propriedade para a imutabilidade do histórico frente a alterações de cadastro
    - **Propriedade 19: Imutabilidade do histórico frente a alterações de cadastro**
    - **Valida: Requisitos 4.9, 5.8, 6.9, 12.6, 12.7**
  - [ ] 20.11 Escrever teste de propriedade para o reprocessamento equivalente à cadeia recalculada
    - **Propriedade 20: Reprocessamento equivale à cadeia recalculada**
    - **Valida: Requisitos 13.1, 13.2, 13.3, 12.7**
    - Inclui o caso "sem mudança de dados reproduz os mesmos valores" (cenário de idempotência do `requirements.md`, seção 7.2)
  - [ ] 20.12 Escrever teste de propriedade para o reprocessamento tudo-ou-nada
    - **Propriedade 21: Reprocessamento tudo-ou-nada**
    - **Valida: Requisitos 13.4, 13.5, 7.2, 12.5**
    - Inclui falha injetada em qualquer ponto da gravação
  - [ ] 20.13 Escrever teste de propriedade para o histórico de revisões
    - **Propriedade 22: Histórico de revisões**
    - **Valida: Requisitos 13.6**
  - [ ] 20.14 Escrever testes de integração da V6 e da persistência do balanço
    - Migração V5 para V6, índices e CHECKs; trigger: `UPDATE` e `DELETE` diretos rejeitados, exceto `is_current` de `true` para `false`; round trip com escala 13 em AFDi e AFDf; `firstMissingDate`; `markNotCurrent` em lote; dois cálculos concorrentes da mesma área e data (um 201 e um 409 ou 422, uma linha corrente); reprocessamentos concorrentes (409); rollback completo quando a gravação falha no meio
    - _Requisitos: 12.5, 12.7, 12.8, 12.9, 12.10, 12.11, 13.5, 13.8, 16.4_

- [ ] 21. Checkpoint da Fase 4: build, testes e relatórios
  - Rodar `clean test integrationTest jacocoTestCoverageVerification`, conferir contagens nos XML, ausência de falhas e de testes pulados, e cobertura de linha do JaCoCo (mínimo de 80%)
  - Informar o que foi feito, as decisões (inclusive DD-01 a DD-07 e A-06) e as pendências. **Aguardar aprovação antes da Fase 5**

---

**Fase 5: Controladores, DTOs, erros e OpenAPI**

- [ ] 22. Apresentação
  - [ ] 22.1 Criar DTOs `*Request` e `*Response` e mapeadores MapStruct
    - Nomes de campo com unidade onde há ambiguidade (`sprinklerRateMmPerHour`, `etpMm`, `precipitationMm`, `appliedIrrigationMm`). `DailyWaterBalanceResponse` com os grupos `inputs`, `results` (com `operatingTime` em `hours`, `wholeHours` e `minutes`) e `control`. `unmappedTargetPolicy = ERROR`. Data no corpo para meteorologia e IR (A-05)
    - _Requisitos: 9.5, 10.3, 10.6, 14.2, 15.7_
  - [ ] 22.2 Criar os quatro controladores sob `/api/v1`
    - `ManagementPlanController`: `POST` (201), `GET` lista, `GET /{planId}`, `PUT /{planId}`
    - `DailyWeatherController` e `AppliedIrrigationController`: `POST` upsert (200), `GET ?from&to`
    - `WaterBalanceController`: `POST /calculate` (201), `POST /reprocess` (200), `GET ?from&to&page&size`, `GET /{date}`, `GET /{date}/revisions`
    - _Requisitos: 4.2, 4.11, 5.1, 5.9, 6.1, 6.10, 7.3, 13.6, 14.1, 14.2_
  - [ ] 22.3 Acrescentar handlers ao `GlobalExceptionHandler` e a anotação `ConflictResponse`
    - `PreconditionsNotMetException` (422 com extensões `date` e `preconditions[]`), `ConflictException` (409) e `ImmutableRecordException` (422), sem alterar os handlers existentes. O handler mais específico prevalece sobre `BusinessRuleException`. Documentar as respostas no OpenAPI
    - _Requisitos: 11.5, 12.8, 12.10, 13.8, 16.1_

- [ ] 23. Testes da Fase 5
  - [ ] 23.1 Escrever testes `MockMvc` de plano, meteorologia e Irrigação Real
    - Status 201, 200, 400 (`violations[]` com todos os campos), 404 e 422; datas no corpo; período inválido
    - _Requisitos: 4.2, 4.3, 4.5, 4.6, 4.8, 5.4, 5.5, 5.10, 5.11, 6.2, 6.5, 6.11_
  - [ ] 23.2 Escrever testes `MockMvc` do `WaterBalanceController`
    - 201, 200, 400, 404, 409 e 422; `preconditions[]` com uma entrada por falha; política de arredondamento idêntica em todos os endpoints (2 casas em mm, 4 em horas, minutos de 0 a 59); paginação inválida retorna 400 sem ajuste; `noRecommendationReason` e `irrigationRecommended` na resposta
    - _Requisitos: 7.3, 10.3, 11.5, 13.8, 14.1, 14.4, 14.5, 14.6, 14.8, 15.7_
  - [ ] 23.3 Escrever testes do `GlobalExceptionHandler` para os novos erros
    - Prevalência do handler específico, formato Problem_Details e preservação dos handlers antigos
    - _Requisitos: 11.5, 12.8, 12.10, 16.1_

- [ ] 24. Checkpoint da Fase 5: build, testes e relatórios
  - Rodar `clean test integrationTest jacocoTestCoverageVerification`, conferir contagens nos XML, ausência de falhas e de testes pulados, e cobertura de linha do JaCoCo (mínimo de 80%)
  - Informar o que foi feito, as decisões e as pendências. **Aguardar aprovação antes da Fase 6**

---

**Fase 6: Integração ponta a ponta, ArchUnit e cobertura**

- [ ] 25. Testes de integração ponta a ponta (`@Tag("integration")`, base `PostgresIntegrationTest`)
  - [ ] 25.1 Ciclo de vida HTTP do Cenário A e do Cenário B
    - Cadastro mínimo de solo (tipo, CC, PMP e D), cultura (nome, KC e Z) e área (nome); plano; meteorologia e IR dos 7 dias; cálculo dos 7 dias com conferência da tabela linha a linha (valores persistidos sem arredondamento, apresentação arredondada); consulta por período e por data; Cenário B com AFDf acima do CAD
    - _Requisitos: 7.1, 7.3, 8.1, 8.2, 8.3, 8.5, 9.2, 9.3, 9.4, 10.1, 10.3, 14.1, 14.2_
  - [ ] 25.2 Erros, duplicidade, imutabilidade e exclusões (Cenário D)
    - Sem meteorologia, sem IR, sem histórico, mesma data duas vezes (422 e uma linha corrente), solo sem CC gravimétrico (422 listando o campo), `precipitationMm` omitido (400), `sprinklerRateMmPerHour` igual a 0 (400 na criação do plano); alterar F, solo, cultura e meteorologia não muda linhas gravadas; exclusão de área, solo e cultura referenciados retorna 422 sem alterar os registros
    - _Requisitos: 1.13, 2.9, 3.5, 4.4, 4.9, 7.7, 8.4, 11.1, 11.2, 11.3, 11.4, 11.7, 12.6_
  - [ ] 25.3 Reprocessamento (Cenário E) e concorrência por HTTP
    - Reprocesso a partir do dia 2 com revisões antigas preservadas e não correntes, uma corrente por dia, `GET /{date}/revisions` em ordem decrescente; reprocesso com data não calculada (422); reprocesso com pré-condição violada em dia posterior sem gravar nada; 409 em requisição concorrente
    - _Requisitos: 13.1, 13.2, 13.3, 13.4, 13.5, 13.6, 13.7, 13.8_
  - [ ] 25.4 Compatibilidade HTTP dos endpoints existentes e dos dados antigos
    - Requisições válidas de clientes existentes de área, solo, cultura e recomendação recebem as mesmas respostas de sucesso; `POST /api/v1/recommendations/calculate` mantém o contrato; registros anteriores à V4 continuam legíveis e editáveis com os mesmos valores
    - _Requisitos: 1.10, 1.11, 2.6, 2.7, 3.4, 16.1, 16.2_

- [ ] 26. Arquitetura e cobertura
  - [ ] 26.1 Conferir o `ArchitectureTest` completo
    - Todas as regras existentes ativas e passando (sem remover, desabilitar ou enfraquecer), as regras novas de `domain.waterbalance` e controladores sob `/api/v1`. Verificar que nenhuma regra existente foi alterada
    - _Requisitos: 7.10, 15.1, 16.7_
  - [ ] 26.2 Conferir a cobertura por pacote e complementar testes unitários
    - Ler o relatório do JaCoCo, identificar classes novas com cobertura baixa e acrescentar testes unitários. O JaCoCo continua medindo só a tarefa `test`, com meta de 80% de linha em `check`. Relatório combinado com integração continua fora do escopo, salvo pedido do usuário
    - _Requisitos: 16.6_

- [ ] 27. Checkpoint da Fase 6: build, testes e relatórios
  - Rodar `clean test integrationTest jacocoTestCoverageVerification` com o Docker aberto. Conferir contagens nos XML de `build/test-results` (unitários e integração), ausência de falhas e de testes pulados, e o relatório do JaCoCo (cobertura de linha de pelo menos 80%)
  - Confirmar os critérios de aceite transversais 1 a 5 do `requirements.md` (seção 6)
  - Informar o que foi feito, as decisões e as pendências. **Aguardar aprovação antes da Fase 7**

---

**Fase 7: Documentação**

- [ ] 28. README e ADR-006
  - [ ] 28.1 Atualizar o `README.md` em pt-br
    - Seção do módulo de balanço hídrico: fluxo diário, endpoints, exemplos de requisição e resposta, política de arredondamento, tratamento de erros e a limitação do módulo (fórmulas do usuário, sem metodologia no `CalculationEngine`)
    - _Requisitos: 16.1, 16.2_
  - [ ] 28.2 Criar `docs/adr/ADR-006-balanco-hidrico-fora-do-motor-registrado.md` e atualizar `docs/adr/README.md`
    - Registrar o balanço hídrico fora do `CalculationEngine` (DD-03), as revisões imutáveis (DD-01) e o reprocessamento tudo-ou-nada (DD-02), com alternativas rejeitadas, no formato dos ADRs 001 a 005
    - _Requisitos: 12.7, 13.2, 13.5_
  - [ ] 28.3 Atualizar este `tasks.md` e registrar o fechamento
    - Marcar as fases concluídas com os números verificados (testes unitários, de integração e cobertura) e completar o "Registro da revisão A-01" e as decisões A-01 a A-08 efetivamente aplicadas
    - _Requisitos: 16.8_

- [ ] 29. Checkpoint final: verificação completa
  - Rodar `clean test integrationTest jacocoTestCoverageVerification` com o Docker aberto e conferir todos os relatórios. Se houver `docker-compose.yml` afetado pelas migrações, conferir que `docker compose up --build` aplica V1 a V6
  - Informar o resultado final e as pendências. **Aguardar a aprovação final do usuário**

## Notes

- Todos os testes (inclusive os baseados em propriedades) são tarefas obrigatórias, e não opcionais, porque o usuário os pediu e porque a meta de 80% de cobertura e a revisão A-01 dependem deles. Nenhum item está marcado com `*`.
- Cada teste de propriedade usa jqwik 1.9.0, no mínimo 100 iterações e o comentário `// Feature: irrigation-water-balance-module, Property N: <título>`. A Propriedade 4 vale para a sequência de chamadas do calculador. As Propriedades 14 a 23 usam repositórios em memória e `Clock` fixo. Concorrência real, trigger, migração e FKs ficam nos testes de integração.
- O design coloca os controladores na Fase 5, e a tabela do `requirements.md` cita "API" na Fase 3. Este plano segue o design, e as Fases 3 e 4 entregam domínio, persistência e serviços testados por unidade e integração de repositório.
- A revisão A-01 não pode cumprir ao mesmo tempo os Requisitos 16.8 e 1.6, 2.5 e 3.1. A alteração de asserções é restrita aos testes que codificam uma obrigatoriedade removida e depende da confirmação do usuário na revisão do design.
- Itens pendentes de decisão do usuário e valores adotados (A-02 a A-08, DP-03 a DP-12): a constante de TR (A-04) fica em um só lugar para mudar com facilidade. Nenhum item bloqueia as Fases 1 e 2.
- O JaCoCo continua medindo só a tarefa `test`. A tarefa `integrationTest` exige o Docker Desktop aberto.
- `V1`, `V2` e `V3` nunca são editadas. As migrações novas são `V4`, `V5` e `V6`, em ordem sequencial.

### Registro da revisão A-01

Preenchido na tarefa 4.1 (conferir no checkpoint da Fase 1). Cada alteração tem comentário "Revisão A-01" no teste. Nenhuma falha teve outra causa: não houve regressão em código de produção.

Antes das alterações: `test` com 18 falhas em 223; `integrationTest` com 1 falha em 50. Depois: `test` 223/0 falhas/0 ignorados; `integrationTest` 50/0 falhas/0 ignorados.

| Teste | Asserção anterior | Asserção nova | Requisito |
|---|---|---|---|
| `AreaServiceTest.createRejectsMissingRequiredFields` | Área vazia gerava violações em `name`, `areaHectares`, `systemFlowRate` e `irrigationSystemType` | Gera só `name` | 3.1 |
| `AreaControllerTest.createRejectsInvalidDataWithProblemDetailsListingEachField` | `irrigationSystemType` nulo era violação esperada | Removido da lista esperada (`name`, `areaHectares`, `latitude`, `systemFlowRate`) | 3.1 |
| `SoilProfileServiceTest.createRequiresEveryUnit` (renomeado `createAcceptsMissingUnits`) | Unidades nulas geravam 4 violações | Criação aceita e as 4 unidades voltam nulas | 1.6 |
| `SoilProfileControllerTest.createRejectsOutOfRangeValuesAndMissingUnits` (renomeado `createRejectsOutOfRangeValues`) | Esperava também as 4 violações de unidade | Espera só as violações de faixa | 1.6 |
| `AggregatesTest.SoilProfileRules.rejectsMissingUnit` (renomeado `acceptsMissingUnit`) | `NullPointerException` com unidade nula | Construção aceita, unidade nula | 1.6 |
| `DomainPropertiesTest.soilProfileRejectsAnyMissingUnit` (renomeado `soilProfileAcceptsAnyMissingUnit`) | Qualquer unidade nula lançava `NullPointerException` | Aceita e as demais unidades se mantêm | 1.6 |
| `CropServiceTest.createRejectsAnEmptyStageList` (renomeado `createAcceptsAnEmptyStageList`) | Lista vazia gerava violação em `stages` | Criação aceita com zero estágios | 2.5 |
| `AggregatesTest.CropRules.rejectsEmptyStageList` (renomeado `acceptsEmptyStageList`) | `IllegalArgumentException` com lista vazia | Construção aceita | 2.5 |
| `DomainPropertiesTest.cropAcceptsOneToTwentyStagesWithUniquePositiveOrder` (renomeado `cropAcceptsZeroToTwentyStagesWithUniquePositiveOrder`) | Esperado válido só com 1 a 20 estágios | Esperado válido com 0 a 20 | 2.5 |
| `CropControllerTest.createRejectsAMissingStageList` (renomeado `createRejectsAMissingKcAndRootDepth`) | Corpo só com nome gerava violação em `stages` | Gera violações em `kc` e `rootDepthMm` (estágios opcionais) | 2.5, 2.10 |
| Fixtures `TestData.cropInput()`, helper `crop(...)` de `CropServiceTest`, `VALID_BODY` de `CropControllerTest`, `CROP` e `CROP_UPDATED` de `ApiLifecycleIntegrationTest` | Cultura sem `kc` e `rootDepth` | Informam `kc` 1,0 e `rootDepth`/`rootDepthMm` 400. Afeta `CropServiceTest` (`createPersists...`, `updateReplacesTheStages`, `createReportsInvalidStageFields...`, `createRejectsDuplicatedStageOrder`, `createRejectsMoreThanTwentyStages`), `CropControllerTest` (`createReturns201WithTheStages`, `createSurfacesServiceViolations`, `updateReturns200Or404Or400`) e `ApiLifecycleIntegrationTest.cropLifecycleIncludingStageReplacement`, sem mudar asserções | 2.10 |

Observação: os construtores de compatibilidade de `CropInput` e `CropRequest` continuam existindo, mas nenhum teste de controlador os usa; nenhuma alteração foi necessária neles.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.1"] },
    { "id": 1, "tasks": ["1.2", "2.2", "2.3", "2.4"] },
    { "id": 2, "tasks": ["3.1", "3.2"] },
    { "id": 3, "tasks": ["3.3"] },
    { "id": 4, "tasks": ["3.4"] },
    { "id": 5, "tasks": ["4.1", "4.3"] },
    { "id": 6, "tasks": ["4.2", "4.4"] },
    { "id": 7, "tasks": ["6.1"] },
    { "id": 8, "tasks": ["7.1", "7.3"] },
    { "id": 9, "tasks": ["7.2", "7.4"] },
    { "id": 10, "tasks": ["8.1", "8.2", "8.3", "8.4", "8.5", "8.6", "8.7", "8.8", "8.9", "8.10", "8.11", "8.12"] },
    { "id": 11, "tasks": ["10.1", "11.1", "11.2"] },
    { "id": 12, "tasks": ["11.3"] },
    { "id": 13, "tasks": ["12.1"] },
    { "id": 14, "tasks": ["12.2", "12.3"] },
    { "id": 15, "tasks": ["13.1"] },
    { "id": 16, "tasks": ["13.2", "13.3"] },
    { "id": 17, "tasks": ["14.1"] },
    { "id": 18, "tasks": ["14.2", "14.3", "14.4", "14.5", "14.6", "14.7"] },
    { "id": 19, "tasks": ["16.1", "17.1"] },
    { "id": 20, "tasks": ["17.2"] },
    { "id": 21, "tasks": ["17.3"] },
    { "id": 22, "tasks": ["18.1"] },
    { "id": 23, "tasks": ["18.2", "18.3"] },
    { "id": 24, "tasks": ["19.1"] },
    { "id": 25, "tasks": ["19.2", "19.4"] },
    { "id": 26, "tasks": ["19.3"] },
    { "id": 27, "tasks": ["20.1"] },
    { "id": 28, "tasks": ["20.2", "20.3", "20.4", "20.5", "20.6", "20.7", "20.8", "20.9", "20.10", "20.11", "20.12", "20.13", "20.14"] },
    { "id": 29, "tasks": ["22.1"] },
    { "id": 30, "tasks": ["22.2", "22.3"] },
    { "id": 31, "tasks": ["23.1", "23.2", "23.3"] },
    { "id": 32, "tasks": ["25.1", "25.4", "26.1"] },
    { "id": 33, "tasks": ["25.2"] },
    { "id": 34, "tasks": ["25.3"] },
    { "id": 35, "tasks": ["26.2"] },
    { "id": 36, "tasks": ["28.1", "28.2", "28.3"] }
  ]
}
```
