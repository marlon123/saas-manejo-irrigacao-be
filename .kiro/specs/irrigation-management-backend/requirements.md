# Documento de Requisitos

## Introdução

Este documento especifica os requisitos do **irrigation-management-backend**, um backend SaaS para manejo de irrigação agrícola. O sistema permite que usuários cadastrem áreas de cultivo, culturas e parâmetros de solo, e executem cálculos agronômicos para determinar a necessidade de irrigação, produzindo recomendações quantitativas como lâmina de irrigação (mm), volume de água necessário (m³) e tempo estimado de irrigação.

Esta primeira fase concentra-se exclusivamente no backend. Autenticação, multi-tenancy e componentes de frontend estão fora de escopo. A arquitetura deverá ser modular e preparada para evolução futura, incluindo a adição posterior de autenticação, multi-tenancy e integrações externas.

A metodologia de cálculo (fórmulas agronômicas) é intencionalmente deixada indefinida nesta fase. O sistema deverá prover os contratos, interfaces e estruturas de domínio para receber implementações futuras de cálculo, sem inventar fórmulas por suposição.

## Glossário

- **Irrigation_Backend**: A aplicação backend completa para manejo de irrigação.
- **Irrigation_API**: A API REST exposta pelo Irrigation_Backend sob o prefixo /api/v1.
- **Area_Service**: O serviço de aplicação responsável pelo gerenciamento de áreas de cultivo.
- **Crop_Service**: O serviço de aplicação responsável pelo gerenciamento de culturas.
- **Soil_Service**: O serviço de aplicação responsável pelo gerenciamento de parâmetros de solo.
- **Recommendation_Service**: O serviço de aplicação responsável pelas recomendações de irrigação e seu histórico.
- **Calculation_Engine**: O componente de domínio responsável por executar metodologias de cálculo de irrigação, independente das camadas de apresentação e infraestrutura.
- **Exception_Handler**: O componente responsável pelo tratamento centralizado de exceções e respostas de erro padronizadas.
- **Area**: Entidade de domínio representando uma área de cultivo (área ou talhão).
- **Crop**: Entidade de domínio representando uma cultura agrícola (cultura).
- **Crop_Stage**: Entidade de domínio representando um estágio de desenvolvimento de uma cultura, associado a um coeficiente de cultura (Kc).
- **Soil_Profile**: Entidade de domínio representando parâmetros de solo.
- **Recommendation**: Entidade de domínio representando uma recomendação de irrigação, incluindo entradas, resultados, metodologia e versão.
- **Calculation_Method**: Contrato de domínio (interface) que define uma metodologia de cálculo de irrigação.
- **Calculation_Input**: Value object contendo os parâmetros de entrada para um cálculo de irrigação.
- **Calculation_Result**: Value object contendo as saídas de um cálculo de irrigação.
- **Problem_Details**: Formato de resposta de erro seguindo a RFC 7807 (application/problem+json).
- **Health_Check**: Endpoint que reporta o status de saúde da aplicação e suas dependências.
- **Flyway**: Ferramenta de versionamento e migração de schema de banco de dados.
- **JaCoCo**: Ferramenta de cobertura de código Java para medir a cobertura de testes.
- **Testcontainers**: Biblioteca que provê instâncias leves e descartáveis de contêineres Docker para testes de integração.
- **OpenAPI**: Especificação para descrição de APIs REST, usada para geração de documentação da API.
- **Dockerfile**: Arquivo que define as instruções de build da imagem de contêiner.
- **Docker_Compose**: Ferramenta para definição e execução de aplicações Docker multi-contêiner.
- **Kc**: Coeficiente de cultura, um fator adimensional que relaciona a evapotranspiração da cultura com a evapotranspiração de referência.
- **ETo**: Evapotranspiração de referência.
- **ETc**: Evapotranspiração da cultura, calculada como ETo multiplicado por Kc.
- **Hectare**: Unidade de área equivalente a 10.000 metros quadrados, usada para medição de área de cultivo.
- **Irrigation_Depth**: Lâmina de irrigação, a profundidade de água a ser aplicada, medida em milímetros (mm).
- **Water_Volume**: Volume de água necessário para irrigação, medido em metros cúbicos (m³).
- **Irrigation_Time**: Duração estimada da irrigação, medida em horas (h).

## Requisitos

### Requisito 1: Gerenciamento de Áreas de Cultivo

