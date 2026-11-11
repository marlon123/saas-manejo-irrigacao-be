# ADR-004: Problem Details (RFC 7807) para erros

- **Status:** Aceito
- **Data:** 11/11/2026

## Contexto

Os clientes da API precisam tratar erros de forma programática e consistente (Req 7): saber o status, o motivo e, nas falhas de validação, quais campos estão errados. Ao mesmo tempo, erros inesperados não podem expor detalhes internos, como stack traces, nomes de classes ou mensagens de exceção (Req 7.4). Todas as exceções precisam ser registradas em log estruturado (Req 7.5).

## Decisão

Todas as respostas de erro usam o formato Problem Details (RFC 7807), com `Content-Type: application/problem+json`, gerado pela classe `ProblemDetail` do Spring 6 (`spring.mvc.problemdetails.enabled=true`). Um único `@RestControllerAdvice` (`GlobalExceptionHandler`) faz o mapeamento:

| Situação | Status | Observação |
|---|---|---|
| Validação de requisição, de domínio ou de parâmetros do motor | 400 | Membro extra `violations[]` com `field` e `message` de cada erro |
| Recurso inexistente | 404 | O `detail` identifica o tipo e o id |
| Regra de negócio, metodologia indisponível ou duplicada | 422 | O `detail` descreve a regra violada |
| Erro inesperado | 500 | `detail` genérico e fixo, sem dados da exceção |

As violações de validação são acumuladas (`ViolationCollector`), para o cliente receber todos os erros de uma vez e cada um no campo certo (por exemplo, `stages[0].kc`). Exceções 4xx são registradas em `WARN` e 5xx em `ERROR`, com `exceptionType`, `exceptionMessage` e `stackTrace` no log JSON.

## Alternativas consideradas

- **Formato de erro próprio (`{ "error": ..., "code": ... }`):** controle total, mas sem padrão de mercado e com contrato a documentar e manter.
- **Respostas padrão do Spring Boot (`/error`):** sem estrutura para violações por campo, e com risco de vazar detalhes conforme a configuração.

## Consequências

- O contrato de erro é padronizado e documentado na especificação OpenAPI.
- O 500 nunca vaza detalhes internos, propriedade verificada por testes com jqwik (Propriedade 16).
- A investigação de falhas depende do log estruturado, não da resposta HTTP. Esse é o comportamento desejado.
