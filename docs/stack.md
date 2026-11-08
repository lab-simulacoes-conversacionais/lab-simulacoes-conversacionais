# Stack Tecnológica — POC 2.3 · Tema 09

**Definição preliminar para a construção da POC**

Versão 1.0 · 09/10/2026 · Grupo 09 · Laboratório de Simulações Conversacionais

## 1. Visão geral

A POC será uma **aplicação web única em Java**, organizada em módulos e implantada como um só serviço com a integração de IA **isolada em um adaptador** para permitir trocar de provedor sem reescrever as regras de negócio.

## 2. Tecnologias

| Camada | Tecnologia | Por que | Requisitos |
| --- | --- | --- | --- |
| Linguagem | Java 21 (LTS) | Exigência da disciplina; versão de suporte longo. | — |
| Framework | Spring Boot 3 + Spring Modulith | Padrão em Java; organiza o sistema nos 6 módulos exigidos. | RNF-11 |
| Integração com IA | Spring AI | Adaptador único para vários provedores, com streaming e moderação. | RNF-02, RNF-03 |
| Interface | Thymeleaf + HTMX | Telas geradas pelo servidor, sem projeto de frontend separado. | RNF-05, RNF-07 |
| Banco de dados | PostgreSQL + Flyway | Relacional, com esquema versionado a cada entrega. | RNF-04, RNF-10 |
| Estado e cache | Redis | Estado da sessão em andamento e cache do catálogo BNCC, com acesso por chave e expiração. | RNF-04, RF-05 |
| Segurança | Spring Security (Argon2/BCrypt) | Login só de profissionais, perfis acumuláveis, expiração de sessão. | RF-01, RF-02, RNF-09 |
| Filtro de conteúdo | Moderação do provedor + regex Java | Bloqueia conteúdo impróprio e dados pessoais antes de exibir/enviar. | RF-21, RF-22 |
| Relatórios | OpenPDF + Commons CSV + Chart.js | Exportação em formatos abertos e gráficos do painel. | RF-31, RF-32 |
| Observabilidade | Actuator + Micrometer | Latência, erros, tokens e custo por sessão. | RNF-13 |

O **provedor de IA** definido para a POC é a OpenAI. Ele atende ao RN-34, porque os dados enviados pela API não são usados para treinar modelos, e é um dos dois provedores com modelo de moderação integrado ao Spring AI, o que permite usar a mesma conta para gerar as falas e para o filtro de conteúdo (RF-22). O grupo solicita Zero Data Retention; até a aprovação vale a retenção padrão de até 30 dias para monitoramento de abuso. O modelo é escolhido no spike da E1, comparando qualidade em português, latência e custo. Trocar de provedor continua exigindo só configuração (RNF-02).

## 3. Qualidade e entrega

| Item | Ferramenta | Requisitos |
| --- | --- | --- |
| Testes | JUnit 5, Mockito e Testcontainers | RNF-11 |
| Cobertura e análise | JaCoCo (mín. 80%) e SonarCloud | RNF-11 |
| CI/CD | GitHub Actions + Docker | RNF-12 |
| Reversão | Reimplantar a imagem da versão anterior (< 15 min) | RNF-12 |
| Hospedagem | VM Linux com Docker Compose e Caddy (HTTPS automático) | RNF-09, RNF-12, RNF-16 |

### Gerência de configuração

Regras simples para o mês da POC, válidas para todo o grupo.

- **Nada direto na main.** Cada issue tem a sua branch, criada a partir da main, com o nome `numero-descricao` (exemplo: `12-sessao-redis`).
- **PR quando a issue estiver pronta.** A descrição leva `Closes #12` e a revisão é pedida a qualquer outra pessoa do grupo.
- **Merge com CI verde e 1 aprovação.** Usa-se "Squash and merge" e a branch é apagada; a issue fecha sozinha e o cartão vai para Concluído no kanban.
- **Fim de cada semana:** tag da entrega (v0.1 na E1, v0.2, v0.3 e v0.4) e implantação no piloto. Se der problema, volta-se para a tag anterior.

