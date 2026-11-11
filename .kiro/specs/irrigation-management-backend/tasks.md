# Implementation Plan: irrigation-management-backend

> **Idioma:** Português (pt-br) | **Workflow:** Requirements-first | **Spec type:** Feature

## Overview

As tarefas estão organizadas em 8 fases incrementais, da fundação do projeto até a containerização. Cada fase depende das anteriores e deve ser concluída com testes e build verificados antes de avançar.

- [x] Fase 1: Fundação do Projeto
- [x] Fase 2: Camada de Domínio
- [x] Fase 3: Camada de Infraestrutura (validada contra PostgreSQL real nos testes de integração da Fase 7)
- [x] Fase 4: Camada de Aplicação
- [x] Fase 5: Camada de Apresentação
- [x] Fase 6: Documentação e Observabilidade
- [x] Fase 7: Estratégia de Testes (verificado em 11/11/2026: 219 testes unitários e 41 de integração passando; cobertura de linha de 88,7% medida só com os unitários, acima da meta de 80%)
- [x] Fase 8: Containerização e Ambiente (verificado em 11/11/2026: imagem construída com os testes unitários no build; `docker compose up` sobe o PostgreSQL saudável antes do backend, aplica V1 a V3 e responde em `localhost:8080`; processo como usuário não-root; sem credenciais, o compose e a aplicação recusam iniciar)

---

## Tasks

**Fase 1: Fundação do Projeto**

- [x] 1. Configuração do Gradle com Kotlin DSL

  - [x] 1.1 Criar `build.gradle.kts` com Spring Boot 3.x (3.3+), Java 21
  - [x] 1.2 Configurar Gradle Wrapper (`gradlew`, `gradlew.bat`, `gradle-wrapper.properties`)
  - [x] 1.3 Configurar gerenciamento centralizado de versões das dependências
  - [x] 1.4 Organizar dependências por responsabilidade (web, jpa, validation, flyway, postgresql, mapstruct, springdoc, testes)
  - [x] 1.5 Configurar plugin do MapStruct para geração de código em tempo de compilação
  - [x] 1.6 Configurar plugin do JaCoCo com meta mínima de 80% de cobertura de linha (Req 10.6)
  - [x] 1.7 Habilitar cache e build incremental
  - [x] 1.8 Configurar tarefa `check` para executar testes antes da geração do artefato

  - Notas de implementação: Usar `io.spring.dependency-management` ou o BOM do Spring Boot. Incluir `net.jqwik:jqwik` no classpath de teste. Adicionar `org.mapstruct:mapstruct-processor` como annotation processor. Configurar `tasks.withType<Test>` para usar JUnit Platform.

  - Referências: Design — Stack Tecnológica; Req 10.6


- [x] 2. Estrutura de Pacotes e Configuração Base do Spring Boot

  - [x] 2.1 Criar classe principal `IrrigationApplication.java` com `@SpringBootApplication`
  - [x] 2.2 Criar estrutura de pacotes: `domain`, `application`, `infrastructure`, `presentation`
  - [x] 2.3 Criar subpacotes conforme design: `domain.model`, `domain.model.valueobjects`, `domain.model.enums`, `domain.calculation`, `domain.repository`, `domain.exception`, `application.service`, `application.dto`, `application.pagination`, `infrastructure.persistence`, `infrastructure.config`, `presentation.controller`, `presentation.dto`, `presentation.mapper`, `presentation.advice`
  - [x] 2.4 Criar `application.yml` com configurações base (datasource, jpa, flyway, server)
  - [x] 2.5 Criar `application-dev.yml` e `application-test.yml` para perfis separados
  - [x] 2.6 Configurar `spring.jpa.hibernate.ddl-auto=validate` (Flyway como única fonte de verdade do schema)
  - [x] 2.7 Configurar `spring.mvc.problemdetails.enabled=true` (suporte nativo RFC 7807)
  - [x] 2.8 Habilitar Spring Boot Actuator para health checks

  - Notas de implementação: A estrutura de pacotes deve refletir exatamente o diagrama de pacotes do design. Manter a camada de domínio sem nenhuma dependência de Spring ou JPA.

  - Referências: Design — Estrutura de Pacotes, Responsabilidades das Camadas; Req 4.1, 7.1


- [x] 3. Configuração do PostgreSQL e Flyway

  - [x] 3.1 Adicionar dependências: `postgresql`, `flyway-core`, `flyway-database-postgresql`
  - [x] 3.2 Configurar datasource no `application.yml` (URL, username, password via env vars)
  - [x] 3.3 Criar diretório `src/main/resources/db/migration`
  - [x] 3.4 Criar migração inicial `V1__init_schema.sql` com tabelas: `areas`, `crops`, `crop_stages`, `soil_profiles`, `recommendations`
  - [x] 3.5 Definir `areas`: id (UUID PK), name (VARCHAR 255 NOT NULL), area_ha (DECIMAL), latitude (DECIMAL nullable), longitude (DECIMAL nullable), general_characteristics (TEXT nullable), irrigation_system_type (VARCHAR), system_flow_rate (DECIMAL), irrigation_efficiency (DECIMAL)
  - [x] 3.6 Definir `crops`: id (UUID PK), name (VARCHAR 255 NOT NULL), variety (VARCHAR 255), cultivation_cycle_days (INT)
  - [x] 3.7 Definir `crop_stages`: id (UUID PK), crop_id (UUID FK), name (VARCHAR 100), kc (DECIMAL), stage_order (INT), UNIQUE(crop_id, stage_order)
  - [x] 3.8 Definir `soil_profiles`: id (UUID PK), soil_type (VARCHAR), field_capacity (DECIMAL), permanent_wilting_point (DECIMAL), bulk_density (DECIMAL), effective_root_depth (DECIMAL), available_water (DECIMAL)
  - [x] 3.9 Definir `recommendations`: id (UUID PK), area_id (UUID FK), date_time (TIMESTAMP), input_data (JSONB), results (JSONB), methodology_id (VARCHAR), methodology_version (VARCHAR)
  - [x] 3.10 Adicionar constraint CHECK: `field_capacity > permanent_wilting_point` em `soil_profiles`
  - [x] 3.11 Adicionar FK: `recommendations.area_id` → `areas(id)`
  - [x] 3.12 Garantir que versões de migração seguem convenção monotonicamente crescente (Req 8.4)
  - [x] 3.13 Criar `V2__*.sql`: adicionar as colunas de unidade em `soil_profiles` (Req 3.14) e tornar `uq_crop_stage_order` `DEFERRABLE INITIALLY DEFERRED`, porque o Hibernate insere os estágios novos antes de excluir os antigos
  - [x] 3.14 Criar `V3__*.sql`: `created_at`/`updated_at` de `areas`, `crops` e `soil_profiles` mantidas só pelo banco (defaults em `now() AT TIME ZONE 'UTC'` e trigger `BEFORE UPDATE` `set_updated_at()`, que só atua quando a linha muda). As entidades JPA não mapeiam essas colunas e a API não as expõe. Limitação conhecida: alterar só os estágios de uma cultura não atualiza `crops.updated_at`

  - Notas de implementação: Os IDs são UUIDs gerados pelo banco. As colunas `input_data` e `results` em `recommendations` são JSONB para armazenar snapshots imutáveis de `CalculationInput` e `CalculationResult` (Req 5.7). A constraint CHECK no banco complementa a validação de domínio (Req 3.11).

  - Referências: Design — Modelo de Dados, ER Diagram; Req 8.1–8.5, 3.11, 5.7


