# irrigation-management-backend

Backend de manejo de irrigação: cadastro de áreas de cultivo, culturas (com estágios e Kc), perfis de solo e recomendações de irrigação com histórico. API REST versionada em `/api/v1`, com erros no padrão Problem Details (RFC 7807) e documentação OpenAPI.

> **Motor de cálculo sem metodologia registrada.** As fórmulas agronômicas ainda não foram definidas (Item em Aberto 1 da spec). Por isso, `POST /api/v1/recommendations/calculate` responde **422** depois de validar a requisição. Ver [ADR-002](docs/adr/ADR-002-strategy-registry-motor-de-calculo.md).

## Stack

Java 21, Spring Boot 3.3.5, Gradle 8.10.2 (Kotlin DSL, via wrapper), PostgreSQL 16, Flyway, Spring Data JPA/Hibernate, Bean Validation, MapStruct, Springdoc OpenAPI e logs JSON (logstash-logback-encoder). Testes com JUnit 5, Mockito, jqwik, ArchUnit, Testcontainers e JaCoCo.

## Pré-requisitos

| Para | Precisa de |
|---|---|
| Rodar com Docker Compose | Docker (Docker Desktop no Windows) com Compose v2 |
| Rodar localmente e desenvolver | JDK 21 e um PostgreSQL acessível (pode ser o do compose) |
| Testes de integração | JDK 21 e Docker em execução |

Não é preciso instalar o Gradle: use o wrapper (`./gradlew` no Linux e macOS, `gradlew.bat` no Windows).

## Configuração

Toda a configuração de ambiente vem de variáveis de ambiente. O repositório não contém credenciais, e **a aplicação não inicia sem `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD`**: não há valores padrão para elas.

Se essas variáveis faltarem, o Spring Boot repassa o texto literal ao driver, e a inicialização falha no Flyway com `password authentication failed for user "${SPRING_DATASOURCE_USERNAME}"`. O nome da variável na mensagem indica que ela não foi definida; não se trata de uma senha errada.

| Variável | Obrigatória | Padrão | Uso |
|---|---|---|---|
| `SPRING_DATASOURCE_USERNAME` | sim | — | Usuário do banco (o compose também o usa para criar o usuário no PostgreSQL) |
| `SPRING_DATASOURCE_PASSWORD` | sim | — | Senha do banco |
| `SPRING_DATASOURCE_URL` | não | `jdbc:postgresql://localhost:5432/irrigation_db` | URL JDBC. No compose, é definida para o host interno `postgresql` |
| `POSTGRES_DB` | não | `irrigation_db` | Nome do banco criado pelo compose |
| `POSTGRES_PORT` / `APP_PORT` | não | `5432` / `8080` | Portas publicadas no host pelo compose |
| `SPRING_PROFILES_ACTIVE` | não | — | `dev` ativa logs legíveis e o SQL do Hibernate. Sem perfil, os logs saem em JSON |
| `JAVA_OPTS` | não | `-XX:MaxRAMPercentage=75.0` | Opções da JVM no contêiner |

Para começar, copie o modelo e troque a senha:

```bash
cp .env.example .env            # Linux/macOS
Copy-Item .env.example .env     # Windows (PowerShell)
```

O `.env` está no `.gitignore` e no `.dockerignore`.

## Executar com Docker Compose

```bash
docker compose up --build -d
```

- O backend só inicia depois que o PostgreSQL passa no healthcheck, ou seja, quando aceita conexões TCP. Na inicialização, o Flyway aplica as migrações.
- As portas são publicadas apenas em `127.0.0.1`, para que banco e API não fiquem acessíveis pela rede local.
- O build da imagem roda `gradlew build`, que inclui os testes unitários e a meta de cobertura. Os testes de integração ficam fora do build da imagem.
- Sem `SPRING_DATASOURCE_USERNAME` ou `SPRING_DATASOURCE_PASSWORD` no `.env`, o compose recusa iniciar e diz qual variável falta.

Para verificar:

```bash
curl http://localhost:8080/api/v1/health
# {"status":"healthy","application":"healthy","database":"connected"}
```

Para parar, use `docker compose down`. Para também apagar os dados do banco, acrescente `-v`, que remove o volume `postgres-data`.

