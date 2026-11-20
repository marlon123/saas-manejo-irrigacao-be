# ADR-006: Balanço hídrico fora do motor registrado, com revisões imutáveis e reprocessamento tudo-ou-nada

- **Status:** Aceito
- **Data:** 11/11/2026

## Contexto

O módulo de balanço hídrico (spec `irrigation-water-balance-module`) calcula, por área e por dia, ETc, CAD, AFDi, AFDf, lâmina (I) e tempo de operação. A AFDi de um dia é a AFDf do dia anterior, então o cálculo depende de um estado que atravessa os dias.

O `CalculationEngine` (ADR-002) trabalha de forma diferente: guarda um `CalculationInput` genérico, com 12 campos que não existem neste cálculo, e não mantém estado entre chamadas. O módulo também precisa preservar o que foi usado em cada cálculo, para auditar por que a recomendação de um dia mudou (Req 12.7), e precisa permitir o reprocessamento de dias já calculados (Req 13).

## Decisão

- **Fora do motor registrado (DD-03).** O `WaterBalanceCalculator` é um serviço de domínio puro, em `com.irrigation.domain.waterbalance`, só com `BigDecimal` e sem dependência de framework. Ele não implementa `CalculationMethod` e não é registrado no `CalculationEngine`. A rastreabilidade se mantém pela metodologia identificada como `WATER_BALANCE_AFD`, versão `1.0`, gravada em cada balanço. `CalculationEngine`, `CalculationMethod`, `CalculationInput`, `CalculationResult` e `recommendations` ficam sem alteração. O ADR-002 continua valendo: nenhuma metodologia está registrada no motor em produção.
- **Revisões imutáveis (DD-01).** O balanço diário é um registro imutável com revisões. Reprocessar cria uma nova revisão corrente e marca a anterior como não corrente (`is_current = false`). Nada é apagado nem sobrescrito. O banco garante a regra com um índice único parcial (um balanço corrente por área e data) e um trigger de imutabilidade, que recusa alterações nos dados de um balanço já gravado. A aplicação traduz as violações em `ConflictException` e `ImmutableRecordException`.
- **Reprocessamento tudo-ou-nada (DD-02).** O reprocessamento roda em uma única transação. Se qualquer dia violar uma pré-condição, nenhuma revisão é criada (Req 13.4 e 13.5). A exclusão mútua por área usa um bloqueio consultivo do PostgreSQL com escopo de transação (`pg_try_advisory_xact_lock`, `AreaOperationLock`). Se outra operação detém o bloqueio, a resposta é 409 (Req 12.10 e 13.8).
- **Só o primeiro plano da área reinicia a AFDi.** Por decisão do usuário, o "primeiro dia" (AFDi = CAD × F e sem recomendação) vale apenas para a data de início do primeiro plano da área. Um plano posterior, por exemplo após trocar cultura ou solo, herda a cadeia, com AFDi = AFDf do dia anterior. Uma lacuna de histórico entre planos gera 422, com a primeira data sem cálculo.

## Alternativas consideradas

- **Implementar o balanço como um `CalculationMethod` no motor.** Rejeitada: o motor é sem estado, exige campos que não existem neste cálculo e o balanço depende do dia anterior. Adaptar o motor alteraria contratos já testados.
- **Atualização in-place do registro do dia.** Rejeitada: apaga o que foi usado no cálculo original, contraria RN-13 e o Req 12.7 e impede auditar por que a recomendação de ontem era diferente da de hoje.
- **Processamento parcial no reprocessamento** (gravar os dias bons e parar no primeiro ruim). Rejeitada: a AFDi de um dia é a AFDf do anterior. Gravar os dias iniciais e parar deixaria uma cadeia com um trecho novo e um trecho antigo, e o trecho antigo seria calculado sobre uma AFDf que já não existe. Uma consulta concorrente veria um estado que nunca foi correto. A gravação dia a dia, em transações separadas, tem o mesmo problema.
- **Reiniciar a AFDi a cada novo plano.** Rejeitada pelo usuário: a água disponível no solo não zera quando o plano muda, então o novo plano continua a cadeia.
- **Controle de concorrência só na aplicação.** Rejeitada: não resiste a requisições concorrentes (DD-05). Por isso o banco mantém os índices únicos parciais e o trigger, e o bloqueio consultivo evita trabalho duplicado.

## Consequências

- O motor registrado e suas recomendações não são afetados pelo módulo, e o balanço pode evoluir sem tocar no `CalculationEngine`.
- Existem dois caminhos de cálculo no sistema (motor genérico e balanço hídrico). Cada um é identificado pelo próprio id e versão de metodologia.
- O histórico completo é auditável, ao custo de mais linhas: cada reprocessamento acrescenta uma revisão por dia afetado. As consultas "correntes" precisam filtrar por `is_current`.
- Reprocessar um trecho longo mantém uma transação aberta e o bloqueio da área até o fim. Outras operações na mesma área recebem 409 nesse intervalo.
- Quem corrige um dado de entrada obtém ou o trecho inteiro recalculado ou nada, sem estado intermediário visível.
- As fórmulas do balanço são as fornecidas pelo usuário no `requirements.md`. Isso não resolve o Item em Aberto 1 do projeto original para o motor registrado.