**Fase 2: Camada de Domínio**

- [x] 4. Value Objects de Domínio

  - [x] 4.1 Criar `AreaInHectares` (BigDecimal, validação: 0 < x ≤ 1.000.000,00) (Req 1.14)
  - [x] 4.2 Criar `FlowRate` (BigDecimal, validação: 0 < x ≤ 1.000.000,00, unidade m³/h) (Req 1.15)
  - [x] 4.3 Criar `GeographicLocation` (latitude -90..90, longitude -180..180) (Req 1.16, 1.17)
  - [x] 4.4 Criar `Kc` (BigDecimal, validação: 0 < x ≤ 2,0) (Req 2.13)
  - [x] 4.5 Criar `VolumetricWaterContent` (BigDecimal, validação: 0,00 a 1,00) (Req 3.11)
  - [x] 4.6 Criar `BulkDensity` (BigDecimal, validação: 0 < x ≤ 3,00 g/cm³) (Req 3.12)
  - [x] 4.7 Criar `RootDepth` (BigDecimal, validação: 0 < x ≤ 5.000,00 mm) (Req 3.13)
  - [x] 4.8 Criar `AvailableWater` (BigDecimal, unidade mm)
  - [x] 4.9 Criar `IrrigationEfficiency` (BigDecimal, validação: 0,01 a 1 como fração) (Req 4.8)
  - [x] 4.10 Criar `CalculationInput` com todos os parâmetros do Req 4.8 (ETo 0–15 mm/dia, Kc 0–2, ETc 0–15 mm/dia, precipitação efetiva 0–500 mm, umidade atual 0–100%, capacidade de armazenamento 0–500 mm, eficiência 0,01–1, lâmina líquida/bruta 0–500 mm, área irrigada 0,01–99999,99 ha, vazão 0,01–9999,99 m³/h, tempo de irrigação 0–999,99 h)
  - [x] 4.11 Criar `CalculationResult` (Irrigation_Depth 0–500 mm, Water_Volume 0–999999,99 m³, Irrigation_Time 0–999,99 h) (Req 4.7)
  - [x] 4.12 Cada value object deve lançar exceção de validação identificando o parâmetro ofensor quando o valor estiver fora do intervalo

  - Notas de implementação: Todos os value objects são Java puro, sem imports de Spring ou JPA. Usar records ou classes imutáveis. A construção valida os intervalos no construtor. Estas validações são a fonte autoritativa de regras de intervalo, complementando o Bean Validation dos DTOs. Ver Propriedades 1 e 2 do design.

  - Referências: Design — Value Objects, Propriedades 1–2; Req 1.13–1.17, 2.13, 3.11–3.13, 4.7, 4.8


- [x] 5. Enums de Domínio

  - [x] 5.1 Criar `IrrigationSystemType` com valores: `SPRINKLER`, `DRIP`, `MICRO_SPRINKLER`, `FURROW`, `CENTER_PIVOT`, `SURFACE`
  - [x] 5.2 Criar `MeasurementUnit` com unidades: porcentagem, g/cm³, mm, m³/h, hectares, mm/dia, horas, fração, adimensional

  - Notas de implementação: Item em Aberto 5 (tipo de sistema de irrigação como enum) e Item em Aberto 3 (unidades de parâmetros de solo). Estas escolhas são reversíveis.

  - Referências: Design — Open Items 5, 3; Req 1.12, 3.14


- [x] 6. Agregados de Domínio

  - [x] 6.1 Criar `Area` (aggregate root): id, nome, AreaInHectares, GeographicLocation opcional, características gerais, IrrigationSystemType, FlowRate, IrrigationEfficiency. Construção e update aplicam invariantes dos Req 1.12–1.16
  - [x] 6.2 Criar `Crop` (aggregate root): id, nome (≤ 255), variedade (≤ 255), ciclo de cultivo (dias), coleção de 1–20 CropStage. Validar contagem de estágios, intervalo de Kc e unicidade de ordem (Req 2.11, 2.12)
  - [x] 6.3 Criar `CropStage`: nome (≤ 100), Kc (> 0, ≤ 2,0), ordem (positiva, única dentro da cultura)
  - [x] 6.4 Criar `SoilProfile` (aggregate root): tipo de solo, VolumetricWaterContent capacidade de campo, VolumetricWaterContent ponto de murcha (CC > PMP), BulkDensity, RootDepth, AvailableWater, MeasurementUnit por parâmetro (Req 3.14)
  - [x] 6.5 Criar `Recommendation` (aggregate root): id, areaId, dateTime, CalculationInput, CalculationResult, MethodologyIdentifier, versão da metodologia. Imutável após criação (Req 5.7)

  - Notas de implementação: Agregados são POJOs simples com regras de negócio, sem anotações JPA. As validações de invariantes são aplicadas no construtor e em métodos de update, garantindo que a entidade nunca exista em estado inválido. Ver Propriedades 3, 4, 5, 9 do design.

  - Referências: Design — Agregados de Domínio, Propriedades 3–5, 9; Req 1.12–1.16, 2.11–2.14, 3.10–3.14, 5.1, 5.7


