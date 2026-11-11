# ADR-002: Strategy + Registry para o motor de cálculo

- **Status:** Aceito
- **Data:** 11/11/2026

## Contexto

O cálculo de irrigação (lâmina, volume e tempo) pode seguir metodologias diferentes, e as fórmulas agronômicas ainda não foram definidas (Item em Aberto 1 do requirements.md). O sistema precisa aceitar novas metodologias sem alterar as existentes (Req 4.3, 4.9), identificar a metodologia e a versão usadas em cada recomendação (Req 5.7) e manter o motor independente de framework (Req 4.1).

## Decisão

- **Strategy:** cada metodologia implementa o contrato de domínio `CalculationMethod` (`methodologyId()`, `version()`, `calculate(CalculationInput)`).
- **Registry:** o `CalculationEngine` (Java puro, em `domain.calculation`) mantém o mapa de metodologias registradas. Ele valida os parâmetros de entrada antes de delegar (400 com a lista de parâmetros inválidos), recusa registro duplicado (`DuplicateMethodologyException`) e recusa metodologia desconhecida (`MethodologyNotAvailableException`, 422).
- **Wiring:** o `CalculationEngineConfig` (infraestrutura) registra no motor todos os beans `CalculationMethod` do contexto Spring. Adicionar uma metodologia é criar uma nova classe e declará-la como bean, sem mexer no motor, nos serviços ou nos controllers.
- Cada `Recommendation` grava o identificador e a versão da metodologia, além do snapshot completo de entradas e resultados.

**Nenhuma metodologia está registrada em produção.** Por decisão do projeto, nenhuma fórmula é implementada enquanto o Item em Aberto 1 não for resolvido. Até lá, `POST /api/v1/recommendations/calculate` responde 422 depois das validações. Os testes usam uma `FakeCalculationMethod`, que devolve um resultado fixo e não implementa fórmula alguma.

## Alternativas consideradas

- **Um único serviço com `switch` por metodologia:** mais simples no início, mas toda metodologia nova altera código existente e testado.
- **Plugins carregados dinamicamente (SPI/ServiceLoader):** flexível, mas é complexidade sem demanda atual. O registro por beans Spring já permite estender sem modificar.

## Consequências

- Novas metodologias entram sem regressão nas existentes, e cada uma é testada isoladamente.
- O histórico permanece reprodutível: a recomendação diz qual metodologia e qual versão a gerou.
- Enquanto não houver metodologia registrada, a funcionalidade de cálculo fica indisponível de propósito, e a API comunica isso com um 422 explícito em vez de um resultado inventado.