**História de Usuário:** Como agrônomo, quero cadastrar e gerenciar áreas de cultivo, para que eu possa usá-las como referência para cálculos de irrigação e histórico de recomendações.

#### Critérios de Aceitação

1. QUANDO uma requisição válida de criação de área for recebida, O Area_Service DEVE persistir a área e retornar o recurso criado com HTTP 201 Created
2. QUANDO uma requisição de criação ou atualização de área contiver dados inválidos, O Area_Service DEVE retornar HTTP 400 Bad Request com uma resposta Problem_Details descrevendo cada erro de validação
3. QUANDO uma requisição de listagem de áreas for recebida, O Area_Service DEVE retornar uma coleção paginada de áreas com HTTP 200 OK, usando número de página padrão 0, tamanho de página padrão 20 e tamanho máximo de página 100
4. QUANDO uma requisição de listagem de áreas incluir parâmetros de página e tamanho, O Area_Service DEVE retornar a página correspondente de resultados, retornando uma página vazia quando a página solicitada exceder as páginas disponíveis
5. QUANDO uma requisição de listagem de áreas incluir um parâmetro de ordenação, O Area_Service DEVE retornar resultados ordenados pelo campo especificado, limitado aos campos válidos para ordenação: nome e área de cultivo em hectares
6. QUANDO uma requisição de obtenção de área por identificador for recebida e a área existir, O Area_Service DEVE retornar HTTP 200 OK com a representação da área
7. QUANDO uma requisição de obtenção de área por identificador for recebida e a área não existir, O Area_Service DEVE retornar HTTP 404 Not Found com uma resposta Problem_Details
8. QUANDO uma requisição válida de atualização de área for recebida e a área existir, O Area_Service DEVE persistir as alterações e retornar HTTP 200 OK com a representação atualizada
9. QUANDO uma requisição de atualização de área for recebida para uma área inexistente, O Area_Service DEVE retornar HTTP 404 Not Found com uma resposta Problem_Details
10. QUANDO uma requisição de exclusão de área por identificador for recebida e a área existir e não for referenciada por outros registros, O Area_Service DEVE remover a área e retornar HTTP 204 No Content
11. QUANDO uma requisição de exclusão de área por identificador for recebida e a área não existir, O Area_Service DEVE retornar HTTP 404 Not Found com uma resposta Problem_Details
12. A entidade Area DEVE armazenar nome, área de cultivo em hectares, localização geográfica, características gerais, tipo de sistema de irrigação e vazão do sistema
13. O Area_Service DEVE validar que o nome seja fornecido, não seja em branco e tenha no máximo 255 caracteres
14. O Area_Service DEVE validar que a área de cultivo em hectares seja maior que zero e no máximo 1.000.000,00
15. O Area_Service DEVE validar que a vazão do sistema seja maior que zero e no máximo 1.000.000,00
16. ONDE uma localização geográfica for fornecida, O Area_Service DEVE armazenar a latitude e longitude em graus decimais, com latitude no intervalo -90 a 90 e longitude no intervalo -180 a 180
17. SE uma requisição de criação ou atualização de área contiver coordenadas geográficas fora dos intervalos válidos, ENTÃO O Area_Service DEVE retornar HTTP 400 Bad Request com uma resposta Problem_Details
18. SE uma requisição de listagem de áreas contiver parâmetros de paginação ou ordenação inválidos, ENTÃO O Area_Service DEVE retornar HTTP 400 Bad Request com uma resposta Problem_Details
19. SE uma requisição de exclusão de área for recebida e a área for referenciada por registros de cálculo de irrigação ou histórico de recomendações, ENTÃO O Area_Service DEVE retornar HTTP 422 Unprocessable Entity com uma resposta Problem_Details

### Requisito 2: Gerenciamento de Culturas

**História de Usuário:** Como agrônomo, quero cadastrar e gerenciar culturas com seus estágios de desenvolvimento e coeficientes de cultura, para que o motor de cálculo possa usar parâmetros específicos de cada cultura.

#### Critérios de Aceitação