- [x] 7. Motor de Cálculo (Contratos e Registry)

  - [x] 7.1 Criar `MethodologyIdentifier` (value object para identificar metodologia)
  - [x] 7.2 Criar interface `CalculationMethod` com métodos: `methodologyId()`, `version()`, `calculate(CalculationInput)`
  - [x] 7.3 Criar interface `CalculationEngine` com métodos: `register(CalculationMethod)`, `calculate(MethodologyIdentifier, CalculationInput)`
  - [x] 7.4 Implementar `CalculationEngine` como registry com `Map<MethodologyIdentifier, CalculationMethod>`
  - [x] 7.5 `calculate` valida `CalculationInput` primeiro (Req 4.11) — lança `InvalidCalculationInputException` se parâmetros ausentes/fora do intervalo, e NÃO executa o cálculo
  - [x] 7.6 `calculate` verifica se a metodologia está registrada (Req 4.6) — lança `MethodologyNotAvailableException` se não estiver
  - [x] 7.7 `calculate` delega para `CalculationMethod.calculate(input)` (Req 4.5)
  - [x] 7.8 `register` lança `DuplicateMethodologyException` se o id já existir, preservando o registro existente (Req 4.10)
  - [x] 7.9 Novos registros não exigem modificação de implementações existentes (Req 4.9)

  - Notas de implementação: O motor de cálculo é Java puro, sem nenhum import de Spring ou JPA. O `Calculation_Engine` é um serviço de domínio puro. As implementações de `CalculationMethod` só se tornam beans Spring no boundary de wiring (Task 14). Nenhuma fórmula agronômica é implementada nesta fase (Item em Aberto 1 — fórmulas deferidas). Ver Propriedades 6, 7, 8 do design.

  - Referências: Design — Calculation_Engine, Propriedades 6–8; Req 4.1–4.11


- [x] 8. Exceções de Domínio e Portas de Repositório

  - [x] 8.1 Criar `DomainException` (base)
  - [x] 8.2 Criar `EntityNotFoundException` (→ mapeia para 404)
  - [x] 8.3 Criar `BusinessRuleException` (→ mapeia para 422)
  - [x] 8.4 Criar `MethodologyNotAvailableException` (→ mapeia para 422)
  - [x] 8.5 Criar `DuplicateMethodologyException` (→ mapeia para 422)
  - [x] 8.6 Criar `InvalidCalculationInputException` (→ mapeia para 400)
  - [x] 8.7 Criar interface `AreaRepository` (porta): save, findById, findAll(Pageable), delete, existsById, isReferencedByRecommendations
  - [x] 8.8 Criar interface `CropRepository` (porta): save, findById, findAll(Pageable), delete
  - [x] 8.9 Criar interface `SoilProfileRepository` (porta): save, findById, findAll(Pageable), delete
  - [x] 8.10 Criar interface `RecommendationRepository` (porta): save, findById, findByAreaAndDateRange(areaId, from, to, Pageable), findByArea(areaId, Pageable), findByDateRange(from, to, Pageable)

  - Notas de implementação: As portas de repositório são interfaces no domínio, sem dependências de Spring Data. Os métodos de query filtrada suportam os critérios de consulta do Req 5.2–5.4.

  - Referências: Design — Exceções de Domínio, Portas de Repositório; Req 5.2–5.4, 7.2, 7.3


**Fase 3: Camada de Infraestrutura**

- [x] 9. Entidades JPA e Repositórios Spring Data

  - [x] 9.1 Criar `AreaEntity` (@Entity) mapeando para tabela `areas`
  - [x] 9.2 Criar `CropEntity` (@Entity) mapeando para tabela `crops` com `@OneToMany` para `CropStageEntity`
  - [x] 9.3 Criar `CropStageEntity` (@Entity) mapeando para tabela `crop_stages` com `@ManyToOne` para `CropEntity`
  - [x] 9.4 Criar `SoilProfileEntity` (@Entity) mapeando para tabela `soil_profiles`
  - [x] 9.5 Criar `RecommendationEntity` (@Entity) mapeando para tabela `recommendations`, com `input_data` e `results` como colunas JSONB (usar `@JdbcTypeCode(SqlTypes.JSON)` ou conversor customizado)
  - [x] 9.6 Criar `AreaJpaRepository` (Spring Data JPA) implementando `AreaRepository`
  - [x] 9.7 Criar `CropJpaRepository` (Spring Data JPA) implementando `CropRepository`
  - [x] 9.8 Criar `SoilProfileJpaRepository` (Spring Data JPA) implementando `SoilProfileRepository`
  - [x] 9.9 Criar `RecommendationJpaRepository` (Spring Data JPA) implementando `RecommendationRepository` com queries filtradas por área e/ou intervalo de datas
  - [x] 9.10 Implementar método `isReferencedByRecommendations(areaId)` para verificação de integridade referencial (Req 1.19)

  - Notas de implementação: As entidades JPA são separadas dos modelos de domínio (separação two-model). O mapeamento entre entidades JPA e modelos de domínio é feito por mappers manuais na Task 10. Para JSONB, usar `SqlTypes.JSON` do Hibernate 6 ou um `AttributeConverter` customizado.

  - Referências: Design — Separação Two-Model, Entidades JPA; Req 1.19, 5.2–5.4, 8.3


- [x] 10. Mappers Manuais de Persistência

  - [x] 10.1 Criar `AreaPersistenceMapper` (@Component): `AreaEntity` ↔ `Area`
  - [x] 10.2 Criar `CropPersistenceMapper` (@Component): `CropEntity` ↔ `Crop` (incluindo `CropStageEntity` ↔ `CropStage`)
  - [x] 10.3 Criar `SoilProfilePersistenceMapper` (@Component): `SoilProfileEntity` ↔ `SoilProfile`
  - [x] 10.4 Criar `RecommendationPersistenceMapper` (@Component): `RecommendationEntity` ↔ `Recommendation` (incluindo a conversão para os records JSONB `CalculationInputJson` e `CalculationResultJson`)

  - Notas de implementação: Divergência da versão original desta task, que previa MapStruct. Os value objects de domínio são records validados e os agregados não têm setters, então o mapeamento é manual: cada value object é reconstruído pelo construtor, aplicando as invariantes. O JSONB usa `@JdbcTypeCode(SqlTypes.JSON)` com records tipados, sem serialização manual com Jackson. O MapStruct continua em uso apenas nos mappers de apresentação (Task 16). Cobertura: `PersistenceMappersTest` (unitário) e `RepositoryRoundTripIntegrationTest` (PostgreSQL real).

  - Referências: Design — Mappers; Req 5.7


