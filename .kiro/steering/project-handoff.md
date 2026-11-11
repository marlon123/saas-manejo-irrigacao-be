---
inclusion: always
---

# Contexto do projeto (handoff entre sessões)

Este arquivo resume o estado do projeto ao fim da sessão de 11/11/2026. Leia antes de agir. Ele complementa a spec em `.kiro/specs/irrigation-management-backend/` (requirements.md, design.md e tasks.md, todos em pt-br). Em caso de divergência sobre o estado das fases, o `tasks.md` é a referência.

## Regras de trabalho combinadas com o usuário

- **Idioma:** toda documentação nova em pt-br. Identificadores de código ficam em inglês.
- **Ritmo:** trabalhar por fases do `tasks.md`. Ao fim de cada fase, compilar, rodar os testes, informar o que foi feito, as decisões e as pendências, e aguardar aprovação antes da fase seguinte.
- **Fórmulas:** não inventar fórmulas agronômicas (Item em Aberto 1). O motor de cálculo não tem nenhuma metodologia registrada em produção. A `FakeCalculationMethod` dos testes devolve um resultado fixo e não é uma metodologia.
- **Decisões relevantes:** apresentar alternativas e justificar antes de decidir.
- **Status das fases:** não dar uma fase como concluída sem ter verificado build e testes de fato. Conferir os números nos relatórios (`build/test-results`, JaCoCo), não só o `BUILD SUCCESSFUL`.

## Ambiente

- **Local do projeto:** `C:\dev\saas-manejo-irrigacao`. É a cópia válida.
- **Cópia antiga:** `C:\Users\UserTQI\OneDrive - T Q I Tecnologia e Qualidade Em Informatica Ltda\Documentos\Projetos\saas-manejo-irrigacao` está DESATUALIZADA. Não editar lá. O OneDrive travava a pasta `build\` e fazia leituras de arquivo voltarem com conteúdo antigo.
- **JDK 21:** `C:\Program Files\Java\jdk-21.0.13.0.3`. O `java` não está no PATH do terminal do agente; definir `JAVA_HOME` antes de chamar o Gradle.
- **Gradle:** não há Gradle instalado. Usar o wrapper `gradlew.bat` (Gradle 8.10.2).
- **Docker:** Docker Desktop instalado (Engine 29.9.3). O Docker Desktop precisa estar aberto para a tarefa `integrationTest`; confirmar com `docker version` (a linha do servidor deve aparecer).
- **Terminal:** funcionando desde 11/11/2026. A causa dos travamentos anteriores era a política de execução `Restricted` do PowerShell 5.1, que impedia a integração do terminal do Kiro: a ferramenta devolvia o controle antes do fim (`Exit Code: -1`) e os comandos seguintes entravam em fila. Foi aplicado `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` (reverter com `Undefined`). Agora os comandos esperam o fim e devolvem o código real.
- **Como rodar o build:**
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.13.0.3"; $env:GRADLE_EXIT_CONSOLE = "1"; .\gradlew.bat <tarefas> --no-daemon --console=plain
  ```
  O `GRADLE_EXIT_CONSOLE` evita que um build com falha pare no `Press any key to exit`. Verificação completa: `clean test integrationTest jacocoTestCoverageVerification` (cerca de 1 min 45 s).
- **Docker no terminal do agente:** a ferramenta bloqueia `docker run -d`. Para isso, usar o processo gerenciado (`control_pwsh_process`) e sempre remover o contêiner num `finally`. No PowerShell 5.1, chamar a API com `curl.exe --noproxy '*' --max-time N`, porque o `Invoke-RestMethod` pode travar.

## Stack

- **Base:** Java 21, Spring Boot 3.3.5, Gradle Kotlin DSL, PostgreSQL (16 nos testes; a versão do ambiente será definida na Fase 8), Flyway, Spring Data JPA/Hibernate.
- **Bibliotecas:** Bean Validation, MapStruct 1.6.3 (só na apresentação), Springdoc 2.6.0 e logstash-logback-encoder 8.0.
- **Testes:** JUnit 5, Mockito, jqwik 1.9.0, ArchUnit 1.3.0, Testcontainers 1.21.4 e JaCoCo 0.8.12.
- **Testcontainers:** precisa ser 1.21.4 ou superior. A 1.20.x usa a API 1.32 do Docker, recusada pelo Engine 29+ (mínimo 1.44). A versão é fixada também pela propriedade `testcontainers.version`, porque o BOM do Spring Boot gerencia o core transitivo.
- **Tarefas Gradle:** `test` exclui `@Tag("integration")` e não precisa de Docker. `integrationTest` roda só os testes com essa tag. O JaCoCo mede apenas a tarefa `test`, com meta de 80% de linha aplicada em `check`.

## Estado das fases (tasks.md)