1. QUANDO uma requisição válida de criação de cultura for recebida, O Crop_Service DEVE persistir a cultura e retornar o recurso criado com HTTP 201 Created
2. QUANDO uma requisição de criação de cultura contiver dados inválidos, O Crop_Service DEVE retornar HTTP 400 Bad Request com uma resposta Problem_Details descrevendo cada erro de validação
3. QUANDO uma requisição de listagem de culturas for recebida, O Crop_Service DEVE retornar uma coleção paginada de culturas com HTTP 200 OK, usando número de página padrão 0 e tamanho de página padrão 20
4. QUANDO uma requisição de listagem de culturas incluir parâmetros de página e tamanho, O Crop_Service DEVE retornar a página correspondente de resultados, com números de página iniciando em 0 e tamanho de página no intervalo de 1 a 100 inclusive
5. QUANDO uma requisição de obtenção de cultura por identificador for recebida e a cultura existir, O Crop_Service DEVE retornar HTTP 200 OK com a representação da cultura
6. QUANDO uma requisição de obtenção de cultura por identificador for recebida e a cultura não existir, O Crop_Service DEVE retornar HTTP 404 Not Found com uma resposta Problem_Details
7. QUANDO uma requisição válida de atualização de cultura for recebida e a cultura existir, O Crop_Service DEVE persistir as alterações e retornar HTTP 200 OK com a representação atualizada
8. QUANDO uma requisição de atualização de cultura for recebida para uma cultura inexistente, O Crop_Service DEVE retornar HTTP 404 Not Found com uma resposta Problem_Details
9. QUANDO uma requisição de exclusão de cultura por identificador for recebida e a cultura existir, O Crop_Service DEVE remover a cultura e retornar HTTP 204 No Content
10. QUANDO uma requisição de exclusão de cultura por identificador for recebida e a cultura não existir, O Crop_Service DEVE retornar HTTP 404 Not Found com uma resposta Problem_Details
11. A entidade Crop DEVE armazenar nome com no máximo 255 caracteres, variedade com no máximo 255 caracteres, ciclo de cultivo e de uma a vinte entradas de Crop_Stage
12. A entidade Crop DEVE conter pelo menos um Crop_Stage, cada um com nome de no máximo 100 caracteres, um valor de Kc e uma ordem sequencial como inteiro positivo único dentro da cultura
13. O Crop_Service DEVE validar que cada valor de Kc de Crop_Stage seja maior que zero e no máximo 2,0
14. O Crop_Service DEVE validar que o nome da cultura seja fornecido, não seja em branco e tenha no máximo 255 caracteres
15. SE uma requisição de listagem de culturas contiver parâmetros de paginação inválidos, ENTÃO O Crop_Service DEVE retornar HTTP 400 Bad Request com uma resposta Problem_Details
16. SE uma requisição de atualização de cultura existente contiver dados inválidos, ENTÃO O Crop_Service DEVE retornar HTTP 400 Bad Request com uma resposta Problem_Details

### Requisito 3: Gerenciamento de Parâmetros de Solo

**História de Usuário:** Como agrônomo, quero cadastrar parâmetros de solo com unidades explícitas, para que o motor de cálculo possa usar dados específicos de armazenamento e retenção de água do solo.

#### Critérios de Aceitação

1. QUANDO uma requisição válida de criação de perfil de solo for recebida, O Soil_Service DEVE persistir o perfil de solo e retornar o recurso criado com HTTP 201 Created
2. QUANDO uma requisição de criação de perfil de solo contiver dados inválidos, O Soil_Service DEVE retornar HTTP 400 Bad Request com uma resposta Problem_Details descrevendo cada erro de validação
3. QUANDO uma requisição de listagem de perfis de solo for recebida, O Soil_Service DEVE retornar uma coleção paginada de perfis de solo com HTTP 200 OK, usando tamanho de página padrão 20 e tamanho máximo de página 100
4. QUANDO uma requisição de obtenção de perfil de solo por identificador for recebida e o perfil de solo existir, O Soil_Service DEVE retornar HTTP 200 OK com a representação do perfil de solo
5. QUANDO uma requisição de obtenção de perfil de solo por identificador for recebida e o perfil de solo não existir, O Soil_Service DEVE retornar HTTP 404 Not Found com uma resposta Problem_Details
6. QUANDO uma requisição válida de atualização de perfil de solo for recebida e o perfil de solo existir, O Soil_Service DEVE persistir as alterações e retornar HTTP 200 OK com a representação atualizada
7. QUANDO uma requisição de atualização de perfil de solo for recebida para um perfil de solo inexistente, O Soil_Service DEVE retornar HTTP 404 Not Found com uma resposta Problem_Details
8. QUANDO uma requisição de exclusão de perfil de solo por identificador for recebida e o perfil de solo existir, O Soil_Service DEVE remover o perfil de solo e retornar HTTP 204 No Content
9. QUANDO uma requisição de exclusão de perfil de solo por identificador for recebida e o perfil de solo não existir, O Soil_Service DEVE retornar HTTP 404 Not Found com uma resposta Problem_Details
10. A entidade Soil_Profile DEVE armazenar tipo de solo, capacidade de campo, ponto de murcha permanente, densidade aparente, profundidade efetiva de raízes e água disponível
11. O Soil_Service DEVE validar que a capacidade de campo seja maior que o ponto de murcha permanente, com ambos os valores no intervalo de 0,00 a 1,00 representando conteúdo de água volumétrico
12. O Soil_Service DEVE validar que a densidade aparente seja maior que zero e no máximo 3,00 gramas por centímetro cúbico
13. O Soil_Service DEVE validar que a profundidade efetiva de raízes seja maior que zero e no máximo 5.000,00 milímetros
14. A entidade Soil_Profile DEVE associar uma unidade de medida explícita a cada parâmetro
15. QUANDO uma requisição de atualização de perfil de solo existente contiver dados inválidos, O Soil_Service DEVE retornar HTTP 400 Bad Request com uma resposta Problem_Details