- [x] 11. Configuração JPA e do Motor de Cálculo

  - [x] 11.1 Criar `JpaConfig` (@Configuration): configurar JPA, datasource, transações
  - [x] 11.2 Criar `CalculationEngineConfig` (@Configuration): instanciar `CalculationEngine` e registrar todos os beans `CalculationMethod` (atualmente nenhum, pois fórmulas são deferidas)
  - [x] 11.3 O `CalculationEngineConfig` é o único ponto onde o motor de domínio puro encontra o Spring

  - Notas de implementação: Quando fórmulas forem adicionadas futuramente, basta criar novos beans `CalculationMethod` que serão automaticamente registrados pelo `CalculationEngineConfig`. Nenhuma alteração no motor de cálculo é necessária. Ver Decisão Arquitetural 2 e 5 do design.

  - Referências: Design — Decisões Arquiteturais 2, 5; Req 4.3, 4.9


**Fase 4: Camada de Aplicação**

- [x] 12. DTOs de Caso de Uso e Paginação

  - [x] 12.1 Criar `PagedResult<T>` com campos: page, size, totalElements, totalPages, content
  - [x] 12.2 Criar DTOs de caso de uso para Area (CreateAreaInput, UpdateAreaInput, AreaOutput)
  - [x] 12.3 Criar DTOs de caso de uso para Crop (CreateCropInput, UpdateCropInput, CropOutput) incluindo CropStageInput e CropStageOutput
  - [x] 12.4 Criar DTOs de caso de uso para SoilProfile (CreateSoilProfileInput, UpdateSoilProfileInput, SoilProfileOutput)
  - [x] 12.5 Criar DTOs de caso de uso para Recommendation (CalculateRecommendationInput, RecommendationOutput)

  - Notas de implementação: DTOs de caso de uso são a fronteira entre a aplicação e a apresentação. Usar Java records onde a imutabilidade ajudar.

  - Referências: Design — DTOs de Caso de Uso; Req 6.2


- [x] 13. Serviços de Aplicação

  - [x] 13.1 Criar `AreaService`: create, update, delete, getById, list (paginado + ordenável)
    - Validar nome (não nulo, ≤ 255), área (> 0, ≤ 1.000.000), vazão (> 0, ≤ 1.000.000), coordenadas (lat -90..90, lon -180..180)
    - Verificar integridade referencial antes de deletar (se referenciada por recomendações → BusinessRuleException → 422) (Req 1.19)
    - Paginação: página padrão 0, tamanho padrão 20, máximo 100; ordenável por name e areaHectares (Req 1.3–1.5)
    - Rejeitar parâmetros de paginação/ordenação inválidos com 400 (Req 1.18)
  - [x] 13.2 Criar `CropService`: create, update, delete, getById, list (paginado)
    - Validar nome (não nulo, ≤ 255), estágios (1–20), Kc (> 0, ≤ 2,0), ordem única positiva (Req 2.11–2.14)
    - Paginação: página 0, tamanho 20, intervalo 1–100 (Req 2.3–2.4)
  - [x] 13.3 Criar `SoilProfileService`: create, update, delete, getById, list (paginado)
    - Validar CC > PMP (0–1), densidade (> 0, ≤ 3,00 g/cm³), profundidade (> 0, ≤ 5.000 mm) (Req 3.11–3.13)
    - Paginação: tamanho 20, máximo 100 (Req 3.3)
  - [x] 13.4 Criar `RecommendationService`: calculate, getById, list (filtrado por área e/ou datas, paginado)
    - `calculate`: invoca `CalculationEngine`, armazena `Recommendation` com snapshot imutável (Req 5.1, 5.7)
    - `list`: filtrar por areaId, dateFrom/dateTo, ou ambos; máximo 100 por página (Req 5.2–5.4)
    - Retornar página vazia com 200 quando não há resultados (Req 5.8)
    - Recomendações armazenadas nunca são modificadas (Req 5.7)

  - Notas de implementação: Os serviços de aplicação orquestram objetos de domínio através das portas de repositório. A validação autoritativa de intervalos ocorre nos value objects de domínio, mas os serviços também validam presença e regras de negócio. Os serviços dependem apenas das interfaces de repositório (portas), não das implementações JPA.

  - Referências: Design — Camada de Aplicação; Req 1.1–1.19, 2.1–2.16, 3.1–3.15, 5.1–5.8


**Fase 5: Camada de Apresentação**

- [x] 14. DTOs de Transporte (Request/Response)

  - [x] 14.1 Criar DTOs de requisição com anotações Bean Validation:
    - `AreaRequest`: @NotBlank name (≤ 255), @Positive @DecimalMax areaHectares (≤ 1.000.000), latitude (@DecimalMin -90, @DecimalMax 90), longitude (@DecimalMin -180, @DecimalMax 180), irrigationSystemType, @Positive @DecimalMax systemFlowRate (≤ 1.000.000), irrigationEfficiency
    - `CropRequest`: @NotBlank name (≤ 255), variety (≤ 255), cultivationCycleDays, List<CropStageRequest> @Size(min=1, max=20) stages
    - `CropStageRequest`: name (≤ 100), @Positive @DecimalMax kc (≤ 2.0), @Positive stageOrder
    - `SoilProfileRequest`: soilType, @DecimalMin fieldCapacity, @DecimalMin permanentWiltingPoint, @Positive @DecimalMax bulkDensity (≤ 3.00), @Positive @DecimalMax effectiveRootDepth (≤ 5000), availableWater
    - `CalculateRecommendationRequest`: areaId, methodologyId, inputs
  - [x] 14.2 Criar DTOs de resposta:
    - `AreaResponse`, `CropResponse`, `CropStageResponse`, `SoilProfileResponse`, `RecommendationResponse`
    - `PagedResponse<T>`: page, size, totalElements, totalPages, content (Req 6.3)
  - [x] 14.3 Implementar como Java records onde a imutabilidade ajudar

  - Notas de implementação: Bean Validation nos DTOs de requisição é uma primeira linha de defesa (portão rápido). A validação autoritativa de intervalos vive nos value objects de domínio. Ambas as camadas mapeiam para o mesmo formato 400 `violations` no handler de exceções. Ver Propriedades 14, 15 do design.

  - Referências: Design — DTOs, Propriedades 14–15; Req 1.2, 2.2, 3.2, 6.2, 6.3, 6.8, 7.1


