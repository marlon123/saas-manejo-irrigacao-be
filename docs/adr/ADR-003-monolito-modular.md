# ADR-003: Monólito modular em vez de microsserviços

- **Status:** Aceito
- **Data:** 11/11/2026

## Contexto

O backend cobre um domínio coeso: áreas, culturas, perfis de solo e recomendações de irrigação. As recomendações dependem diretamente das áreas, inclusive por integridade referencial (Req 1.19). O time é pequeno, e o produto ainda prevê autenticação, multi-tenancy e integrações externas (estações meteorológicas, fontes de ETo), sem que essas peças estejam definidas.

## Decisão

Um único serviço Spring Boot, organizado em camadas com dependências apontando para dentro (Clean Architecture / hexagonal):

- `domain`: regras de negócio, portas de repositório e motor de cálculo, sem framework.
- `application`: casos de uso (serviços), DTOs de caso de uso e paginação.
- `infrastructure`: adaptadores JPA, mappers de persistência, Flyway e configuração.
- `presentation`: controllers REST sob `/api/v1`, DTOs de transporte e tratamento de erros.

As fronteiras são verificadas por `ArchitectureTest` (ArchUnit). Toda dependência entre camadas passa por interfaces (portas), o que permite substituir adaptadores sem tocar no domínio.

## Alternativas consideradas

- **Microsserviços por agregado (áreas, culturas, recomendações):** isolamento de deploy e escala, mas com custo alto e sem necessidade atual. A integridade área ↔ recomendação passaria a exigir consistência distribuída (eventos, sagas), e a operação ganharia rede, observabilidade distribuída e versionamento entre serviços.
- **Monólito sem camadas explícitas:** mais rápido no começo, mas dificultaria extrair módulos depois e testar o domínio em isolamento.

## Consequências

- Um único deploy (uma imagem Docker) e um único banco, com transações locais garantindo a integridade referencial.
- As extensões previstas têm lugar definido: filtro de autenticação na apresentação, contexto de tenant na aplicação e adaptadores de APIs externas na infraestrutura.
- Se um módulo precisar escalar ou evoluir separadamente, as portas já delimitam o corte. A extração continua possível, mas não é gratuita.