- **Fases 1 a 8:** concluídas e marcadas no `tasks.md`. Todas as 8 fases do plano estão entregues.
- **Última verificação (11/11/2026, build com `clean`):** 219 testes unitários e 41 de integração, sem falhas nem testes pulados. Cobertura de linha de 88,7% (1.246 de 1.405 linhas), medida só com os unitários.
- **Testes de integração** (`@Tag("integration")`, base `support/PostgresIntegrationTest` com container singleton e `@ServiceConnection`):
  - `MigrationIntegrationTest`: V1 a V3 em ordem, schema, constraint deferrable, JSONB e falha de migração (uma `V99__broken_migration.sql` em `src/test/resources/db/failing-migration`).
  - `RepositoryRoundTripIntegrationTest`: round-trip dos quatro agregados, inclusive JSONB e troca de estágios de cultura.
  - `ApiLifecycleIntegrationTest`: ciclo de vida HTTP das três entidades, 422 ao excluir área com recomendação e cálculo (201, 404 e 422).
  - `RecommendationFilterIntegrationTest`: filtros por área, período e ambos.
  - `HealthIntegrationTest` e `DatabaseDownHealthIntegrationTest`: banco no ar e banco derrubado durante o teste.
  - `StructuredLoggingIntegrationTest`: logs JSON da aplicação completa.
  - `AuditTimestampsIntegrationTest`: defaults em UTC e trigger de `updated_at` (V3).
- **Fase 8 (Docker, README e ADRs):** `Dockerfile`, `.dockerignore`, `docker-compose.yml`, `.env.example`, `.gitattributes`, `README.md` e `docs/adr/` (ADR-001 a ADR-005). Verificada com `docker compose up --build`. Existe um `.env` local, criado na verificação com senha aleatória, fora do versionamento.

## Decisões tomadas

Todas já estão refletidas no `design.md` e no `tasks.md`.

1. **Migração V2:** adicionou as colunas de unidade em `soil_profiles` (Req 3.14) e tornou `uq_crop_stage_order` `DEFERRABLE INITIALLY DEFERRED`, porque o Hibernate insere os estágios novos antes de excluir os antigos.
2. **Migração V3:** `created_at`/`updated_at` de `areas`, `crops` e `soil_profiles` são mantidas só pelo banco. Defaults em `now() AT TIME ZONE 'UTC'` e trigger `BEFORE UPDATE` (`set_updated_at()`), que só atua quando a linha muda. As entidades JPA não mapeiam essas colunas e a API não as expõe. Limitação conhecida: alterar só os estágios de uma cultura não atualiza `crops.updated_at`.
3. **Mapeadores da persistência são manuais.** Os value objects são records validados e os agregados não têm setters. O MapStruct é usado só nos DTOs da apresentação, com `unmappedTargetPolicy = ERROR`. Cobertos por `PersistenceMappersTest` (unitário) e pelo round-trip de integração.
4. **JSONB tipado:** `CalculationInputJson` e `CalculationResultJson`, com `@JdbcTypeCode(SqlTypes.JSON)`.
5. **Paginação inválida retorna 400, sem ajuste automático** (Req 1.18 e 2.15).
6. **Rota de cálculo:** `POST /api/v1/recommendations/calculate`.
7. **DTOs:** um mesmo request serve para criar e atualizar (o PUT substitui o recurso). Interpretado como a separação entre request e response exigida pelo Req 6.2.
8. **Cálculo para área inexistente retorna 404.** Ordem das verificações: validação (400), área (404), parâmetros do motor (400), metodologia (422).
9. **Validação:** o `ViolationCollector` acumula todas as violações em uma `ValidationFailedException`, que vira 400 com `violations[]`. Os agregados expõem métodos estáticos `validateX` para cada erro sair no campo certo, por exemplo `stages[0].kc`.
10. **`GET /api/v1/health`** sempre responde 200, com `healthy`/`degraded` e `connected`/`disconnected`. Para sondas, `/actuator/health` responde 503 quando o banco cai.
11. **Logs e datas:** log em JSON por padrão e legível no perfil `dev`. O campo `exceptionType` traz o nome qualificado da classe. Datas em UTC, com um `Clock` injetável.
12. **Ordenação de áreas:** parâmetros `sortBy` (`name` ou `areaHectares`) e `direction` (`asc` ou `desc`).
13. **Credenciais sem valor padrão** (Req 11.6): só `SPRING_DATASOURCE_URL` tem padrão. Sem usuário e senha, a aplicação falha no Flyway com `password authentication failed for user "${SPRING_DATASOURCE_USERNAME}"`, e o compose recusa iniciar (`${VAR:?}`).
14. **Contêineres:** `bootJar` com nome fixo `irrigation-backend.jar` e JAR plain desligado. Runtime como usuário não-root `app`. Portas publicadas só em `127.0.0.1`, porque no Docker Desktop uma porta publicada em todas as interfaces abria um listener em `::1` que travava as chamadas a `localhost`. Healthcheck do banco com `pg_isready -h 127.0.0.1`.

## Pendências

- **Validação explícita das credenciais na inicialização:** é opcional (ver design.md, Containerização). Hoje a falha acontece, mas com uma mensagem indireta.
- **Pasta antiga do OneDrive:** o usuário decide se apaga. Os arquivos temporários dela já foram removidos.
- **Itens em aberto da spec:** os 11 itens em aberto do requirements.md continuam sem confirmação do usuário (unidades, fontes de ETo e precipitação etc.). Foram adotados os padrões propostos.
- **Cobertura de integração:** o JaCoCo não soma os testes de integração. Se for desejado, criar um relatório combinado informativo, fora da regra do `check`, para o build continuar funcionando sem Docker.