## Executar localmente (sem Docker para a aplicação)

É preciso um PostgreSQL acessível. A opção mais simples é subir só o banco do compose:

```bash
docker compose up -d postgresql
```

Depois, defina as variáveis e rode a aplicação. No PowerShell:

```powershell
$env:SPRING_DATASOURCE_USERNAME = "irrigation"
$env:SPRING_DATASOURCE_PASSWORD = "<a senha do seu .env>"
$env:SPRING_PROFILES_ACTIVE = "dev"
.\gradlew.bat bootRun
```

No Linux ou macOS:

```bash
export SPRING_DATASOURCE_USERNAME=irrigation SPRING_DATASOURCE_PASSWORD='<a senha do seu .env>' SPRING_PROFILES_ACTIVE=dev
./gradlew bootRun
```

O Spring Boot não lê o `.env` sozinho: as variáveis precisam estar no ambiente do processo.

## Comandos Gradle

| Comando | O que faz | Docker? |
|---|---|---|
| `./gradlew test` | Testes unitários, de propriedade (jqwik), de controller e de arquitetura | não |
| `./gradlew integrationTest` | Testes de integração (`@Tag("integration")`) com PostgreSQL via Testcontainers | sim |
| `./gradlew jacocoTestReport` | Relatório de cobertura em `build/reports/jacoco/test/html/index.html` | não |
| `./gradlew check` | `test` mais a verificação da meta de 80% de cobertura de linha | não |
| `./gradlew build` | `check` mais o JAR executável `build/libs/irrigation-backend.jar` | não |
| `./gradlew bootRun` | Executa a aplicação | não, mas precisa de um banco |

A cobertura é medida apenas com a tarefa `test`, para que `check` e `build` funcionem sem Docker. No Windows, use `gradlew.bat` no lugar de `./gradlew`. Se um build com falha parar em "Press any key to exit", defina `GRADLE_EXIT_CONSOLE=1`.

## API

Documentação interativa: <http://localhost:8080/swagger-ui.html>. Especificação OpenAPI em JSON: <http://localhost:8080/v3/api-docs>.