As migrations do Flyway são nomeadas com data e hora (`V20261110_1430__descricao.sql`), para que duas branches nunca criem migrations com o mesmo número.

## 4. Arquitetura

A POC segue o estilo de **monólito modular**: um único serviço Spring Boot, implantado como uma imagem Docker, dividido nos seis módulos exigidos pelo RNF-11. Cada módulo tem fronteira própria, verificada automaticamente pelo **Spring Modulith**, e só conversa com os outros pela API pública do módulo ou por eventos de domínio. Essa forma mantém a implantação e a reversão simples durante o mês do piloto e permite extrair um módulo como serviço separado no futuro, se a operação crescer para várias escolas.

### Camadas

Dentro de cada módulo o código é organizado em quatro camadas. A dependência vai sempre de cima para baixo: a apresentação chama a aplicação, que usa o domínio e a infraestrutura. O domínio não depende de framework.

| Camada | Responsabilidade | Tecnologia |
| --- | --- | --- |
| Apresentação | Telas e fragmentos HTML, formulários e envio das respostas da IA em streaming para a tela de projeção. | Spring MVC, Thymeleaf, HTMX (SSE) |
| Aplicação | Casos de uso de cada módulo: abre a transação, verifica o perfil do usuário e aplica as regras de negócio. | Spring Boot, Spring Security |
| Domínio | Entidades, estados e regras de negócio (RN), como as transições do ciclo de vida do cenário e da sessão. | Java 21 |
| Infraestrutura | Repositórios, cache, chamadas ao provedor de IA, tarefas agendadas e publicação de eventos. | JPA/Hibernate, PostgreSQL, Redis, Spring AI |

### Módulos

A tabela mostra o que cada módulo faz e de quem depende. As setas de dependência não formam ciclos, condição que o teste de arquitetura do Spring Modulith verifica a cada build.

| Módulo | Responsabilidade | Depende de | Requisitos |
| --- | --- | --- | --- |
| Acesso | Usuários, perfis acumuláveis, turmas, parâmetros (limite de perguntas, custo diário) e autenticação. | — | RF-01 a RF-04 |
| Cenários | Cenários, versões, vínculo com a BNCC, quiz, curadoria (envio, teste, checklist, decisão), biblioteca e arquivamento. | Acesso, Adaptador de IA | RF-05 a RF-13, RF-27 |
| Sessões | Sessões em sala, mensagens, controles (pausar, retomar, reiniciar, encerrar), tempo, bloqueio de dados pessoais, aplicação do quiz e engajamento. | Acesso, Cenários, Adaptador de IA | RF-14 a RF-22, RF-28 a RF-30 |
| Auditoria | Casos de conteúdo bloqueado, sinalizações, triagem, regras de suspensão e log de ações críticas. | Acesso | RF-23 a RF-26 |
| Métricas | Indicadores por sessão, cenário e turma, comparação com sessões de controle, painel e exportação CSV/PDF. | Acesso (lê dados por eventos) | RF-31, RF-32 |
| Adaptador de IA | Porta única para o provedor: falas do personagem, sugestão de cenário, moderação, prompts versionados e metadados de cada chamada. | — | RNF-02, RNF-10, RNF-13 |

### Regras de dependência e comunicação

Cada módulo expõe apenas o pacote raiz (sua API); os subpacotes internos ficam inacessíveis aos outros módulos. O teste `ApplicationModules.verify()` falha o build se alguém criar um ciclo ou acessar uma classe interna de outro módulo.

- **Chamada direta** pela API do módulo quando quem chama precisa da resposta na hora. Exemplo: Sessões pede a Cenários a versão aprovada vigente antes de iniciar a sessão (RN-01).
- **Evento de domínio** quando outro módulo apenas reage ao que aconteceu. Exemplo: ao encerrar a sessão, Métricas grava os indicadores; ao aprovar um cenário, Auditoria grava o log. Os eventos ficam no registro de publicação do Spring Modulith, no PostgreSQL, e são reprocessados se o ouvinte falhar.

### Eventos de domínio

