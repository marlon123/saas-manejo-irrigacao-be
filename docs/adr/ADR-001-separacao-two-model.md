# ADR-001: Separação two-model (domínio vs persistência)

- **Status:** Aceito
- **Data:** 11/11/2026

## Contexto

As regras de negócio (intervalos de hectares, vazão, Kc, CC > PMP, unicidade da ordem dos estágios etc.) precisam ser testáveis sem Spring nem banco, e o motor de cálculo deve ser independente das camadas de apresentação e infraestrutura (Req 4.1). Anotar os objetos de domínio com JPA traria para o domínio exigências do Hibernate: construtor sem argumentos, campos mutáveis, proxies e coleções gerenciadas.

## Decisão

Manter dois modelos:

- **Domínio** (`com.irrigation.domain`): Java puro. Agregados (`Area`, `Crop`, `SoilProfile`, `Recommendation`) e value objects imutáveis, validados no construtor (`AreaInHectares`, `Kc`, `FlowRate`...). Nenhum import de Spring ou JPA, verificado por `ArchitectureTest` (ArchUnit).
- **Persistência** (`infrastructure.persistence.entity`): classes `@Entity` separadas, mapeadas para o schema criado pelo Flyway (`ddl-auto: validate`).

A conversão entre os dois é feita por **mappers manuais** (`infrastructure.persistence.mapper`), e não por MapStruct, como previa a versão original da spec. Como os value objects são records validados e os agregados não têm setters, o mapeamento manual reconstrói cada value object pelo construtor e aplica as invariantes ao ler do banco. O MapStruct continua em uso na apresentação (DTO ↔ caso de uso), onde os tipos são simples.

Os snapshots de cálculo da `Recommendation` são gravados em JSONB por records tipados (`CalculationInputJson`, `CalculationResultJson`) com `@JdbcTypeCode(SqlTypes.JSON)`.

## Alternativas consideradas

- **Domínio anotado com JPA (um único modelo):** menos código, mas acopla o domínio ao Hibernate e enfraquece a imutabilidade dos value objects.
- **MapStruct também na persistência:** reduziria o código de mapeamento, mas exigiria fábricas e adaptações para construtores validados e campos opcionais (localização só com latitude e longitude), perdendo a vantagem de geração automática.

## Consequências

- O domínio é testado em isolamento: 219 testes unitários sem banco.
- Há código de mapeamento extra, protegido por testes de round-trip: `PersistenceMappersTest` (unitário, com jqwik) e `RepositoryRoundTripIntegrationTest` (PostgreSQL real).
- Um dado inválido gravado diretamente no banco é rejeitado ao ser lido, porque o value object o valida.
- Os estágios de cultura não têm identidade no domínio: cada gravação recria a coleção. Isso exigiu tornar `uq_crop_stage_order` `DEFERRABLE` (migração V2), porque o Hibernate insere os estágios novos antes de remover os antigos.