- [x] 15. Controllers REST

  - [x] 15.1 Criar `AreaController` (@RestController, @RequestMapping("/api/v1/areas"))
    - POST → 201, GET (paginado + ordenável) → 200, GET/{id} → 200/404, PUT/{id} → 200/404/400, DELETE/{id} → 204/404/422
  - [x] 15.2 Criar `CropController` (@RestController, @RequestMapping("/api/v1/crops"))
    - POST → 201, GET (paginado) → 200, GET/{id} → 200/404, PUT/{id} → 200/404/400, DELETE/{id} → 204/404
  - [x] 15.3 Criar `SoilProfileController` (@RestController, @RequestMapping("/api/v1/soil-profiles"))
    - POST → 201, GET (paginado) → 200, GET/{id} → 200/404, PUT/{id} → 200/404/400, DELETE/{id} → 204/404
  - [x] 15.4 Criar `RecommendationController` (@RestController, @RequestMapping("/api/v1/recommendations"))
    - POST /calculate → 201/400/422, GET (filtrado por areaId, dateFrom, dateTo, paginado) → 200, GET/{id} → 200/404
  - [x] 15.5 Criar `HealthController` (@RestController, @RequestMapping("/api/v1/health"))
    - GET → status "healthy"/"unhealthy", DB "connected"/"disconnected", "degraded" se DB inacessível

  - Notas de implementação: Todos os endpoints sob `/api/v1` (Req 6.1). O endpoint `POST /api/v1/recommendations/calculate` aceita `{ areaId, methodologyId, inputs }`, invoca o `Calculation_Engine`, e em caso de sucesso persiste e retorna a `Recommendation` com 201. Como nenhuma metodologia concreta está registrada nesta fase, uma requisição recebe corretamente `MethodologyNotAvailableException` (→ 422) (Req 4.6).

  - Referências: Design — Controllers REST, Tabela de Endpoints; Req 1.1–1.19, 2.1–2.16, 3.1–3.15, 5.1–5.8, 6.1–6.10, 9.1–9.3


- [x] 16. Mappers MapStruct de Apresentção

  - [x] 16.1 Criar `AreaDtoMapper` (@Mapper): DTO de requisição → DTO de caso de uso, DTO de caso de uso → DTO de resposta
  - [x] 16.2 Criar `CropDtoMapper` (@Mapper): incluindo mapeamento de CropStage
  - [x] 16.3 Criar `SoilProfileDtoMapper` (@Mapper)
  - [x] 16.4 Criar `RecommendationDtoMapper` (@Mapper)
  - [x] 16.5 Configurar todos com `componentModel = "spring"`

  - Notas de implementação: Os mappers de apresentção convertem entre DTOs de transporte e DTOs de caso de uso/modelos de domínio. Usar `@Mapper(componentModel = "spring")` para beans Spring em tempo de compilação.

  - Referências: Design — Mappers MapStruct de Apresentação; Req 6.2


- [x] 17. Tratamento Global de Exceções

  - [x] 17.1 Criar `GlobalExceptionHandler` (@RestControllerAdvice)
  - [x] 17.2 Mapear `MethodArgumentNotValidException` / `ConstraintViolationException` → 400 com Problem_Details incluindo `violations[]` (field + message) (Req 7.1)
  - [x] 17.3 Mapear `InvalidCalculationInputException` → 400 com Problem_Details incluindo `violations[]` (parameter + message) (Req 4.11)
  - [x] 17.4 Mapear `EntityNotFoundException` → 404 com Problem_Details (title, status, detail) (Req 7.2)
  - [x] 17.5 Mapear `MethodologyNotAvailableException` → 422 com Problem_Details (Req 7.3)
  - [x] 17.6 Mapear `DuplicateMethodologyException` → 422 com Problem_Details (Req 7.3)
  - [x] 17.7 Mapear `BusinessRuleException` → 422 com Problem_Details (Req 7.3)
  - [x] 17.8 Mapear `Exception` (catch-all) → 500 com Problem_Details genérico, SEM stack trace ou detalhes internos (Req 7.4)
  - [x] 17.9 Adicionar extensão `violations` ao `ProblemDetail.properties` como array de `{ "field": ..., "message": ... }`
  - [x] 17.10 Logar todas as exceções com tipo, mensagem e stack trace via logging estruturado (Req 7.5)

  - Notas de implementação: Usar o suporte nativo `ProblemDetail` do Spring Boot 3 (habilitado em `application.yml`). A extensão `violations` é adicionada via `ProblemDetail.setProperty("violations", list)`. O handler catch-all loga a exceção completa mas retorna apenas um detalhe genérico. Ver Propriedades 14, 15, 16 do design e a Tabela de Mapeamento Exceção → Resposta.

  - Referências: Design — Tratamento de Erros, Propriedades 14–16; Req 7.1–7.5, 1.2, 2.2, 3.2, 4.6, 4.11, 6.8, 6.9, 6.10


**Fase 6: Documentação e Observabilidade**