### Requisito 4: Motor de Cálculo de Irrigação

**História de Usuário:** Como desenvolvedor, quero um motor de cálculo com contratos extensíveis, para que diferentes metodologias de cálculo agronômico possam ser implementadas e evoluídas sem afetar as camadas REST e de persistência.

#### Critérios de Aceitação

1. O Calculation_Engine DEVE ser independente das camadas de apresentação e infraestrutura, de forma que nenhum componente de apresentação ou infraestrutura seja necessário para o funcionamento do motor
2. O Calculation_Engine DEVE definir o contrato Calculation_Method que as metodologias de cálculo implementam
3. O Calculation_Engine DEVE permitir o registro de múltiplas implementações de Calculation_Method, cada uma identificada por um identificador único de metodologia
4. O Calculation_Engine DEVE definir os value objects Calculation_Input e Calculation_Result representando entradas e saídas de cálculo
5. QUANDO um cálculo for solicitado com uma metodologia registrada, O Calculation_Engine DEVE delegar a execução ao Calculation_Method correspondente
6. SE um cálculo for solicitado com uma metodologia não registrada, ENTÃO O Calculation_Engine DEVE lançar um erro indicando que a metodologia não está disponível
7. O Calculation_Result DEVE incluir a Irrigation_Depth no intervalo de 0 a 500 milímetros, a Water_Volume no intervalo de 0 a 999999,99 metros cúbicos e a Irrigation_Time no intervalo de 0 a 999,99 horas
8. O domínio DEVE definir value objects para parâmetros de entrada de cálculo incluindo: ETo no intervalo de 0 a 15 milímetros por dia, Kc no intervalo de 0 a 2 adimensional, ETc no intervalo de 0 a 15 milímetros por dia, precipitação efetiva no intervalo de 0 a 500 milímetros, umidade atual do solo no intervalo de 0 a 100 por cento, capacidade de armazenamento de água no intervalo de 0 a 500 milímetros, eficiência do sistema de irrigação no intervalo de 0,01 a 1 como fração, lâmina líquida de irrigação no intervalo de 0 a 500 milímetros, lâmina bruta de irrigação no intervalo de 0 a 500 milímetros, área irrigada no intervalo de 0,01 a 99999,99 hectares, vazão do sistema no intervalo de 0,01 a 9999,99 metros cúbicos por hora e tempo de irrigação no intervalo de 0 a 999,99 horas
9. QUANDO uma nova implementação de Calculation_Method for registrada, O Calculation_Engine DEVE disponibilizar a nova metodologia sem exigir modificações em implementações existentes de Calculation_Method
10. SE um Calculation_Method for registrado com um identificador de metodologia que já está registrado, ENTÃO O Calculation_Engine DEVE lançar um erro indicando o identificador duplicado e preservar o registro existente inalterado
11. SE um cálculo for solicitado com parâmetros de entrada ausentes ou fora de seus intervalos válidos, ENTÃO O Calculation_Engine DEVE lançar um erro identificando os parâmetros inválidos e NÃO DEVE executar o cálculo

### Requisito 5: Histórico de Recomendações de Irrigação

**História de Usuário:** Como agrônomo, quero armazenar e consultar o histórico de recomendações de irrigação, para que eu possa revisar cálculos passados e seus parâmetros ao longo do tempo.