| Evento | Publicado por | Consumido por | Efeito |
| --- | --- | --- | --- |
| CenarioAprovado, CenarioDevolvido, CenarioArquivado | Cenários | Auditoria | Registra a ação crítica no log (RN-35). |
| ConteudoBloqueado | Sessões | Auditoria | Abre caso na fila do curador; a sessão já foi pausada (RN-20). |
| SinalizacaoRegistrada | Auditoria | Cenários, Sessões | Se marcada como grave, suspende o cenário e pausa sessões em andamento (RN-23a, RN-25). |
| SinalizacaoTriada | Auditoria | Cenários, Métricas | Mantém ou retira a suspensão (RN-23b a d) e atualiza a taxa de erros (RN-29). |
| SessaoEncerrada | Sessões | Métricas | Grava contadores e indicadores da sessão (RN-28). |
| PerfilAlterado, RelatorioExportado | Acesso, Métricas | Auditoria | Registra a ação crítica no log (RN-35). |

## 5. Design dos componentes

### Ciclo de vida do cenário

O status do cenário segue a Figura 3 do documento de requisitos: **Rascunho, Em curadoria, Aprovado, Suspenso e Arquivado**. Cada transição é um método da entidade de domínio que confere as regras antes de mudar o estado e lança erro de negócio quando alguma não é atendida. Assim cada regra tem um teste unitário próprio, sem banco nem tela.

- **Enviar à curadoria** exige vínculo com a BNCC e conteúdo mínimo (RN-02, RN-03).
- **Aprovar** exige perfil Curador, autor diferente do curador (RN-05, RN-36) e, a partir da E2, checklist preenchido e pelo menos 5 perguntas de teste (RN-06). A versão aprovada passa a ser a vigente e fica imutável (RN-08).
- **Devolver** exige comentário e volta a versão para Rascunho (RN-07).
- **Editar um cenário aprovado** cria uma nova versão em Rascunho; a versão vigente continua valendo em sala até a nova ser aprovada (RN-08).
- **Suspender e reativar** são acionados pelos eventos da Auditoria (RN-23); **arquivar** retira o cenário da biblioteca sem apagar histórico (RN-09).

### Ciclo de vida da sessão

A sessão tem três estados: **Em andamento, Pausada e Encerrada**. A pausa guarda o motivo (manual, conteúdo bloqueado, limite de 50 minutos ou cenário suspenso), porque cada motivo mostra um aviso diferente ao professor. Encerrada é um estado final (RN-19). Reiniciar não muda o estado: marca um ponto de corte, e a tela e o histórico enviado à IA passam a começar dali, enquanto o registro completo continua guardado.

### Adaptador de IA

O módulo define uma **interface própria** (porta) com três operações: responder no papel do personagem, devolvendo a resposta em trechos; sugerir um cenário a partir de tema, componente, ano e habilidades BNCC; e moderar um texto. A implementação usa o ChatClient e o modelo de moderação do Spring AI, e o provedor é escolhido por configuração. Nenhum outro módulo importa classes do Spring AI, o que cumpre o RNF-02.

O **montador de prompt** junta o template versionado (arquivo no repositório) com os dados da versão do cenário na mensagem de sistema. O histórico da sessão vem em seguida, e a pergunta da turma vai sempre em uma mensagem de usuário separada, nunca concatenada às instruções, para reduzir injeção de prompt (RNF-09). Cada chamada devolve provedor, modelo, parâmetros, versão do prompt, tokens, custo e latência, que são gravados com a resposta (RNF-10, RNF-13).

### Fluxo de uma pergunta em sala