- [x] 18. Documentação OpenAPI

  - [x] 18.1 Adicionar dependência `springdoc-openapi-starter-webmvc-ui`
  - [x] 18.2 Configurar Springdoc para escanear apenas controllers sob `/api/v1`
  - [x] 18.3 Adicionar anotações `@Operation`, `@ApiResponse`, `@Schema` nos controllers e DTOs
  - [x] 18.4 Documentar todos os endpoints com método HTTP, caminho, resumo e esquemas de requisição/resposta (Req 12.1, 12.2)
  - [x] 18.5 Garantir que o endpoint de documentação seja acessível via HTTP GET no formato OpenAPI (Req 12.3)
  - [x] 18.6 Configurar título, descrição, versão e servidores na documentação

  - Notas de implementação: O Springdoc gera automaticamente a especificação OpenAPI a partir das anotações dos controllers e DTOs. As anotações `@Operation` e `@Schema` enriquecem a documentação. O endpoint padrão do Springdoc (`/swagger-ui.html` e `/v3/api-docs`) deve estar acessível.

  - Referências: Design — Documentação OpenAPI; Req 12.1–12.3


- [x] 19. Logging Estruturado e Health Checks

  - [x] 19.1 Configurar logging estruturado em JSON (Logback JSON encoder) (Req 9.4)
  - [x] 19.2 Cada entrada de log deve capturar: timestamp, severity (ERROR/WARN/INFO), message
  - [x] 19.3 Entradas de erro devem adicionalmente capturar: exceptionType, exceptionMessage, stackTrace (Req 7.5)
  - [x] 19.4 Logar eventos informacionais (create/update/delete, cálculos) em INFO
  - [x] 19.5 Logar falhas de validação/regra de negócio em WARN
  - [x] 19.6 Logar erros inesperados em ERROR
  - [x] 19.7 Garantir que logs nunca incluam credenciais ou segredos
  - [x] 19.8 Configurar Spring Boot Actuator health endpoint (`/actuator/health`)
  - [x] 19.9 Configurar `HealthIndicator` customizado para conectividade com banco de dados (Req 9.2, 9.3)
  - [x] 19.10 O `HealthController` deve expor `/api/v1/health` com o formato acordado ("healthy"/"unhealthy", "connected"/"disconnected", "degraded") (Req 9.1–9.3)

  - Notas de implementação: Usar `logback-spring.xml` com encoder JSON (ex.: `logstash-logback-encoder` ou o logging estruturado nativo do Spring Boot 3). O Actuator já provê `/actuator/health` com DB health indicator; o `HealthController` adicionalmente expõe o formato acordado sob `/api/v1/health`.

  - Referências: Design — Health & Observabilidade, Logging Estruturado; Req 7.5, 9.1–9.4


**Fase 7: Estratégia de Testes**

- [x] 20. Testes Unitários de Domínio (JUnit 5 + Mockito)

  - [x] 20.1 Testes para `AreaInHectares`: valor válido aceito, valores fora do intervalo rejeitados (≤ 0, > 1.000.000)
  - [x] 20.2 Testes para `FlowRate`: valor válido aceito, valores fora do intervalo rejeitados
  - [x] 20.3 Testes para `GeographicLocation`: lat -90..90, lon -180..180, coordenadas inválidas rejeitadas
  - [x] 20.4 Testes para `Kc`: 0 < x ≤ 2,0, valores inválidos rejeitados
  - [x] 20.5 Testes para `VolumetricWaterContent`: 0..1, valores fora rejeitados
  - [x] 20.6 Testes para `BulkDensity`: 0 < x ≤ 3,00, valores fora rejeitados
  - [x] 20.7 Testes para `RootDepth`: 0 < x ≤ 5.000, valores fora rejeitados
  - [x] 20.8 Testes para `Area`: construção válida, nome em branco rejeitado, área inválida rejeitada, coordenadas inválidas rejeitadas
  - [x] 20.9 Testes para `Crop`: 1–20 estágios aceitos, 0 ou 21 rejeitados, ordem duplicada rejeitada, ordem não positiva rejeitada
  - [x] 20.10 Testes para `SoilProfile`: CC > PMP aceito, CC ≤ PMP rejeitado, densidade/profundidade inválidas rejeitadas, unidade ausente rejeitada
  - [x] 20.11 Testes para `CalculationEngine`: registro válido, metodologia duplicada rejeitada, metodologia não registrada rejeitada, entradas inválidas rejeitadas sem executar cálculo, delegação para metodologia registrada
  - [x] 20.12 Cada teste deve cobrir pelo menos um caso válido e um inválido (Req 10.1)

  - Notas de implementação: Os testes de domínio são Java puro, sem Spring. Usar JUnit 5 com asserções nativas ou AssertJ. Ver Propriedades 1–8 do design.

  - Referências: Design — Propriedades 1–8; Req 10.1


- [x] 21. Testes Baseados em Propriedades (jqwik)

  - [x] 21.1 Adicionar dependência `net.jqwik:jqwik` no escopo de teste
  - [x] 21.2 Propriedade 1: Value objects numéricos aceitam valores no intervalo e rejeitam fora (≥ 100 iterações)
  - [x] 21.3 Propriedade 2: Value objects de texto aceitam strings não-nulo dentro do limite e rejeitam em branco/acima do limite
  - [x] 21.4 Propriedade 3: Crop aceita 1–20 estágios com ordem única positiva, rejeita caso contrário
  - [x] 21.5 Propriedade 4: Capacidade de campo > ponto de murcha em SoilProfile
  - [x] 21.6 Propriedade 5: Todo parâmetro de solo carrega MeasurementUnit não-nulo
  - [x] 21.7 Propriedade 6: Motor de cálculo rejeita entradas inválidas e não invoca o método
  - [x] 21.8 Propriedade 7: Motor delega para metodologias registradas e rejeita ids não registrados
  - [x] 21.9 Propriedade 8: Registro nunca altera registros existentes
  - [x] 21.10 Propriedade 9: Recommendation é snapshot completo e imutável
  - [x] 21.11 Propriedade 10: Filtros de recomendação retornam apenas correspondentes, página vazia quando nenhum
  - [x] 21.12 Propriedade 11: Área referenciada por recomendação não pode ser excluída
  - [x] 21.13 Propriedade 12: Metadados de paginação consistentes (totalPages = ceil(totalElements/size))
  - [x] 21.14 Propriedade 13: Ordenação de áreas restrita a campos válidos
  - [x] 21.15 Propriedade 14: Erros de validação retornam Problem Details com violações de campo
  - [x] 21.16 Propriedade 15: Erros 404 e 422 retornam Problem Details com status correto
  - [x] 21.17 Propriedade 16: Erros 500 nunca vazam detalhes internos
  - [x] 21.18 Cada propriedade deve executar mínimo de 100 iterações e ser tagueada: `// Feature: irrigation-management-backend, Property N: <descrição>`

  - Notas de implementação: Usar `@Property` + `@ForAll` do jqwik. Criar geradores customizados para BigDecimal com intervalos, enums, e strings com tamanho limitado. Usar fakes leves de `CalculationMethod` para testes do motor. Usar repositório em memória ou mocks para testes de recomendação. Ver Propriedades 1–16 do design.

  - Referências: Design — Propriedades 1–16, Testes Baseados em Propriedades; Req 10.1, 10.7