#### Critérios de Aceitação

1. QUANDO um cálculo for realizado, O Recommendation_Service DEVE armazenar uma Recommendation com um identificador de recomendação, o identificador de área associado, data e hora, dados de entrada, resultados, identificador de metodologia e versão da metodologia
2. QUANDO uma requisição de listagem de recomendações for recebida com um filtro de identificador de área, O Recommendation_Service DEVE retornar uma coleção paginada de no máximo 100 recomendações por página filtrada por aquela área com HTTP 200 OK
3. QUANDO uma requisição de listagem de recomendações for recebida com um filtro de intervalo de datas, O Recommendation_Service DEVE retornar uma coleção paginada de no máximo 100 recomendações por página dentro do período especificado com HTTP 200 OK
4. QUANDO uma requisição de listagem de recomendações for recebida com ambos os filtros de identificador de área e intervalo de datas, O Recommendation_Service DEVE retornar uma coleção paginada de no máximo 100 recomendações por página correspondendo a ambos os critérios com HTTP 200 OK
5. QUANDO uma requisição de obtenção de recomendação por identificador for recebida e a recomendação existir, O Recommendation_Service DEVE retornar HTTP 200 OK com a representação da recomendação
6. QUANDO uma requisição de obtenção de recomendação por identificador for recebida e a recomendação não existir, O Recommendation_Service DEVE retornar HTTP 404 Not Found com uma resposta Problem_Details
7. QUANDO o identificador ou versão de uma metodologia de cálculo mudar, O Recommendation_Service DEVE preservar as recomendações armazenadas anteriormente sem modificar seus dados de entrada armazenados, resultados, identificador de metodologia ou versão de metodologia
8. QUANDO uma requisição de listagem de recomendações com filtros não retornar resultados correspondentes, O Recommendation_Service DEVE retornar HTTP 200 OK com uma coleção paginada vazia

### Requisito 6: Padronização e Versionamento da API

**História de Usuário:** Como desenvolvedor, quero uma API REST padronizada, para que os clientes possam consumir os endpoints de manejo de irrigação de forma consistente.

#### Critérios de Aceitação

1. A Irrigation_API DEVE expor todos os endpoints sob o prefixo /api/v1
2. A Irrigation_API DEVE usar DTOs separados de requisição e resposta para cada operação
3. QUANDO um endpoint de coleção for chamado, A Irrigation_API DEVE retornar resultados paginados com metadados de número de página, tamanho de página, total de elementos e total de páginas, usando tamanho de página padrão de 20 elementos, tamanho máximo de página de 100 elementos e números de página iniciando em 0
4. QUANDO um recurso for criado com sucesso, A Irrigation_API DEVE retornar HTTP 201 Created
5. QUANDO um recurso for obtido ou atualizado com sucesso, A Irrigation_API DEVE retornar HTTP 200 OK
6. QUANDO um recurso for excluído com sucesso, A Irrigation_API DEVE retornar HTTP 204 No Content
7. QUANDO um recurso solicitado não existir, A Irrigation_API DEVE retornar HTTP 404 Not Found
8. QUANDO dados de entrada estiverem com campos obrigatórios ausentes, malformados ou contiverem valores fora dos intervalos permitidos, A Irrigation_API DEVE retornar HTTP 400 Bad Request
9. QUANDO um erro inesperado ocorrer, A Irrigation_API DEVE retornar HTTP 500 Internal Server Error
10. QUANDO A Irrigation_API retornar um status de erro, A Irrigation_API DEVE incluir um corpo de resposta contendo uma mensagem de erro indicando a causa do erro

### Requisito 7: Tratamento de Erros Padronizado

**História de Usuário:** Como desenvolvedor, quero respostas de erro padronizadas, para que os clientes possam tratar erros programaticamente e de forma consistente.

#### Critérios de Aceitação

