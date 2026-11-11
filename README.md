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
| POST / GET | `/api/v1/crops` | Criar / listar culturas com estágios |
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