1. O professor envia a pergunta digitada pelo painel de controle, por uma requisição HTMX.
2. Sessões lê o estado da sessão no Redis e confere se ela está em andamento, se o limite de perguntas não foi atingido (RN-18) e o tempo decorrido (RN-17).
3. O texto passa pelo bloqueio de dados pessoais, feito com expressões regulares em Java (RN-13). Se houver padrão de e-mail, telefone, CPF ou endereço, nada é enviado e o professor vê o aviso para reescrever.
4. A pergunta passa pela moderação do provedor (RN-20).
5. A pergunta é gravada no PostgreSQL com autoria "Turma" e o estado da sessão é atualizado no Redis.
6. O Adaptador de IA monta o prompt e chama o provedor em streaming.
7. Os trechos recebidos são agrupados por frase; cada frase é moderada antes de ir para a tela por SSE (RN-20, RNF-03). Se alguma for bloqueada, a resposta não aparece, a sessão pausa e é publicado o evento ConteudoBloqueado.
8. A resposta completa é gravada com seus metadados e as métricas de latência, tokens e custo são registradas no Micrometer (RNF-10, RNF-13).

O passo 7 concilia o RN-20, que exige filtrar antes de exibir, com o RNF-03, que exige o primeiro trecho em até 3 segundos. Fica definido que a resposta é moderada frase a frase e a exibição começa assim que a primeira frase é aprovada. Na E1, um teste com 30 perguntas de referência mede o tempo até a primeira frase aprovada, com critério de 95% em até 3 segundos. Se o critério não for atingido, o sistema passa a moderar a resposta inteira antes de exibir (as respostas têm no máximo cerca de 120 palavras, RN-16), mostra o aviso "personagem pensando" enquanto isso, e o grupo leva ao PO a revisão da meta do RNF-03.

### Estado da sessão em andamento no Redis

O estado "quente" de cada sessão em andamento fica no **Redis**, em uma chave por sessão: status, motivo da pausa, contador de perguntas, horário de início, tempo acumulado em pausa, ponto de reinício e as últimas mensagens exibidas. Ele é atualizado a cada interação. Se a página for recarregada ou a conexão cair, a tela de projeção é reconstruída a partir dessa chave, sem refazer consultas pesadas no banco (RF-20, RNF-04).

O PostgreSQL continua sendo a **fonte da verdade**: toda pergunta, resposta e evento de pausa é gravado no banco na mesma interação. Se o Redis reiniciar ou a chave sumir, o estado é reconstruído a partir do banco. Por isso o Redis não precisa de persistência em disco, e a chave expira algumas horas depois que a sessão é encerrada ou fica inativa. A regra de uma sessão em andamento por turma (RN-10) continua garantida no PostgreSQL.

O segundo uso do Redis é o **cache do catálogo BNCC** (Spring Cache): são dados de referência, consultados a cada busca e criação de cenário, que só mudam quando o importador roda. O importador limpa o cache ao terminar.

O Redis entra como banco NoSQL de chave e valor porque esses dois casos pedem acesso por chave, baixa latência e expiração automática. Ele não guarda nenhum dado que precise de integridade referencial ou de histórico.

## 6. Persistência de dados

### Como pensar

Antes de escolher tecnologia, fazemos duas perguntas para cada dado do sistema. O objetivo é justificar cada escolha, e não só dizer "usamos PostgreSQL".

- **O que é e como se comporta?** Quem escreve, quem lê, se muda ou é imutável, quanto tempo precisa existir e se é sensível.
- **Onde e em que formato fica melhor?** Tabela relacional, JSON dentro do banco, arquivo, cache etc.

### O que persistir

Os dados se dividem pelos seis módulos da arquitetura (RNF-11).

| Módulo | Dados | Natureza |
| --- | --- | --- |
| Acesso | Usuário, perfis (N:N), turma, parâmetros (limite de perguntas, custo diário). | Cadastral, muda pouco; senha só como hash. |
| Cenários | Cenário, versões, habilidades BNCC vinculadas, fontes, perguntas disparadoras, quiz (questões e alternativas), checklist de curadoria, comentários de devolução, catálogo BNCC. | Versões aprovadas são imutáveis (RN-08); BNCC é de referência, carregada uma vez e mantida em cache no Redis. |
| Sessões | Sessão (professor, turma, versão), mensagens (pergunta da "Turma" e resposta da IA), eventos de pausa e retomada, contagem do quiz, nº de presentes, nota de engajamento. | Alto volume, append-only; conversas apagadas 90 dias após a POC (RN-33). Estado da sessão em andamento também no Redis. |
| Auditoria | Sinalizações, triagem, suspensões, log de ações críticas. | Log imutável, nunca apagado (RN-35). |
| Métricas | Indicadores por sessão, cenário e turma. | Derivado: pode ser calculado, mas precisa sobreviver à exclusão das conversas. |
| Adaptador de IA | Prompts e templates; metadados de cada resposta (provedor, modelo, parâmetros, tokens, custo, latência). | Prompts são artefatos versionados (RNF-11); metadados formam a trilha de rastreabilidade (RNF-10). |