1. QUANDO um erro de validação ocorrer, O Exception_Handler DEVE retornar HTTP 400 Bad Request com uma resposta Problem_Details que inclua um título, status, detalhe e uma lista de erros de validação de campo onde cada entrada identifica o nome do campo e fornece uma descrição do erro
2. QUANDO um recurso não for encontrado, O Exception_Handler DEVE retornar HTTP 404 Not Found com uma resposta Problem_Details que inclua um título, status e detalhe identificando o recurso ausente
3. QUANDO uma violação de regra de negócio ocorrer, O Exception_Handler DEVE retornar HTTP 422 Unprocessable Entity com uma resposta Problem_Details que inclua um título, status e detalhe descrevendo a regra de negócio violada
4. QUANDO um erro inesperado ocorrer, O Exception_Handler DEVE retornar HTTP 500 Internal Server Error com uma resposta Problem_Details que inclua um título, status e um detalhe genérico que não exponha detalhes de implementação internos ou stack traces
5. O Exception_Handler DEVE registrar todas as exceções usando logging estruturado que capture o timestamp, tipo da exceção, mensagem da exceção e stack trace

### Requisito 8: Migração de Schema de Banco de Dados

**História de Usuário:** Como desenvolvedor, quero migrações de banco de dados versionadas, para que o schema evolua de forma controlada e reproduzível.

#### Critérios de Aceitação

1. A ferramenta Flyway DEVE rastrear migrações aplicadas e aplicar apenas migrações pendentes em ordem crescente de versão
2. QUANDO a aplicação iniciar, A ferramenta Flyway DEVE executar todas as migrações pendentes antes de aceitar requisições
3. A ferramenta Flyway DEVE criar tabelas para áreas, culturas, estágios de cultura, perfis de solo, recomendações e registros de cálculo
4. Cada migração DEVE ter um número de versão monotonicamente crescente que determina a ordem de execução
5. SE uma migração de banco de dados falhar, ENTÃO a aplicação DEVE interromper a inicialização, indicar a versão da migração falha e bloquear migrações subsequentes até que a falha seja resolvida

### Requisito 9: Health Checks e Observabilidade

**História de Usuário:** Como operador, quero health checks e logs estruturados, para que eu possa monitorar o status da aplicação e diagnosticar problemas.

#### Critérios de Aceitação

1. QUANDO o endpoint Health_Check receber uma requisição, O endpoint Health_Check DEVE reportar o status da aplicação como "healthy" ou "unhealthy"
2. QUANDO o endpoint Health_Check receber uma requisição, O endpoint Health_Check DEVE reportar o status de conectividade do banco de dados como "connected" ou "disconnected"
3. SE o banco de dados estiver inacessível, ENTÃO O endpoint Health_Check DEVE reportar o status geral de saúde como "degraded"
4. O Irrigation_Backend DEVE produzir logs estruturados em formato JSON que capturem o timestamp, nível de severidade do conjunto de error, warning e info, e uma mensagem descritiva

### Requisito 10: Estratégia de Testes

**História de Usuário:** Como desenvolvedor, quero uma suíte de testes abrangente, para que eu possa verificar regras de negócio e prevenir regressões.

#### Critérios de Aceitação

1. A suíte de testes DEVE incluir testes unitários para cada regra de negócio de entidade de domínio, com pelo menos um caso de teste para comportamento válido e pelo menos um caso de teste para comportamento inválido
2. A suíte de testes DEVE incluir testes unitários para cada método de serviço de aplicação, com pelo menos um caso de teste para o caminho de sucesso e pelo menos um caso de teste para o caminho de erro
3. A suíte de testes DEVE incluir testes para cada endpoint de controller REST, cobrindo no mínimo respostas de sucesso e respostas de erro
4. A suíte de testes DEVE incluir testes de integração com PostgreSQL usando Testcontainers
5. A suíte de testes DEVE incluir testes de validação que verifiquem a rejeição de dados de entrada inválidos e a aceitação de dados de entrada válidos
6. A ferramenta JaCoCo DEVE medir a cobertura de testes com uma cobertura mínima de linha de 80 por cento
7. A suíte de testes DEVE verificar comportamentos e regras de negócio por meio de asserções sobre resultados esperados em vez de depender apenas do percentual de cobertura

### Requisito 11: Containerização e Configuração de Ambiente

**História de Usuário:** Como desenvolvedor, quero aplicação containerizada e configuração de ambiente, para que eu possa executar o sistema localmente e me preparar para deploy.

#### Critérios de Aceitação