| Método | Rota | Descrição |
|---|---|---|
| POST / GET | `/api/v1/areas` | Criar / listar áreas (paginação e `sortBy=name\|areaHectares`, `direction=asc\|desc`) |
| GET / PUT / DELETE | `/api/v1/areas/{id}` | Obter / substituir / excluir (422 se houver recomendações) |
| POST / GET | `/api/v1/crops` | Criar / listar culturas com estágios (`kc` e `rootDepthMm` obrigatórios; ver [Módulo de balanço hídrico](#módulo-de-balanço-hídrico)) |
| GET / PUT / DELETE | `/api/v1/crops/{id}` | Obter / substituir / excluir |
| POST / GET | `/api/v1/soil-profiles` | Criar / listar perfis de solo |
| GET / PUT / DELETE | `/api/v1/soil-profiles/{id}` | Obter / substituir / excluir |
| POST | `/api/v1/recommendations/calculate` | Calcular e armazenar uma recomendação (hoje, 422: sem metodologia) |
| GET | `/api/v1/recommendations` | Listar, com filtros `areaId`, `dateFrom` e `dateTo` (ISO, por exemplo `2025-01-01T00:00:00`) |
| GET | `/api/v1/recommendations/{id}` | Obter uma recomendação |
| GET | `/api/v1/health` | Sempre 200, com `healthy`/`degraded` e o estado do banco |
| GET | `/actuator/health` | Para sondas de orquestração: 503 quando o banco está inacessível |

- **Paginação:** `page` começa em 0, `size` vai de 1 a 100 (padrão 20). Valores fora disso retornam 400.
- **Erros:** respostas em `application/problem+json`. Erros de validação trazem `violations[]` com `field` e `message`.
- **Datas:** em UTC.

## Módulo de balanço hídrico

Calcula, por área e por dia, o balanço hídrico (ETc, CAD, AFD inicial e final, irrigação ideal I e tempo de funcionamento do aspersor) e guarda cada cálculo como uma revisão imutável. As fórmulas foram fornecidas pelo usuário e ficam no `WaterBalanceCalculator`, fora do `CalculationEngine`.

> **Limitação do módulo.** O `CalculationEngine` continua **sem nenhuma metodologia registrada**, e `POST /api/v1/recommendations/calculate` segue respondendo 422. O balanço hídrico não passa por esse motor. A `FakeCalculationMethod` usada nos testes devolve um resultado fixo e **não é uma metodologia**. Nenhuma fórmula agronômica além das fornecidas pelo usuário foi adicionada.

### Fluxo diário

1. **Cadastros mínimos.**
   - **Solo** (`/soil-profiles`): `soilType`, `bulkDensity` (D, g/cm³) e o par gravimétrico `fieldCapacityGravimetricPct` (CC) e `permanentWiltingPointGravimetricPct` (PMP), em %. O par é "ambos ou nenhum", e o PMP deve ser menor que a CC.
   - **Cultura** (`/crops`): `name`, `kc` e `rootDepthMm` (Z, em mm). Estágios são opcionais (0 a 20).
   - **Área** (`/areas`): apenas `name`.
2. **Plano de manejo** (`POST /areas/{areaId}/management-plans`): liga cultura e solo à área e define `fFactor` (F, adimensional, maior que 0 e até 1) e `sprinklerRateMmPerHour`. Cada área tem no máximo um plano ativo, e os períodos não se sobrepõem.
3. **Entradas do dia:** meteorologia (`etpMm`, `precipitationMm`) e irrigação real IR (`appliedIrrigationMm`). Ambas são gravadas por upsert (mesma área e data substitui o registro).
4. **Cálculo** (`POST .../water-balance/calculate`): informa `date` e `cycleDay` (TR). Os dias devem ser calculados em ordem.
5. **Reprocessamento** (`POST .../water-balance/reprocess`): depois de corrigir meteorologia, IR ou cadastros, recalcula a cadeia de dias a partir de uma data e grava novas revisões.

> **Mudança de contrato nas culturas.** `kc` e `rootDepthMm` passaram a ser **obrigatórios** em `POST` e `PUT /api/v1/crops`. Clientes antigos que criavam cultura sem esses campos agora recebem 400, com `violations[]` indicando os campos. Culturas já existentes continuam legíveis, e a resposta devolve `kc` e `rootDepthMm` nulos quando não foram preenchidos. Para usá-las em um plano, é preciso atualizá-las com `PUT`; do contrário o cálculo retorna 422 com `CROP_FIELDS_MISSING`.

**Primeiro dia.** O primeiro dia é a data de início do **primeiro plano da área** (o mais antigo). Nele, AFDi = CAD × F e não há recomendação de irrigar (`noRecommendationReason = FIRST_DAY_OF_FIRST_CYCLE`), mesmo que I seja positivo, e o tempo é zero. Nos demais dias, AFDi é a AFDf corrente do dia anterior, sem arredondamento, inclusive na troca de ciclo ou de plano. A AFDf pode ficar acima do CAD ou abaixo de zero; o valor não é truncado. Datas anteriores ao início do primeiro plano retornam 400.

### Endpoints

Todas as rotas ficam sob `/api/v1/areas/{areaId}`. Datas no formato `yyyy-MM-dd`.

| Método | Rota | Resposta | Descrição |
|---|---|---|---|
| POST | `/management-plans` | 201 | Cria um plano de manejo |
| GET | `/management-plans` | 200 | Lista os planos (início decrescente) |
| GET | `/management-plans/{planId}` | 200 | Obtém um plano (404 se for de outra área) |
| PUT | `/management-plans/{planId}` | 200 | Altera `fFactor`, `sprinklerRateMmPerHour` e `endDate`. Cultura e solo só mudam se o plano não tiver balanços |
| POST | `/daily-weather` | 200 | Registra a meteorologia do dia (upsert) |
| GET | `/daily-weather?from=&to=` | 200 | Lista um período inclusivo de até 366 dias |
| POST | `/applied-irrigation` | 200 | Registra a irrigação real do dia (upsert) |
| GET | `/applied-irrigation?from=&to=` | 200 | Lista um período inclusivo de até 366 dias |
| POST | `/water-balance/calculate` | 201 | Calcula e grava o balanço de um dia (cabeçalho `Location` para o dia) |
| POST | `/water-balance/reprocess` | 200 | Reprocessa a partir de uma data e devolve as novas revisões |
| GET | `/water-balance?from=&to=&page=&size=` | 200 | Lista os balanços correntes, paginados (`page` a partir de 0, `size` de 1 a 100, padrão 20) |
| GET | `/water-balance/{date}` | 200 | Obtém o balanço corrente de uma data |
| GET | `/water-balance/{date}/revisions` | 200 | Lista todas as revisões da data, da mais recente para a mais antiga |

### Exemplo: Cenário A (dias 1 e 2)

Parâmetros: CC = 30 %, PMP = 15 %, D = 1,2 g/cm³, Z = 400 mm, Kc = 0,8, F = 0,50 e taxa do aspersor de 6 mm/h. Então CAD = 0,01 × (30 − 15) × 1,2 × 400 = 72 mm e AFDi do dia 1 = 36 mm. Os exemplos abaixo usam `$AREA`, `$CROP`, `$SOIL` e `$PLAN` para os ids devolvidos pelas criações.

```bash
# 1. Cadastros mínimos
curl -X POST http://localhost:8080/api/v1/soil-profiles -H 'Content-Type: application/json' -d '{
  "soilType": "Argiloso",
  "fieldCapacityGravimetricPct": 30,
  "permanentWiltingPointGravimetricPct": 15,
  "bulkDensity": 1.2 }'

curl -X POST http://localhost:8080/api/v1/crops -H 'Content-Type: application/json' -d '{
  "name": "Milho", "kc": 0.8, "rootDepthMm": 400 }'

curl -X POST http://localhost:8080/api/v1/areas -H 'Content-Type: application/json' -d '{
  "name": "Talhão 1" }'

# 2. Plano de manejo (início em 2026-04-01)
curl -X POST http://localhost:8080/api/v1/areas/$AREA/management-plans -H 'Content-Type: application/json' -d '{
  "cropId": "'$CROP'", "soilProfileId": "'$SOIL'", "startDate": "2026-04-01",
  "fFactor": 0.50, "sprinklerRateMmPerHour": 6 }'

# 3. Entradas dos dias 1 e 2
curl -X POST http://localhost:8080/api/v1/areas/$AREA/daily-weather -H 'Content-Type: application/json' \
  -d '{ "date": "2026-04-01", "etpMm": 5.00, "precipitationMm": 0 }'
curl -X POST http://localhost:8080/api/v1/areas/$AREA/applied-irrigation -H 'Content-Type: application/json' \
  -d '{ "date": "2026-04-01", "appliedIrrigationMm": 0 }'
curl -X POST http://localhost:8080/api/v1/areas/$AREA/daily-weather -H 'Content-Type: application/json' \
  -d '{ "date": "2026-04-02", "etpMm": 6.25, "precipitationMm": 0 }'
curl -X POST http://localhost:8080/api/v1/areas/$AREA/applied-irrigation -H 'Content-Type: application/json' \
  -d '{ "date": "2026-04-02", "appliedIrrigationMm": 0 }'

# 4. Cálculo do dia 1 e, depois, do dia 2
curl -X POST http://localhost:8080/api/v1/areas/$AREA/water-balance/calculate -H 'Content-Type: application/json' \
  -d '{ "date": "2026-04-01", "cycleDay": 1 }'
```

Resposta do dia 1 (`201`). O primeiro dia do plano tem I = 4,00 mm, mas não há recomendação:

```json
{
  "id": "…", "areaId": "…", "date": "2026-04-01", "cycleDay": 1,
  "managementPlanId": "…", "cropId": "…", "soilProfileId": "…",
  "inputs": {
    "etpMm": 5.00, "precipitationMm": 0.00, "appliedIrrigationMm": 0.00,
    "fieldCapacityPct": 30.00, "wiltingPointPct": 15.00, "bulkDensityGCm3": 1.20,
    "rootDepthMm": 400.00, "kc": 0.80, "fFactor": 0.50, "sprinklerRateMmPerHour": 6.00
  },
  "results": {
    "etcMm": 4.00, "cadMm": 72.00, "afdInitialMm": 36.00, "afdFinalMm": 32.00,
    "idealIrrigationMm": 4.00, "irrigationRecommended": false,
    "noRecommendationReason": "FIRST_DAY_OF_FIRST_CYCLE",
    "operatingTime": { "hours": 0.0000, "wholeHours": 0, "minutes": 0 }
  },
  "control": {
    "calculatedAt": "2026-04-01T12:00:00Z", "methodologyId": "WATER_BALANCE_AFD", "methodologyVersion": "1.0",
    "revision": 1, "current": true
  }
}
```

O mesmo `POST .../calculate` com `{ "date": "2026-04-02", "cycleDay": 2 }` devolve `results` com ETc = 5,00 mm, AFDi = 32,00 mm, AFDf = 27,00 mm, I = 5,00 mm, `irrigationRecommended: true`, `noRecommendationReason: null` e `operatingTime` de `{ "hours": 0.8333, "wholeHours": 0, "minutes": 50 }` (5 ÷ 6 h). Os dias 3 a 7 do Cenário A estão no `requirements.md` do módulo (`.kiro/specs/irrigation-water-balance-module/`).

Consultas:

```bash
curl "http://localhost:8080/api/v1/areas/$AREA/water-balance?from=2026-04-01&to=2026-04-07&page=0&size=20"
# {"content":[ ... ],"page":0,"size":20,"totalElements":2,"totalPages":1}
curl http://localhost:8080/api/v1/areas/$AREA/water-balance/2026-04-02
curl http://localhost:8080/api/v1/areas/$AREA/water-balance/2026-04-02/revisions
```

**Reprocessamento (Cenário E).** Com os 7 dias calculados, corrija a ETP do dia 2 de 6,25 para 7,50 e reprocesse a partir dele:

```bash
curl -X POST http://localhost:8080/api/v1/areas/$AREA/daily-weather -H 'Content-Type: application/json' \
  -d '{ "date": "2026-04-02", "etpMm": 7.50, "precipitationMm": 0 }'
curl -X POST http://localhost:8080/api/v1/areas/$AREA/water-balance/reprocess -H 'Content-Type: application/json' \
  -d '{ "date": "2026-04-02" }'
```

A resposta é a lista das novas revisões (`revision: 2`), em ordem cronológica. No dia 2, ETc = 6,00 mm, AFDf = 26,00 mm, I = 6,00 mm e o tempo é 1,0000 h. O dia 3 passa a ter AFDi = 26,00 mm e AFDf = 27,00 mm. Corrigir a meteorologia ou a IR não altera balanços já calculados: só o reprocessamento gera novas revisões.

### Imutabilidade e revisões

- Cada cálculo grava uma linha em `daily_water_balance`. Uma linha nunca é alterada nem apagada. Um gatilho no banco rejeita `UPDATE` e `DELETE` diretos, com uma única exceção: passar `is_current` de `true` para `false`.
- Cada data tem **uma** revisão corrente. O reprocessamento marca as correntes como não correntes e insere as revisões `n + 1`, na mesma transação. As revisões antigas continuam em `GET .../{date}/revisions`.
- O reprocessamento é tudo-ou-nada. Ele recalcula, em memória, a data inicial e todas as posteriores que já têm balanço corrente, com os cadastros, a meteorologia e a IR **atuais**. Se qualquer dia violar uma pré-condição, a resposta é 422 com a data violadora e nada é gravado. Dias nunca calculados não são calculados.
- Cada balanço guarda um instantâneo das entradas (`inputs`). Alterar o F do plano, o solo ou a cultura depois do cálculo não muda balanços existentes.
- Área, solo e cultura referenciados por um plano ou balanço não podem ser excluídos (422).

### Política de arredondamento

- **Cálculo exato:** ETc, CAD, AFDi, AFDf e I usam `BigDecimal` sem arredondamento, e os valores são gravados como calculados. Cálculos seguintes usam o valor gravado, nunca o apresentado.
- **Tempo de funcionamento:** é a única divisão (I ÷ taxa do aspersor), calculada e **gravada com 10 casas decimais, HALF_EVEN**.
- **Apresentação (somente nas respostas):** mm com 2 casas, HALF_UP; horas com 4 casas, HALF_UP; minutos inteiros (horas × 60, HALF_UP), com carry para a hora e minutos de 0 a 59. Por exemplo, 2,5 ÷ 4 = 0,6250 h, 37,5 min, que aparece como 0 h 38 min. A mesma regra vale em todos os endpoints do módulo. HALF_UP arredonda metades para longe do zero, inclusive em valores negativos.
- Quando não há recomendação (primeiro dia ou I ≤ 0), o tempo é zero.

### Tratamento de erros

Respostas em `application/problem+json`, na ordem de verificação: 400, 404, 409 e 422.

| Status | Quando | Corpo |
|---|---|---|
| 400 | Campo ausente, fora da faixa ou com formato inválido (por exemplo, `precipitationMm` omitido ou data anterior ao início do primeiro plano) | `violations[]` com `field` e `message`, todas juntas |
| 404 | Área, plano, solo ou cultura inexistente, ou balanço inexistente na data consultada | `title` e `detail` |
| 409 | Já existe um cálculo ou reprocessamento em andamento para a área | `title` e `detail`; repetir a chamada em instantes |
| 422 | Regra de negócio violada: data já calculada (use o reprocessamento), dia anterior sem balanço, área sem plano ou data fora dos planos, reprocesso de data não calculada, exclusão de registro referenciado | `title` e `detail` |
| 422 | Dados do dia incompletos (cálculo e reprocessamento) | Além de `detail`, traz `date` e `preconditions[]` com `code`, `message` e `fields`, todas as faltas juntas |

Códigos de `preconditions[]`:

| `code` | Significado | `fields` |
|---|---|---|
| `WEATHER_MISSING` | Sem meteorologia da área na data | vazio |
| `APPLIED_IRRIGATION_MISSING` | Sem irrigação real da área na data | vazio |
| `SOIL_FIELDS_MISSING` | O solo do plano não tem CC ou PMP gravimétricos ou densidade | `fieldCapacityGravimetric`, `permanentWiltingPointGravimetric`, `bulkDensity` (os ausentes) |
| `CROP_FIELDS_MISSING` | A cultura do plano não tem `kc` ou profundidade radicular | `kc`, `rootDepth` (os ausentes) |

Exemplo de 422, para um dia sem meteorologia nem irrigação real (`fields` vazio nesses dois códigos):

```json
{
  "type": "about:blank",
  "title": "Violação de regra de negócio",
  "status": 422,
  "detail": "Pré-condições não atendidas para a data 2026-04-03",
  "date": "2026-04-03",
  "preconditions": [
    { "code": "WEATHER_MISSING", "message": "Não há meteorologia diária (ETP e precipitação) da área na data 2026-04-03", "fields": [] },
    { "code": "APPLIED_IRRIGATION_MISSING", "message": "Não há Irrigação Real da área na data 2026-04-03", "fields": [] }
  ]
}
```

## Estrutura do projeto

```
src/main/java/com/irrigation
├── domain            # regras de negócio em Java puro (sem Spring/JPA)
│   ├── model         #   agregados, value objects e enums
│   ├── calculation   #   motor de cálculo (Strategy + Registry)
│   ├── repository    #   portas de repositório
│   └── exception
├── application       # casos de uso: serviços, DTOs, paginação, validação
├── infrastructure    # adaptadores
│   ├── persistence   #   entidades JPA, repositórios e mappers manuais
│   ├── health        #   verificação do banco
│   └── config        #   JPA, motor de cálculo, Clock
└── presentation      # controllers REST, DTOs de transporte (MapStruct) e tratamento de erros

src/main/resources
├── db/migration      # migrações Flyway (V1 schema, V2 unidades do solo, V3 auditoria)
├── application.yml   # configuração base (lê as variáveis de ambiente)
└── logback-*.xml     # log JSON (padrão) e legível (perfil dev)

docs/adr              # Architecture Decision Records
.kiro/specs           # requisitos, design e plano de tarefas
```

As camadas dependem apenas das camadas internas, o que é verificado por ArchUnit. Ver [ADR-003](docs/adr/ADR-003-monolito-modular.md).

## Decisões de arquitetura

As decisões principais estão em [`docs/adr`](docs/adr/README.md): separação entre os modelos de domínio e de persistência, motor de cálculo extensível, monólito modular, Problem Details e testes baseados em propriedades. A especificação completa (requisitos, design e tarefas) está em `.kiro/specs/irrigation-management-backend/`.