**O que não persistir:** nenhum dado de estudante (RNF-01). Relatórios CSV e PDF são gerados sob demanda; persiste-se só o registro da exportação no log.

### Como persistir

O núcleo é PostgreSQL relacional, com arquivos versionados para prompts e para a carga da BNCC, e Redis para o estado da sessão em andamento e o cache da BNCC.

- **PostgreSQL relacional via JPA/Hibernate.** O sistema tem muitas regras de integridade, e várias podem ser garantidas pelo próprio banco. RN-10 (uma sessão em andamento por turma): índice único parcial `UNIQUE (turma_id) WHERE status = 'EM_ANDAMENTO'`. RN-26 (de 3 a 10 questões com 4 alternativas): validação na aplicação e CHECK onde couber. RN-27 (soma das respostas ≤ presentes): validação de domínio e teste automatizado.
- **Flyway para o esquema.** Cada entrega (E1 a E4) vira um conjunto de migrations, o que aplica o conteúdo de refatoração de banco. Por exemplo, a E4 adiciona o quiz a cenários já aprovados, gerando nova versão (RN-26).
- **Versionamento de cenário com `cenario` e `cenario_versao`.** A tabela `cenario` guarda a identidade e o status atual; `cenario_versao` guarda um snapshot completo e imutável de cada versão. O conteúdo textual da versão (contexto, limites, fontes) pode ficar em JSONB, o que facilita comparar versões (RF-11). O que serve para filtros e integridade (componente, ano, habilidades BNCC, status) fica em colunas e tabelas relacionais. A sessão aponta para a versão, nunca para o cenário genérico.
- **Log de auditoria append-only.** Tabela sem UPDATE nem DELETE permitidos, por permissão revogada do usuário da aplicação ou por trigger. Opcional: encadear um hash de cada linha com a anterior, tornando adulterações detectáveis.
- **Mensagens com retenção.** Tabela de mensagens com data e um job agendado que apaga após o prazo, registrando cada execução (RNF-15). Particionar por mês é opcional, mas apagar uma partição inteira é barato.
- **Prompts em arquivos versionados no Git.** Templates de prompt em arquivos (YAML ou Markdown) no repositório; cada resposta guarda a versão de prompt usada. É persistência em arquivo e cumpre o RNF-11.
- **BNCC importada de arquivo.** A base oficial do MEC (CSV ou JSON) é carregada por migration de dados ou por um importador, aplicando a parte de arquivos e JSON da disciplina.
- **Redis para estado e cache.** Uma chave por sessão em andamento, com expiração, guarda o estado usado para retomar a aula (RNF-04); o catálogo BNCC fica em cache. Nenhum dado existe só no Redis: tudo pode ser reconstruído a partir do PostgreSQL.

### Modelo de dados

Principais tabelas do PostgreSQL. Todas usam chave primária própria e datas de criação; os nomes seguem o vocabulário do documento de requisitos.