- [x] 22. Testes de Serviços de Aplicação (JUnit 5 + Mockito)

  - [x] 22.1 Testes para `AreaService`: sucesso e erro em create/update/delete/getById/list
    - Verificar rejeição de exclusão quando área é referenciada por recomendações (→ 422)
    - Verificar paginação com defaults e limites
  - [x] 22.2 Testes para `CropService`: sucesso e erro em create/update/delete/getById/list
  - [x] 22.3 Testes para `SoilProfileService`: sucesso e erro em create/update/delete/getById/list
  - [x] 22.4 Testes para `RecommendationService`: sucesso e erro em calculate/getById/list
    - Verificar filtros por área, por datas, por ambos
    - Verificar retorno de página vazia quando não há resultados
  - [x] 22.5 Mockar portas de repositório em todos os testes (Req 10.2)

  - Notas de implementação: Usar `@ExtendWith(MockitoExtension.class)` e `@Mock` para as portas de repositório. Cada método deve ter pelo menos um caminho de sucesso e um de erro (Req 10.2).

  - Referências: Design — Testes Unitários; Req 10.2


- [x] 23. Testes de Controllers (@WebMvcTest + MockMvc)

  - [x] 23.1 Testes para `AreaController`: POST → 201, GET list → 200 com paginação, GET/{id} → 200/404, PUT/{id} → 200/404/400, DELETE/{id} → 204/404/422
  - [x] 23.2 Testes para `CropController`: POST → 201, GET list → 200, GET/{id} → 200/404, PUT/{id} → 200/404/400, DELETE/{id} → 204/404
  - [x] 23.3 Testes para `SoilProfileController`: POST → 201, GET list → 200, GET/{id} → 200/404, PUT/{id} → 200/404/400, DELETE/{id} → 204/404
  - [x] 23.4 Testes para `RecommendationController`: POST /calculate → 201/400/422, GET list (com filtros) → 200, GET/{id} → 200/404
  - [x] 23.5 Testes para `HealthController`: GET → 200 com status healthy/unhealthy
  - [x] 23.6 Testes de validação: exercitar DTOs válidos e inválidos, verificando status 400 e formato Problem Details (Req 10.5)
  - [x] 23.7 Asserir códigos de status, formato Problem Details (title, status, detail, violations) e metadados de paginação

  - Notas de implementação: Usar `@WebMvcTest` com `MockMvc`. Mockar serviços de aplicação com `@MockBean`. Verificar content type `application/problem+json` nas respostas de erro. Ver Propriedades 12–16 do design.

  - Referências: Design — Testes de Controller; Req 10.3, 10.5


- [x] 24. Testes de Integração (Testcontainers + PostgreSQL)

  - [x] 24.1 Adicionar dependências: `testcontainers`, `testcontainers-postgresql`, `org.springframework.boot:spring-boot-testcontainers`
  - [x] 24.2 Criar classe base de teste de integração com `@SpringBootTest`, `@ServiceConnection` e `PostgreSQLContainer`
  - [x] 24.3 Testes de round-trip de repositórios: salvar e recuperar Area, Crop, SoilProfile, Recommendation
  - [x] 24.4 Teste de integridade referencial: excluir área referenciada por recomendação → 422 (Req 1.19)
  - [x] 24.5 Testes de consulta filtrada de recomendações por área, por datas, por ambos (Req 5.2–5.4)
  - [x] 24.6 Testes do ciclo de vida completo: create → list → get → update → delete para cada entidade
  - [x] 24.7 Teste de migrações Flyway: tabelas criadas em ordem crescente de versão (Req 8.1/8.3)
  - [x] 24.8 Teste de migração falha: migração com erro interrompe startup e reporta versão falha (Req 8.5)
  - [x] 24.9 Teste de health: `connected` com BD no ar, `degraded` quando BD inacessível (Req 9.3)
  - [x] 24.10 Teste de logs JSON estruturados: capturar e verificar entradas de log (Req 7.5, 9.4)
  - [x] 24.11 Taguear testes de integração para permitir pular quando Docker não estiver disponível
  - [x] 24.12 Teste de timestamps de auditoria: defaults em UTC e trigger de `updated_at` da V3 (`AuditTimestampsIntegrationTest`)

  - Notas de implementação: Usar `@ServiceConnection` (Spring Boot 3.1+) para auto-configurar o PostgreSQL do Testcontainers. O container é iniciado e destruído por teste/classe. Os testes de migração podem usar um container separado para isolar. Ver Design — Testes de Integração.

  - Referências: Design — Testes de Integração; Req 1.19, 5.2–5.4, 7.5, 8.1–8.5, 9.3, 9.4, 10.4


- [x] 25. Testes Smoke / Estruturais

  - [x] 25.1 Adicionar dependência ArchUnit (`com.tngtech.archunit:archunit-junit5`)
  - [x] 25.2 Teste de independência arquitetural: `domain` (especialmente `domain.calculation`) não tem dependências de `presentation`, `infrastructure`, Spring ou JPA (Req 4.1)
  - [x] 25.3 Teste de versionamento da API: todos os controllers mapeiam sob `/api/v1` (Req 6.1)
  - [x] 25.4 Teste de OpenAPI: GET no endpoint de docs e verificar presença de todos os endpoints e esquemas (Req 12)
  - [x] 25.5 Teste de versionamento de migrações: versões são monotonicamente crescentes (Req 8.4)
  - [x] 25.6 Configurar JaCoCo com meta mínima de 80% de cobertura de linha, enforceada no build (Req 10.6)

  - Notas de implementação: ArchUnit verifica a independência da camada de domínio programaticamente. O teste de OpenAPI pode usar `MockMvc` para GET em `/v3/api-docs` e verificar o JSON retornado. JaCoCo é configurado no `build.gradle.kts` com `jacocoTestCoverageVerification`.

  - Referências: Design — Testes Smoke/Estruturais; Req 4.1, 6.1, 8.4, 10.6, 12


