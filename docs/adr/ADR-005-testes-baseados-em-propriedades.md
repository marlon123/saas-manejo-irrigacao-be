# ADR-005: Testes baseados em propriedades com jqwik

- **Status:** Aceito
- **Data:** 11/11/2026

## Contexto

A maior parte das regras do sistema é de intervalos numéricos e invariantes: hectares, vazão, Kc entre 0 e 2, CC > PMP, paginação de 1 a 100, ordenação restrita a campos válidos, formato dos erros. Testes por exemplo cobrem bem alguns pontos escolhidos, mas deixam passar limites e combinações que ninguém pensou em escrever. A spec define 16 propriedades de correção que valem "para qualquer" entrada (design.md, seção de Propriedades).

## Decisão

Usar **jqwik** (integrado à JUnit Platform) para os testes de propriedade, ao lado dos testes por exemplo com JUnit 5 e Mockito:

- Cada propriedade do design vira um `@Property` com geradores próprios (`@Provide`): BigDecimal em faixas, enums, strings com tamanho limitado e listas.
- Mínimo de 100 tentativas por propriedade (algumas usam 200), com o comentário `// Feature: irrigation-management-backend, Property N: ...` para rastrear a propriedade até o design.
- Os testes por exemplo continuam fixando casos concretos e pontos de integração. O jqwik amplia a cobertura de entradas, sem substituí-los.

## Alternativas consideradas

- **Só testes parametrizados (`@ParameterizedTest`):** os valores ficam fixos e escolhidos à mão, sem geração nem minimização de contraexemplos.
- **Outras bibliotecas (QuickTheories, junit-quickcheck):** menos ativas e com integração inferior à JUnit Platform.

## Consequências

- Quando uma propriedade falha, o jqwik reduz a entrada ao menor contraexemplo (shrinking), o que facilita o diagnóstico.
- Os testes de propriedade levam mais tempo que os por exemplo. A suíte unitária completa roda em cerca de 1 minuto.
- O jqwik grava na raiz do projeto o arquivo `.jqwik-database`, com as sementes das falhas recentes, para repetir a execução. Ele é local e não deve ser versionado.
- Os geradores precisam ser mantidos junto com as regras: o jqwik não tem gerador padrão para todos os tipos (o `UUID`, por exemplo, exige um `@Provide`).