| Tabela | Módulo | Conteúdo principal | Restrições |
| --- | --- | --- | --- |
| `usuario`, `usuario_perfil` | Acesso | Nome, e-mail, hash da senha, ativo; perfis Professor, Curador e Administrador. | E-mail único; um usuário pode ter vários perfis. |
| `turma`, `parametro` | Acesso | Escola, ano/série, turno, rótulo; parâmetros chave e valor. | Sem lista de estudantes (RN-32). |
| `habilidade_bncc` | Cenários | Código, descrição, etapa, ano, componente. | Código oficial como chave. |
| `cenario`, `cenario_versao` | Cenários | Autor, status, versão vigente; número da versão, conteúdo em JSONB, componente, etapa, ano, faixa etária, duração. | Versão aprovada imutável; duração entre 10 e 30 min (RN-17). |
| `versao_habilidade` | Cenários | Ligação entre versão e habilidades BNCC. | Ao menos uma por versão (RN-02). |
| `questao`, `alternativa` | Cenários | Enunciado, objetivo BNCC, alternativas e a correta. | 3 a 10 questões, 4 alternativas, 1 correta (RN-26). |
| `checklist_curadoria`, `devolucao` | Cenários | Itens conferidos e nº de perguntas de teste; comentário do curador. | 5 perguntas de teste (RN-06); comentário obrigatório (RN-07). |
| `sessao` | Sessões | Professor, turma, versão, tipo (simulação ou controle), status, presentes, engajamento e contadores. | Uma em andamento por turma (RN-10); engajamento de 1 a 5 (RN-28). |
| `mensagem`, `evento_sessao` | Sessões | Autoria (Turma ou IA), texto, data; pausas, retomadas e reinícios com motivo. | Mensagens apagadas pela retenção (RN-33). |
| `resposta_ia_meta` | Adaptador de IA | Provedor, modelo, parâmetros, versão do prompt, tokens, custo, latência. | Um registro por resposta da IA (RNF-10). |
| `resultado_quiz` | Sessões | Contagem por alternativa de cada questão. | Soma ≤ presentes (RN-27). |
| `sinalizacao` | Auditoria | Trecho copiado, tipo, gravidade percebida e final, justificativa, status. | Referência à mensagem pode ficar nula após a retenção. |
| `log_auditoria` | Auditoria | Usuário, ação, entidade, detalhes em JSONB, data, hash encadeado. | Sem UPDATE e DELETE (RN-35). |
| `execucao_retencao` | Sessões | Data da execução e quantidade de registros apagados. | Registro de cada execução (RNF-15). |

### Decisões sobre retenção

Os dois pontos vêm do conflito entre a exclusão das conversas (RN-33) e dados que precisam ser mantidos, e ficam definidos assim.

- **Sinalização × retenção.** Ao ser criada, a sinalização copia o texto da resposta sinalizada e da pergunta que a originou, além de guardar a sessão, a versão do cenário e os metadados da resposta (provedor, modelo, versão do prompt). A referência à mensagem é opcional (`ON DELETE SET NULL`), então a exclusão das conversas não deixa a sinalização órfã nem apaga a evidência usada na triagem.
- **Métricas × retenção.** Ao encerrar a sessão, o sistema grava nela os contadores de perguntas da turma, respostas da IA, duração efetiva e tempo em pausa. A taxa de erros (RN-29) passa a usar a soma das respostas registradas nas sessões de cada versão, e não a contagem de mensagens, e continua correta depois da exclusão das conversas. Antes de apagar mensagens, o job de retenção confere se toda sessão já tem contadores e os calcula quando faltarem.

## 7. Segurança e privacidade

As medidas abaixo aplicam os princípios de privacidade por padrão e de professor no controle em pontos concretos da arquitetura.

- **Nenhum dado de estudante:** turmas sem lista de alunos, autoria genérica "Turma" e bloqueio de padrões de dado pessoal antes de qualquer envio ao provedor (RNF-01, RN-11, RN-13).
- **Autenticação e sessão:** Spring Security com senha em Argon2, expiração da sessão por inatividade, HTTPS em toda a comunicação e proteção CSRF também nas requisições HTMX (RNF-09).
- **Autorização por perfil em cada caso de uso,** incluindo a regra de que ninguém aprova cenário que criou e a visibilidade definida no RN-37 (RN-05, RN-36).
- **Dados enviados ao provedor:** só o cenário, o histórico da sessão e a pergunta digitada (RN-34). Chaves de API ficam em variáveis de ambiente e nos segredos do GitHub, fora do repositório.
- **Injeção de prompt:** instruções do cenário separadas do texto digitado e testes automáticos com tentativas de tirar o personagem do papel (RNF-09).