**Fase 8: Containerização e Ambiente**

- [x] 26. Dockerfile Multi-stage

  - [x] 26.1 Criar `Dockerfile` multi-stage:
    - Estágio 1 (build): JDK 21 (`eclipse-temurin:21-jdk`), copiar código, executar `./gradlew build`
    - Estágio 2 (runtime): JRE 21 slim (`eclipse-temurin:21-jre`), copiar fat JAR, executar como usuário não-root
  - [x] 26.2 Garantir que a imagem seja construída sem erros e inicie sem falhas (Req 11.1)
  - [x] 26.3 Expor porta 8080

  - Notas de implementação: O multi-stage build mantém a imagem final pequena (apenas JRE + JAR). Usar `USER` para executar como não-root por segurança.

  - Referências: Design — Containerização; Req 11.1


- [x] 27. Docker Compose e Configuração de Ambiente

  - [x] 27.1 Criar `docker-compose.yml` com dois serviços:
    - `postgresql`: imagem `postgres:16`, volume para dados, healthcheck
    - `irrigation-backend`: build do Dockerfile, `depends_on` postgresql com `condition: service_healthy`
  - [x] 27.2 Garantir que o backend inicia apenas quando o PostgreSQL aceita conexões (Req 11.2, 11.3)
  - [x] 27.3 Criar `.env.example` com variáveis de ambiente (SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD)
  - [x] 27.4 Adicionar `.env` ao `.gitignore`
  - [x] 27.5 Configurar `application.yml` para ler credenciais de variáveis de ambiente (Req 11.4, 11.5)
  - [x] 27.6 Garantir que nenhum valor de credencial seja commitado no repositório (Req 11.6)

  - Notas de implementação: O `depends_on` com `condition: service_healthy` garante que o backend não tente conectar antes do PostgreSQL estar pronto. As credenciais são carregadas de variáveis de ambiente em tempo de execução, nunca hard-coded.

  - Referências: Design — Containerização e Configuração; Req 11.1–11.6


- [x] 28. README e Documentação Final

  - [x] 28.1 Criar `README.md` com instruções de execução local:
    - Pré-requisitos (Java 21, Docker, Gradle)
    - Como executar com Docker Compose
    - Como executar localmente sem Docker
    - Principais comandos Gradle (`./gradlew build`, `./gradlew test`, `./gradlew check`, `./gradlew jacocoTestReport`)
    - Endpoints da API e documentação Swagger
    - Estrutura do projeto
  - [x] 28.2 Criar diretório `docs/adr/` para ADRs (Architecture Decision Records)
  - [x] 28.3 Criar ADR-001: Separação Two-Model (Domínio vs Persistência)
  - [x] 28.4 Criar ADR-002: Strategy + Registry para Motor de Cálculo
  - [x] 28.5 Criar ADR-003: Monólito Modular vs Microserviços
  - [x] 28.6 Criar ADR-004: Problem Details RFC 7807 para Tratamento de Erros
  - [x] 28.7 Criar ADR-005: Testes Baseados em Propriedades com jqwik

  - Notas de implementação: O README deve ser o ponto de entrada para novos desenvolvedores. Os ADRs documentam as decisões arquiteturais principais do design, fornecendo contexto histórico e justificativa para cada escolha.

  - Referências: Req 8 (Documentação e Organização), Design — Decisões Arquiteturais


**Fase 9: Pendências Opcionais (pós-entrega)**

Melhorias opcionais registradas ao fim da Fase 8. Não bloqueiam a entrega das fases 1 a 8. Nenhuma delas implementa fórmulas agronômicas: o motor de cálculo continua sem metodologia registrada em produção (Item em Aberto 1).

- [ ] 29. Validação explícita das credenciais na inicialização
  - [ ]* 29.1 Implementar a validação das variáveis `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD` na inicialização
    - Criar um `EnvironmentPostProcessor` (ou validador equivalente) em `infrastructure.config` que interrompa a inicialização com mensagem direta quando usuário ou senha estiverem ausentes, vazios ou com o placeholder literal `${...}`
    - Não registrar o valor das credenciais em logs ou mensagens de erro (Req 11.5, 11.6)
    - Não afetar os testes de integração, em que o `@ServiceConnection` fornece a conexão sem definir essas propriedades
    - _Requisitos: 11.4, 11.5, 11.6_
  - [ ]* 29.2 Escrever testes unitários da validação de credenciais
    - Cobrir credenciais ausentes, vazias, com placeholder não resolvido e válidas
    - Verificar que a mensagem de erro não contém o valor da senha
    - _Requisitos: 11.5, 11.6_

- [ ] 30. Relatório combinado de cobertura (informativo)
  - [ ]* 30.1 Configurar relatório JaCoCo combinado dos testes unitários e de integração
    - Em `build.gradle.kts`, criar uma tarefa de relatório (por exemplo, `jacocoCombinedReport`) que some os dados de execução de `test` e `integrationTest`
    - Manter a regra de 80% de `jacocoTestCoverageVerification` medida só com a tarefa `test` e ligada ao `check`; o relatório combinado fica fora do `check`, para o build continuar funcionando sem Docker
    - _Requisitos: 10.4, 10.6_

## Notes

- Todos os itens das fases 1 a 8 estão marcados como concluídos, conforme a verificação de 11/11/2026 registrada no topo do arquivo.
- Tasks marcadas com `*` são opcionais e podem ser puladas sem afetar a entrega.
- Divergências da versão original, já refletidas no design: mappers de persistência manuais (Task 10), migrações V2 e V3 (Task 3), rota `POST /api/v1/recommendations/calculate` (Task 15) e containerização com portas em `127.0.0.1` e credenciais sem valor padrão (Tasks 26 e 27).
- Os 11 itens em aberto do requirements.md continuam sem confirmação do usuário; foram adotados os padrões propostos.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["29.1", "30.1"] },
    { "id": 1, "tasks": ["29.2"] }
  ]
}
```