1. O Dockerfile DEVE produzir uma imagem de contêiner que seja construída sem erros e inicie sem falhas
2. O arquivo Docker_Compose DEVE incluir o serviço Irrigation_Backend e um serviço PostgreSQL, com o Irrigation_Backend dependendo da inicialização do serviço PostgreSQL
3. QUANDO o arquivo Docker_Compose for executado, OS serviços DEVEM iniciar e o Irrigation_Backend DEVE se conectar ao serviço PostgreSQL
4. O Irrigation_Backend DEVE suportar configuração externalizada através de variáveis de ambiente, permitindo diferentes valores por ambiente sem alterações de código
5. O Irrigation_Backend DEVE carregar credenciais de variáveis de ambiente em tempo de execução
6. O repositório NÃO DEVE conter arquivos com valores de credenciais commitados

### Requisito 12: Documentação da API

**História de Usuário:** Como desenvolvedor, quero documentação da API, para que eu possa entender e consumir os endpoints disponíveis.

#### Critérios de Aceitação

1. A documentação OpenAPI DEVE descrever todos os endpoints da Irrigation_API, incluindo o método HTTP, caminho do recurso e um resumo descrevendo o propósito de cada endpoint
2. A documentação OpenAPI DEVE incluir esquemas de requisição consistindo de parâmetros e definições de corpo de requisição, e esquemas de resposta consistindo de códigos de status HTTP e definições de corpo de resposta para cada endpoint
3. QUANDO um cliente acessar o endpoint de documentação via HTTP GET, A Irrigation_API DEVE retornar a documentação OpenAPI no formato de especificação OpenAPI

## Itens em Aberto e Decisões Pendentes

Os itens a seguir requerem definição antes da implementação do motor de cálculo. Estão registrados aqui conforme instrução do stakeholder para registrar decisões pendentes e propor alternativas para discussão.

1. **Fórmulas de cálculo agronômico**: As fórmulas específicas para cálculo de irrigação (ETo, ETc, lâmina de irrigação, volume de água, tempo de irrigação) não estão definidas e não devem ser implementadas por suposição. A metodologia será definida posteriormente com base em referências técnicas e validação de especialista. Alternativa: definir um contrato Calculation_Method conectável agora e implementar fórmulas concretas quando especificado.
2. **Unidade de vazão do sistema**: A unidade de medida para a vazão do sistema de irrigação (vazão) não foi especificada. Proposta: metros cúbicos por hora (m³/h). Alternativa: litros por segundo (L/s). Pendente de confirmação.
3. **Unidades dos parâmetros de solo**: As unidades para capacidade de campo e ponto de murcha permanente não foram especificadas. Proposta: conteúdo de água volumétrico (0,00 a 1,00). Alternativa: percentual. Proposta para densidade aparente: gramas por centímetro cúbico (g/cm³). Proposta para profundidade efetiva de raízes: metros (m). Proposta para água disponível: milímetros (mm). Pendente de confirmação.
4. **Formato de localização geográfica**: O formato para armazenar localização geográfica não foi especificado. Proposta: latitude e longitude em graus decimais (WGS84), intervalo de latitude -90 a 90, intervalo de longitude -180 a 180. Alternativa: descrição em texto livre. Pendente de confirmação.
5. **Tipo de sistema de irrigação**: Se o tipo de sistema de irrigação deve ser um campo de texto livre ou um valor enumerado não foi especificado. Proposta: um valor enumerado com opções comuns (aspersão, gotejamento, micro-aspersão, sulcos, pivô central, superfície). Pendente de confirmação.
6. **Parâmetros agronômicos da cultura**: Os parâmetros agronômicos associados às culturas não foram detalhados. Proposta: uma estrutura flexível de chave-valor ou campos específicos a serem definidos. Pendente de confirmação.
7. **Estrutura dos estágios de desenvolvimento da cultura**: A estrutura dos estágios de desenvolvimento não foi detalhada. Proposta: cada estágio com nome, valor de Kc, duração em dias e ordem sequencial. Pendente de confirmação.
8. **Ciclo de cultivo**: Se o ciclo de cultivo é medido em dias ou se refere a safras não foi especificado. Proposta: duração em dias. Pendente de confirmação.
9. **Fonte de ETo**: Se a evapotranspiração de referência (ETo) é informada manualmente, calculada a partir de dados meteorológicos ou obtida de uma API meteorológica externa não foi especificado. Pendente de definição.
10. **Fonte de precipitação efetiva**: A fonte dos dados de precipitação efetiva não foi especificada. Pendente de definição.
11. **Eficiência do sistema de irrigação**: Se a eficiência do sistema de irrigação é um parâmetro do solo, da área ou do sistema de irrigação não foi especificado. Proposta: um atributo da área associada ao sistema de irrigação. Pendente de confirmação.
