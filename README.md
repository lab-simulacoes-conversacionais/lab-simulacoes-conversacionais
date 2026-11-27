# lab-simulacoes-conversacionais

POC 2.3: Laboratório de Simulações Conversacionais. O professor conduz, na tela projetada, uma conversa da turma com um personagem simulado por IA.

Aplicação web única em Java, organizada como monólito modular com Spring Modulith.

## Como rodar localmente

Pré-requisito: **Docker** com Docker Compose. Para subir tudo, incluindo o PostgreSQL e o Redis:

```
docker compose up --build
```

A aplicação aplica as migrations ao iniciar e fica em <http://localhost:8080>. Sem login, as rotas levam para a tela de entrada em `/login`. Depois de entrar, a página inicial (`/`) mostra os atalhos conforme o perfil de quem acessou (RF-01, RF-02). Para conferir se ela está conectada ao banco e ao Redis, acesse <http://localhost:8080/actuator/health>, que não exige login.

Para parar, use `docker compose down`. Para apagar também os dados do banco, use `docker compose down -v`. Se alguma porta já estiver em uso, copie `.env.example` para `.env` e troque o valor.

### Desenvolvendo fora do Docker

Também é preciso ter o **JDK 21** com `JAVA_HOME` configurado. Não é preciso instalar o Maven, porque o projeto traz o Maven Wrapper. Os testes usam Testcontainers, então o Docker precisa estar rodando.

| Ação | Linux/macOS/Git Bash | Windows (PowerShell) |
| --- | --- | --- |
| Subir só o PostgreSQL e o Redis | `docker compose up -d postgres redis` | `docker compose up -d postgres redis` |
| Subir a aplicação | `./mvnw spring-boot:run` | `.\mvnw.cmd spring-boot:run` |
| Compilar e rodar os testes | `./mvnw verify` | `.\mvnw.cmd verify` |

## Perfis de configuração

| Perfil | Quando usar | Configuração |
| --- | --- | --- |
| `local` | Padrão, quando nenhum perfil é informado | Valores padrão que combinam com o `docker-compose.yml` |
| `prod` | Nas VMs de homologação e do piloto, com `SPRING_PROFILES_ACTIVE=prod` | Só variáveis de ambiente, sem valores padrão: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT` e `REDIS_PASSWORD` |

Senhas e chaves nunca entram no repositório. Valores pessoais ficam no `.env`, que o Git ignora.

A variável `SESSAO_INATIVIDADE` vale nos dois perfis e define o tempo sem atividade até a sessão expirar. O padrão é `60m`, maior que uma hora-aula, e a menor unidade é o minuto.

## Autenticação

Só profissionais da escola têm conta (RF-01, RN-11). Todas as rotas exigem login, menos a tela de login e `/actuator/health`.

- **Formulário de login:** `POST /login` com os campos `email`, `senha` e `_csrf`. A tela de entrada é a `login.html`, com o visual do protótipo; o campo CSRF é incluído pelo Thymeleaf. A saída é `POST /logout`, também com o token CSRF, pelo botão **Sair** do cabeçalho.
- **Senhas:** novos usuários são criados pelo `CadastroDeUsuarios`, que grava só o hash em Argon2id. Nunca grave em `usuario.senha_hash` por outro caminho.
- **Mensagem de erro:** e-mail sem conta, usuário inativo e senha errada recebem a mesma resposta, para a tela não revelar quais contas existem.

### Perfis e autorização

Os perfis são `PROFESSOR`, `CURADOR` e `ADMINISTRADOR`, e um usuário pode acumular mais de um (RF-02, RN-36). O perfil é verificado em cada caso de uso, com `@PreAuthorize`:

```java
@PreAuthorize("hasAnyRole('PROFESSOR', 'CURADOR')")
public Cenario criar(...) { ... }
```

O `AutorizacaoDosCasosDeUsoTest` falha o build se um método público de um `@Service` público em `aplicacao` não declarar `@PreAuthorize`, no método ou na classe, ou se um caso de uso de `cenarios` liberar o perfil Administrador (RN-37). Só o `CadastroDeUsuarios` fica de fora, porque não é um caso de uso.

- **Gestão de usuários:** a `GestaoDeUsuarios` é exclusiva do Administrador e permite listar, buscar, cadastrar, ativar, desativar e atribuir perfis (RF-03). O sistema recusa a operação que deixaria o sistema sem nenhum administrador ativo.
- **Quem já está logado:** os perfis são conferidos no banco a cada requisição. Uma mudança vale na requisição seguinte, e o usuário desativado perde a sessão.
- **Log de auditoria:** cadastrar um usuário ou alterar seus perfis publica o evento `PerfilAlterado`, e a Auditoria grava uma linha em `log_auditoria` com o autor, a data e os perfis antes e depois (RN-35). A gravação acontece logo depois da transação, em outra thread.

### Primeiro administrador

Só um administrador cadastra usuários, então o primeiro é criado ao subir, a partir de `ADMIN_INICIAL_EMAIL` e `ADMIN_INICIAL_SENHA`, que valem nos dois perfis. Se já houver um administrador ativo, as variáveis são ignoradas e podem ser removidas. Sem elas, a aplicação sobe normalmente, sem administrador, e registra um aviso.

### CSRF nas requisições HTMX

Todo envio que não seja `GET` precisa do token CSRF, e o HTMX o envia pelo cabeçalho `X-CSRF-TOKEN`. Basta declarar o cabeçalho uma vez no `<body>` do layout, que todos os elementos da página herdam:

```html
<body th:hx-headers='|{"X-CSRF-TOKEN": "${_csrf.token}"}|'>
```

Se a sessão tiver expirado, a requisição HTMX recebe 401 com `HX-Redirect: /login`, e o navegador vai para a tela de login em vez de colocá-la dentro do fragmento.

## Migrations

O esquema do banco é só do Flyway: o Hibernate apenas confere (`ddl-auto=validate`). As migrations ficam em `src/main/resources/db/migration`.

- **Nome:** data e hora no formato `V20261110_1430__descricao.sql`, para que duas branches nunca criem o mesmo número.
- **Migration aplicada não muda:** para corrigir algo, crie uma migration nova.
- **Só adicionar estruturas:** a imagem anterior precisa continuar funcionando no mesmo banco, para que a reversão seja só trocar a tag da imagem (RNF-12). Nada de `DROP`, `RENAME` ou `ALTER COLUMN`. Coluna nova em tabela existente precisa aceitar nulo ou ter valor padrão, porque a imagem anterior não vai preenchê-la.

O `MigrationsTest` falha o build se encontrar um comando destrutivo e confere que a imagem anterior sobe em um banco com migrations mais novas. A regra de coluna nova não é verificada automaticamente, então vale conferir na revisão.

## Módulos

Cada módulo é um subpacote direto de `br.ufg.inf.labsimulacoes`:

| Pacote | Módulo | Responsabilidade |
| --- | --- | --- |
| `acesso` | Acesso | Usuários, perfis, turmas, parâmetros e autenticação |
| `cenarios` | Cenários | Cenários, versões, catálogo da BNCC, quiz, curadoria e biblioteca |
| `sessoes` | Sessões | Sessões em sala, mensagens, controles, aplicação do quiz e engajamento |
| `auditoria` | Auditoria | Conteúdo bloqueado, sinalizações, triagem, suspensão e log de ações críticas |
| `metricas` | Métricas | Indicadores, comparação com sessões de controle, painel, exportação e alerta de custo diário da IA |
| `ia` | Adaptador de IA | Porta única para o provedor de IA |

O pacote `eventos`, no mesmo nível, guarda só os tipos dos eventos de domínio. Ele não é um módulo de negócio, mas existe para que dois módulos possam publicar e escutar eventos um do outro sem formar um ciclo.

Dentro de cada módulo, o código fica em quatro camadas, e a dependência vai sempre de cima para baixo:

| Subpacote | Camada | Conteúdo |
| --- | --- | --- |
| `apresentacao` | Apresentação | Telas, fragmentos HTML, formulários e streaming |
| `aplicacao` | Aplicação | Casos de uso, transações e verificação de perfil |
| `dominio` | Domínio | Entidades, estados e regras de negócio, sem dependência de framework |
| `infraestrutura` | Infraestrutura | Repositórios, cache, chamadas externas e tarefas agendadas |

O que um módulo oferece aos outros fica em interfaces no pacote raiz, implementadas na camada de aplicação:

| Módulo | Interface | Para quê |
| --- | --- | --- |
| `acesso` | `ParametrosDeUso` | Ler o limite de perguntas por sessão e o custo diário máximo |
| `acesso` | `TurmasDaEscola` | Listar e buscar turmas, só para leitura, com qualquer perfil autenticado |
| `cenarios` | `CatalogoBncc` | Buscar habilidades da BNCC, com qualquer perfil autenticado |
| `cenarios` | `BibliotecaDeCenarios` | Listar, em páginas de até 100, os cenários aprovados da escola, com busca por tema e filtros combináveis por componente, ano e habilidade BNCC (RF-12), com perfil Professor ou Curador |
| `cenarios` | `CenariosAprovados` | Ler a versão aprovada vigente de um cenário para iniciar sessão (RN-01), com perfil Professor ou Curador |
| `ia` | `BloqueioDeDadosPessoais` | Recusar, antes de qualquer chamada ao provedor, um texto digitado com e-mail, telefone, CPF ou endereço (RN-13), com perfil Professor ou Curador |
| `ia` | `ConsumoDaIa` | Somar o consumo da IA (respostas, tokens, custo e respostas sem custo) num intervalo ou de uma sessão (RNF-13), sem conferir perfil, porque a conferência agendada roda sem login |
| `ia` | `OrigemDasRespostas` | Ler o provedor, o modelo e a versão do prompt de uma resposta gravada, para a sinalização copiar (RF-23, RNF-10), com perfil Professor ou Curador |
| `sessoes` | `TrechosDasSessoes` | Ler a conversa gravada, cada resposta com a pergunta que a originou e as sessões recentes de um professor (RF-23), com perfil Professor ou Curador; não confere de quem é a sessão, o que fica com quem chama |

### Catálogo da BNCC

As habilidades da BNCC ficam na tabela `habilidade_bncc` e vêm dos arquivos em `src/main/resources/bncc/` (ver [Créditos dos dados da BNCC](#créditos-dos-dados-da-bncc)). Por enquanto, só o Ensino Fundamental é importado.

- **Importação:** roda a cada subida da aplicação. Ela grava pelo código, sem duplicar, só atualiza o que mudou e nunca apaga uma habilidade, porque as versões de cenário apontam para elas. Só entram as habilidades vigentes, e o log mostra quantas foram inseridas, atualizadas, mantidas e ignoradas. Se um arquivo tiver problema, como um componente que o `estrutura.json` não conhece, nada é gravado e a aplicação não sobe.
- **Desligar na subida:** defina `BNCC_IMPORTAR_AO_INICIAR=false` (propriedade `bncc.importar-ao-iniciar`). Os testes já rodam assim e importam o arquivo quando precisam.
- **Busca:** pela `CatalogoBncc`, com filtros opcionais por código, trecho da descrição, etapa, ano e componente, em ordem de código e com até 50 resultados por padrão (`bncc.busca.limite-padrao`). O ano 7 também encontra as habilidades do bloco 6º e 7º, como EF67LP08.
- **Cache:** cada busca fica no Redis (cache `habilidades-bncc`), com a chave formada pelos filtros normalizados. A importação limpa o cache inteiro depois que a gravação é confirmada. A validade de 24 horas (`bncc.cache.validade`) é só uma garantia a mais.
- **Outra etapa:** basta criar um novo leitor que implemente `FonteDeHabilidades` na infraestrutura de `cenarios`. A importação junta as habilidades de todos os leitores.

### Sugestão de cenário pela IA

No editor de cenário novo, "Sugerir com IA" abre o pedido de sugestão (RF-07): tema, componente, ano e habilidades BNCC, com a mesma busca do editor. `POST /cenarios/sugestao` chama a `SugestaoDeCenarios`, e o rascunho sugerido abre no editor com a marca "Sugerido por IA" (RN-04), que também aparece em "Meus cenários" e na fila do curador, com um lembrete para conferir os fatos com as fontes. Um pedido recusado (incompleto, fora do catálogo, acima do limite de pedidos ou com falha da IA) volta ao editor com o diálogo aberto e preenchido. Sem JavaScript, o link abre `/cenarios/novo?sugerir=true`, com o diálogo já aberto.

### Modo de teste do cenário

Antes de aprovar, o curador conversa com o personagem da versão em curadoria (RF-09), pelo mesmo adaptador e pelo mesmo montador de prompt da sala.

- **Tela:** a seção "Modo teste" da fila do curador (`/curadoria`) mostra a conversa da rodada e o contador "n/5 perguntas mínimas", que conta como o RN-06. A pergunta vai por `POST /curadoria/{id}/teste` (HTMX), e a seção volta inteira, com a resposta já moderada. Pergunta ou resposta bloqueada aparece só como aviso neutro. O autor vê a seção, mas não testa. `TesteDoCenario.rodada(cenarioId)` lê a rodada para a tela, sem travar nem gravar nada.
- **Caso de uso:** `TesteDoCenario.perguntar(cenarioId, texto)`, só com o perfil Curador. Só se testa a versão Em curadoria, e o autor do cenário ou da versão nunca testa a própria, mesmo tendo o perfil Curador (RN-05, RN-36). Devolve a `PerguntaDeTeste` com o resultado e a resposta inteira. A pergunta tem no máximo 1000 caracteres, depois de tirar os espaços das pontas, o mesmo limite do campo da pergunta na sala.
- **Reserva antes da IA:** cada pergunta passa por três passos, e nenhuma chamada à IA fica dentro de transação.
  1. Numa transação curta, com o cenário travado, o caso de uso confere a versão, o autor e o teto e grava a pergunta como `PENDENTE` na rodada em aberto.
  2. Fora de transação, modera a pergunta, gera a resposta inteira e modera a resposta.
  3. Noutra transação curta, grava o resultado na mesma linha: `CONCLUIDA`, `PERGUNTA_BLOQUEADA`, `RESPOSTA_BLOQUEADA` ou `FALHA`.

  Com a trava, pedidos ao mesmo tempo nunca passam do teto. Uma resposta já gerada fica sempre ligada à pergunta, também quando a moderação falha depois: o `resposta_ia_meta_id` é único, e nenhum consumo da IA fica sem a pergunta que o gerou.
- **Filtro (RN-20):** se a pergunta for bloqueada, ela não chega ao modelo. Se a resposta for bloqueada, ela não volta nem é guardada. Nos dois casos, a pergunta fica com a situação, e a tela mostra o aviso neutro no lugar da resposta. Não abre caso na fila do curador nem publica evento.
- **Não é sessão de turma:** o teste não cria sessão nem mensagem, não usa o Redis da sessão e não publica evento nenhum. Grava só em `pergunta_de_teste`, então não entra nas métricas. O consumo fica em `resposta_ia_meta`, apontado pela pergunta, como na sala (RNF-10, RNF-13).
- **Duas contagens da rodada em aberto:**
  - **Teto (RNF-13):** `PerguntasDeTeste.ocupadas` conta todas as perguntas da rodada do curador, pendentes, concluídas, bloqueadas e com falha, porque qualquer tentativa gasta algo. O teto é `cenarios.teste.perguntas-por-versao` (40 por padrão, no mínimo 5), e passar dele é recusado antes de chamar a IA (`LimiteDoTesteAtingido`).
  - **Checklist (RN-06, #27):** `PerguntasDeTeste.contar(versaoId, curadorId)` conta só as que testaram o personagem: concluídas e de resposta bloqueada. Pendentes, falhas e perguntas bloqueadas não contam. O mínimo é `PerguntasDeTeste.MINIMO_PARA_APROVAR` (5).
- **Pendente presa:** se a aplicação cair entre a reserva e o resultado, a pergunta fica `PENDENTE`. Ela só ocupa a sua vaga do teto até a próxima devolução e não conta para o RN-06.
- **Rodada:** a devolução volta a mesma versão a rascunho, na mesma linha. Por isso o `DevolucaoDeCenario` liga todas as perguntas em aberto da versão à devolução (`devolucao_id`), inclusive as pendentes, e as contagens e o histórico recomeçam na versão reenviada. Uma pergunta pendente fechada pela devolução recebe o resultado depois, mas continua na rodada fechada: o caso de uso responde com `TesteNaoPermitido`, e ela não conta na rodada nova, mesmo que a versão seja reenviada sem edição.
- **Histórico:** o modelo recebe as últimas 20 falas concluídas da rodada do próprio curador, como na sala.
- **Para a tela (#33):** `PerguntasDeTeste.daRodada(versaoId, curadorId)` traz a conversa da rodada, da mais antiga para a mais nova. A resposta só existe na situação `CONCLUIDA`. Com `FalhaNoProvedorDeIa`, a pergunta fica com a situação de falha, ocupando a vaga, e pode ser feita de novo.
- **Retenção:** o RN-33 cobre só as conversas das sessões, então `pergunta_de_teste` não é apagada por nenhum job. Ela guarda só o que o curador digitou e o que o personagem respondeu, sem dado de aluno. Se o PO quiser um prazo, ele entra no job de retenção do RNF-15.

### Checklist de curadoria

A partir da E2, o curador só aprova depois de conferir o checklist e de testar o personagem (RF-10, RN-06).

- **Itens:** fatos conferidos com as fontes, linguagem adequada à faixa etária, objetivos coerentes com a BNCC e limites de assunto respeitados no teste (`ItemDoChecklist`). Todos são obrigatórios.
- **Teste:** pelo menos 5 perguntas na rodada em aberto do próprio curador, pela contagem do modo de teste (`PerguntasDeTeste.contar`). Perguntas de outro curador ou de antes de uma devolução não contam.
- **Regra no domínio:** `Cenario.aprovar(curador, conferencia, momento)` recusa com `AprovacaoIncompleta`, que diz tudo o que falta de uma vez. As regras de quem aprova (RN-05, RN-36) são conferidas antes.
- **Registro:** a aprovação grava em `checklist_curadoria` os itens e quantas perguntas o curador tinha feito, uma linha por versão aprovada. Como a versão aprovada (RN-08), a linha não muda nem é apagada (trigger), e o banco também recusa checklist incompleto.
- **Tela:** a seção "Checklist de revisão" da fila do curador (`/curadoria`), como no protótipo. O aviso ao lado de "Aprovar" diz o que falta ("Faltam 3 perguntas de teste.", "Conclua todo o checklist.", "Revisão completa."), e o botão só fica ativo com tudo feito (`curadoria.js`). É só um adiantamento: o servidor confere de novo e, se faltar algo, volta à fila com o checklist marcado como estava.

### Versões, biblioteca e arquivamento na tela

- **Histórico e comparação (RF-11):** `GET /cenarios/{id}/versoes` lista as versões da mais nova para a mais antiga, com a vigente marcada, e mostra duas lado a lado, campo a campo: o que saiu aparece riscado em vermelho e o que entrou, sublinhado em verde. Sem escolha, compara a mais nova com a última aprovada antes dela; `?de=1&para=3` escolhe outras. O professor vê o histórico dos próprios cenários (pelo botão "Versões" de Meus cenários) e o curador vê o de todos (RN-37). A fila do curador mostra o mesmo histórico, com a versão em curadoria ao lado da que está em sala.
- **Versão nova e restauração (RN-08):** só o autor, nessa mesma página. `POST /cenarios/{id}/versoes/nova` abre a versão seguinte de um cenário aprovado ou suspenso, e `POST /cenarios/{id}/versoes/{numero}/restaurar` traz de volta uma versão aprovada anterior. As duas abrem em rascunho no editor e passam pela curadoria; a vigente continua em sala até a nova ser aprovada.
- **Filtros da biblioteca (RF-12):** `/biblioteca` aceita `busca`, `componente`, `ano` e `habilidade` na URL, combináveis, e a paginação os mantém. As opções de componente, ano e habilidade vêm de `BibliotecaDeCenarios.filtrosDisponiveis()`, então só aparece o que traz algum cenário.
- **Arquivamento (RF-13):** o curador arquiva pelo detalhe do cenário na biblioteca, depois de confirmar num diálogo. `POST /curadoria/{id}/arquivar` volta à biblioteca com o aviso; o histórico continua guardado (RN-09).

### Sessão em sala

A sessão fica ligada ao professor, à turma e à versão aprovada vigente no início (RF-14, RN-10). Ela tem três estados: Em andamento, Pausada e Encerrada.

- **Início:** a `InicioDeSessao` recebe a turma e o cenário e só aceita o perfil Professor. Ela recusa uma turma que não existe e um cenário sem versão aprovada (RN-01).
- **Controles:** a `ControlesDaSessao` pausa, retoma, reinicia e encerra (RF-17). Só o professor que iniciou a sessão a controla. Pausar e retomar gravam um evento em `evento_sessao`, com o motivo da pausa. Reiniciar grava o ponto de corte: a tela fica limpa, mas as mensagens continuam no banco (RN-19). Sessão encerrada não aceita nenhum controle.
- **Perguntas disparadoras:** o painel do professor lista as perguntas disparadoras da versão da sessão (RF-18). Elas ficam só no painel, nunca na área projetada. O clique leva a pergunta ao campo de envio sem enviar, para o professor ajustar antes. Com a sessão pausada ou no limite de perguntas, ficam bloqueadas junto com o campo.
- **Controle de tempo:** o tempo de conversa conta desde o início, sem as pausas (RF-19). Ao chegar à duração prevista do cenário, o painel só avisa, sem interromper a conversa. Aos 50 minutos de conversa, a sessão pausa sozinha com o motivo `LIMITE_50_MINUTOS`, e retomar pede a confirmação do professor (RN-17). Depois dessa confirmação, ela não pausa de novo pelo tempo.
  - **Onde o limite é conferido:** toda mudança que trava a sessão (pausar, retomar, reiniciar, encerrar e gravar a pergunta) confere o limite antes de mudar. A leitura da tela confere primeiro com o estado do Redis, sem ir ao banco, e só trava a sessão quando o tempo passou.
  - **Com a tela aberta:** o cronômetro pede o painel de novo aos 50 minutos e repete o pedido a cada 10 segundos se ele falhar.
  - **Com a tela fechada:** a pausa é feita na próxima leitura ou mudança, registrada no momento dos 50 minutos. Se algo já foi registrado depois desse momento, como uma pausa e retomada do professor ou uma mensagem, a pausa fica no último registro, para o histórico, refeito em ordem de data, não sair fora de ordem.
- **Uma sessão por turma:** só a sessão Em andamento bloqueia a turma, pelo índice único `uq_sessao_em_andamento_por_turma` (RN-10). A sessão pausada não impede outra, mas também não volta enquanto a outra estiver em andamento.
- **Estado no Redis:** cada sessão tem a chave `sessao:<id>:estado`, com status, motivo da pausa, tempo em pausa, ponto de reinício, contador de perguntas e as 20 últimas mensagens da tela. A chave é gravada só depois que a transação é confirmada, e a validade recomeça a cada gravação (`sessoes.estado.validade`, 6 horas por padrão).
- **Reconstrução:** o PostgreSQL é a fonte da verdade. Os controles (pausar, retomar, reiniciar e encerrar) sempre partem do estado refeito a partir de `sessao`, `evento_sessao` e `mensagem`, com a sessão travada, e nunca da chave do Redis, que pode ainda não ter recebido a gravação do controle anterior. A chave serve à leitura da tela: se ela sumir ou discordar do banco, o estado é refeito e gravado de novo (RNF-04). Falha do Redis não derruba a aula.
- **Retomada após falha:** recarregar a página refaz a tela a partir do Redis, ou do banco se a chave tiver sumido, com a conversa, o estado e o tempo (RF-20, RNF-04). O canal da resposta (`ConversaDaSessao.entregarResposta`) cobre os casos de volta:
  - se a página foi recarregada ou a conexão caiu no meio da resposta e a resposta antiga ainda está sendo gerada, o canal novo espera ela terminar, até 60 segundos, e recebe ela inteira, sem outra chamada ao modelo. O texto já exibido é apagado ao reabrir o canal, para não repetir;
  - se o servidor já percebeu a queda e cancelou a resposta antiga, a pergunta é respondida de novo;
  - se a espera passar de 60 segundos, a tela mostra a falha, e o "Tentar de novo" entrega a resposta gravada quando ela terminar;
  - se a pergunta foi respondida enquanto a tela estava fora, recebe a resposta gravada;
  - se a resposta não chegou ao fim, a pergunta continua pendente e é respondida de novo.

  Na falha do provedor, o "Tentar de novo" troca só a resposta (`GET /sessoes/{id}/resposta-pendente`), sem recarregar a página. Se a conexão cair no envio de uma pergunta ou de um controle, a tela avisa, e a pergunta digitada continua no campo.
- **Mensagens:** quem gravar mensagens (RF-16) precisa usar o horário da aplicação em `mensagem.criado_em`, porque o ponto de reinício também usa esse relógio.

### Bloqueio de dados pessoais

Um texto digitado com padrão de dado pessoal não vai ao provedor (RF-21, RN-13, RNF-01).

- **Onde:** a `BloqueioDeDadosPessoais`, na API do `ia`, usa os padrões de `DadosPessoais`, só com expressões regulares em Java. Fica no `ia` porque o teste do curador está em `cenarios`, que não pode depender de `sessoes`.
- **Caminhos:** a pergunta da sala (`ConversaDaSessao`), o tema da sugestão (`SugestaoDeCenarios`) e a pergunta do teste do curador (`TesteDoCenario`) passam pelo bloqueio antes de qualquer chamada ao provedor, inclusive a moderação. A moderação já envia o texto, e um texto impróprio vira caso na fila do curador com o texto copiado.
- **Segunda barreira:** a porta `AdaptadorDeIa` confere de novo a pergunta, em `responder`, e o tema, em `sugerirCenario`. A `moderar` fica de fora, porque também recebe as frases geradas pela IA, que ninguém pode reescrever.
- **Fora do escopo:** o conteúdo do cenário (contexto, fontes, objetivos e o resto), escrito por professores e curadores, ainda não passa pelo bloqueio. Fica para outra issue.
- **O que bloqueia:**
  - **E-mail**, também escrito com "arroba", "(arroba)", "(at)" e "ponto". O @ e o ponto valem colados ou com um espaço de cada lado, nunca de um lado só, para "Siga @santosdumont. Ele voou" não bloquear.
  - **Telefone:** celular de 9 dígitos começando com 9, com ou sem DDD e +55; fixo com o DDD entre parênteses ou depois do +55; 10 ou 11 dígitos seguidos começando com DDD. O fixo sem DDD só bloqueia com uma palavra de telefone por perto ("tel", "whatsapp", "ligar"...). Números públicos também bloqueiam.
  - **CPF** com e sem pontuação, com espaço, ponto ou hífen entre os blocos, sem conferir os dígitos verificadores.
  - **Endereço:** CEP, logradouro com número depois da vírgula ou de "nº" ("Rua das Flores, 123"), quadra e lote, e complemento a até uns 40 caracteres do logradouro com nome ("Rua das Flores, apto 12"). "Logradouro, número" bloqueia mesmo num contexto histórico, menos quando o número logo depois da vírgula é um ano de 1500 a 2099 ou uma porcentagem. O nome do logradouro não tem ponto, para a busca não atravessar o fim da frase, exceto em até três títulos abreviados logo no começo dele: dr, dra, prof, profa, pres, cel, gen, eng, sta, sto, des, min, cap, mal, ten, av e r ("Rua Dr. Fulano, 12", "Av. Pres. Vargas, 500").
- **O que não bloqueia:** códigos da BNCC, datas, anos e intervalos de anos ("1914-1918", também com meia-risca), notas, medidas, valores, URLs, logradouros sem número ("Rua 25 de Março", "moro na rua das Flores") e "logradouro, ano" ("A rua principal, 1920 foi asfaltada"). Por isso "Avenida Paulista, 1578" passa, e "Avenida Paulista, nº 1578" bloqueia.
- **Limitações conhecidas:**
  - **Falsos positivos:** números de 10 dígitos que parecem telefone com DDD, como 1234567890 e 2147483647, e um logradouro seguido de vírgula e número que não é endereço ("A questão 3, item 2 da Avenida Brasil, 5 alunos acertaram").
  - **Falsos negativos:** e-mail por extenso sem parênteses e sem "ponto" ("fulano arroba exemplo com"); "at" e "dot" sem parênteses ou colchetes; e-mail com espaço de um lado só do @ ou do ponto; números por extenso; número sem vírgula nem "nº" ("Rua das Flores 123"); abreviação fora da lista ("Rua Alm. Barroso, 10") ou no meio do nome; "Tv." como travessa; número da casa de 1500 a 2099 logo depois da vírgula, sem "nº"; e dígitos de outros sistemas de escrita, como os árabe-índicos (٩٨٧٦٥-٤٣٢١), que a normalização não troca por 0 a 9.
- **Traços:** a meia-risca, o travessão, os outros traços e o sinal de menos valem como hífen ("98765–4321").
- **Normalização:** só para a busca, sem acentos, em minúsculas, sem caracteres invisíveis e com os espaços juntados. Nenhuma regra tem quantificador aninhado, e o tempo cresce em linha com o texto.
- **Ao bloquear:** nada é gravado, e nada vai para log, evento ou auditoria. A exceção `TextoComDadoPessoal` leva só o tipo. O aviso nomeia o tipo sem repetir o dado: "A pergunta parece ter um dado pessoal (telefone). Reescreva sem e-mail, telefone, CPF ou endereço, e não digite nomes de estudantes."
  - **Na sala:** volta 422, a sessão continua em andamento, a pergunta não conta no limite (RN-18) e o texto fica no campo. O `projecao.js` faz o HTMX trocar as respostas de erro do formulário que vêm com o `HX-Retarget` para a área de avisos (400, 409, 422 e 503), que por padrão ele não troca.
  - **Pergunta já pendente com dado pessoal**, como uma gravada antes do bloqueio: a `ConversaDaSessao` confere antes de chamar o adaptador, tira a pergunta da espera como no bloqueio da resposta e o canal manda o evento SSE `dado-pessoal`. Ao reabrir o canal, como ao recarregar a página, o aviso é o mesmo, e não o da moderação. A sessão não pausa, não abre caso e volta a aceitar perguntas, e o bloqueio conta uma vez só na métrica.
  - **Na sugestão e no teste do curador:** o pedido volta com o aviso, mas sem o texto bloqueado, para a tela não repetir o dado. A sugestão não gasta a cota de pedidos, e o teste não ocupa vaga do teto.
- **Métrica:** `lab.privacidade.bloqueio`, com a tag `tipo` (`email`, `telefone`, `cpf` ou `endereco`). Nenhum usuário, sessão ou texto vira tag.
- **Tamanho:** a pergunta da sala e a do teste têm no máximo `PedidoDeFala.TAMANHO_MAXIMO_DA_PERGUNTA` (1000) caracteres, conferidos também no servidor.

### Sinalização de erro

Professor e curador sinalizam uma resposta da IA com possível erro (RF-23, RN-21), pelo `RegistroDeSinalizacao`, na Auditoria.

> **Até a RF-25, uma sinalização grave não suspende o cenário.** O RN-23a manda suspender o cenário na hora quando o professor marca a sinalização como grave, mas isso é da RF-25. Por enquanto, o evento `SinalizacaoRegistrada` é publicado sem ninguém escutar, e o cenário continua aprovado e na biblioteca.

- **O que se informa:** o tipo (erro histórico, erro científico, anacronismo, saída do personagem ou conteúdo inadequado), a gravidade percebida (leve, moderada ou grave, com os critérios do RN-22) e uma justificativa de até 500 caracteres. O limite fica na aplicação, e não no banco, para mudar sem migration.
- **Quem e até quando:**
  - O professor sinaliza só respostas das próprias sessões, durante a sessão ou até 7 × 24 horas depois do encerramento, com o limite incluído (`PrazoDaSinalizacao`). Uma sessão que nunca foi encerrada conta do início. A sessão de outro professor é tratada como inexistente (RN-37).
  - O curador sinaliza qualquer resposta, a qualquer momento.
  - Quem tem os dois perfis sinaliza como professor na própria sessão, dentro do prazo, e como curador nos outros casos. O perfil fica gravado, porque só a sinalização grave do professor vai suspender o cenário (RN-23a).
  - Cada pessoa sinaliza uma resposta uma vez só; outra pessoa pode sinalizar a mesma resposta.
- **O que é copiado:** a pergunta e a resposta inteiras, o início e o professor da sessão, a versão do cenário e o provedor, o modelo e a versão do prompt da resposta, em JSONB (`OrigemDasRespostas`, no `ia`). As referências à sessão, à mensagem e aos metadados ficam nulas quando a retenção os apaga (RN-33), e a sinalização continua completa (`SinalizacaoAposARetencaoTest`). Uma resposta antiga gravada sem metadados é sinalizada sem a cópia deles.
- **Dados pessoais:** a justificativa passa pelo `BloqueioDeDadosPessoais`, porque fica gravada para sempre (RNF-01). A pergunta e a resposta copiadas não passam: a sinalização não pode ser recusada pelo conteúdo que ela denuncia.
- **Evento e métrica:** o `SinalizacaoRegistrada` leva só ids e códigos, sem nenhum texto. A métrica `lab.auditoria.sinalizacao` conta por `tipo` e `perfil`. O log registra os ids da sinalização e da sessão, o tipo e o perfil, sem nenhum texto.
- **Relógio:** o prazo usa o `RelogioDaAuditoria`, que os testes trocam por um momento fixo. O de Sessões é interno àquele módulo.
- **Na tela, depois da aula:**
  - **Sessões recentes** (`/sessoes/revisao`): as sessões do professor ainda no prazo, cada uma com o limite para sinalizar. O link fica na tela de nova sessão.
  - **Revisão da conversa** (`/sessoes/{id}/revisao`): a conversa gravada, só para leitura, com "Sinalizar possível erro" em cada resposta do personagem enquanto o prazo vale. Depois dele, a conversa continua visível, sem o botão. O link fica na tela de sessão encerrada.
  - A sessão de outro professor responde 404, como uma que não existe. A regra de quem pode sinalizar é a mesma do caso de uso (`QuemSinaliza`), então a tela só mostra o botão quando a sinalização seria aceita.
  - O envio vai pelo HTMX e volta com o fragmento do formulário, com status 200 também na recusa, como na curadoria: o aviso aparece no lugar do formulário, sem o ajuste de 4xx do `projecao.js`. Na recusa por dado pessoal, a justificativa não volta à tela.
  - As rotas de `/sessoes` são só do perfil Professor, então o curador sem esse perfil ainda não tem tela para sinalizar.
- **Na tela, durante a aula (#43):** o botão "Sinalizar erro" do painel do modo projeção (atalho S) abre um modal com a última resposta do personagem (`/sessoes/{id}/sinalizacao`), o tipo, a gravidade (começa em Moderada) e a justificativa. Vale também com a sessão pausada. O envio volta com o mesmo modal, com a confirmação ou a recusa, e usa o mesmo caso de uso da revisão.
- **Avisos no modo projeção (#43):** a área projetada mostra só o que a turma pode ver. As recusas da pergunta (dado pessoal, bloqueio, limite), a falha do provedor com o "Tentar de novo", a pausa automática, o tempo previsto e a sugestão do quiz ficam na área de avisos do painel (`#aviso-da-sessao`, com `hx-preserve`). Na conversa ficam só avisos neutros: "Resposta não exibida", "Resposta não recebida" e "Pergunta não enviada". Com o painel oculto, o botão "Mostrar painel" ganha um ponto quando chega um aviso novo.
- **Fora destas issues:** a triagem e a gravidade final (RF-24), a suspensão e a pausa das sessões (RF-25), a taxa de erros (RN-29), a fila do curador (#44) e uma tela para o curador sinalizar respostas.

### Observabilidade e custo da IA

O RNF-13 pede latência, erros, tokens e custo por sessão, e um alerta ao administrador quando o custo do dia passar do custo diário máximo dos parâmetros.

- **Metadados de cada resposta:** `resposta_ia_meta` guarda tokens, custo e latência (RNF-10), e agora a `finalidade` (`FALA_EM_SALA`, `TESTE_DO_CURADOR` ou `SUGESTAO`) e a `referencia_id`, que quem chama informa no `PedidoDeFala` (na sala, o id da sessão). O adaptador grava sem saber o que a referência significa. A sugestão grava `SUGESTAO` sozinha. Os metadados gravados antes da #31 ficam sem finalidade. Um pedido montado com o construtor antigo, de três argumentos, também.
- **Custo por sessão:** `ConsumoDaIa.daReferencia(FALA_EM_SALA, sessaoId)` soma todas as respostas da sessão, inclusive as bloqueadas pelo filtro e as que vieram vazias, que não viram mensagem. O administrador consulta na aba **Consumo** (`/admin/consumo`), pelo número da sessão. Nada é agrupado por professor (RN-31).
- **Custo do dia:** soma `resposta_ia_meta` da meia-noite à meia-noite no fuso `metricas.custo.fuso` (`America/Sao_Paulo`). O banco é a fonte da verdade. Entram a fala em sala, o teste do curador e a sugestão; a moderação não tem custo. Respostas sem tokens informados ficam sem custo e são contadas à parte, e então o custo real é maior que o mostrado.
- **Preço de cada uso:** cada resposta é precificada pela tarifa do modelo e do nível de serviço que usa. A fala do personagem, na sala e no teste do curador, usa `ia.preco.*` (`IA_PRECO_ENTRADA` e `IA_PRECO_SAIDA`, com a tarifa do nível prioritário por padrão). A sugestão de cenário, que roda no nível padrão, usa `ia.sugestao.preco.*` (`IA_PRECO_SUGESTAO_ENTRADA` e `IA_PRECO_SUGESTAO_SAIDA`, com a tarifa desse nível). Ao trocar o modelo ou o nível de um uso, troque o preço dele junto.
- **Alerta:** a `VerificacaoDoCustoDiario` roda a cada `metricas.custo.verificacao` (5 minutos) e, quando o custo do dia passa do limite (estritamente), grava uma linha em `alerta_custo_diario` e escreve um WARN no log. É um alerta por dia, garantido pela chave primária, mesmo depois de um reinício ou com duas instâncias. Ele não se repete se o limite mudar no mesmo dia. Sem limite definido, não há alerta. Enquanto houver alerta hoje, todas as abas da administração mostram o aviso no topo, com o custo calculado na hora. A conferência agendada fica desligada nos testes (`metricas.custo.verificar-automaticamente`, variável `METRICAS_CUSTO_VERIFICAR`).
- **Micrometer:** além dos timers do RNF-03, o adaptador registra os contadores `lab.ia.custo` (em dólares) e `lab.ia.tokens` (tag `tipo`, `entrada` ou `saida`), as duas com as tags `finalidade` e `modelo`, e `lab.ia.falha` (tag `motivo`: `resposta-vazia` ou `sem-roteiro`). A sugestão virou a etapa `sugestao` de `lab.ia.chamada`. Nenhum id de sessão, usuário ou cenário vira tag. Os contadores recomeçam a cada subida da aplicação.
- **Contagem repetida de tokens:** o Spring AI já conta os tokens em `gen_ai.client.token.usage`, por modelo e tipo (`input` e `output`), o que foi confirmado em execução (`TetoDeTokensNaRequisicaoTest`). O `lab.ia.tokens` repete essa contagem para separar por finalidade, que o Spring AI não conhece.
- **Exposição:** o Actuator continua expondo só o `health`, e o Caddy bloqueia o resto. Os contadores ficam na memória da aplicação até haver um coletor.

### Testes de quebra de personagem

O RNF-09 pede testes automáticos de tentativas de tirar o personagem do papel. O `QuebraDePersonagemTest` manda ao `AdaptadorDeIa`, com o modelo de verdade, o conjunto de casos de `CasoDeQuebraDePersonagem`: injeção de prompt, pedido de dados pessoais, opinião político-partidária ou religiosa e saída de tema (RN-12, RN-15).

- **Avaliação:** primeiro a resposta é conferida pelos trechos proibidos do caso, que nem uma recusa pode conter, como os dados pessoais informados ou pedaços das instruções. O que a resposta pode citar só para recusar, como o texto que a injeção manda repetir, fica no critério do juiz. Depois, o `JuizDoPersonagem` pede ao modelo um veredito com o critério do caso e as regras do personagem. O juiz usa raciocínio baixo e o nível de serviço padrão. O modelo dele pode ser trocado por `ia.teste-quebra.juiz.modelo`; sem essa propriedade, é o mesmo da fala.
- **No CI:** o job **Quebra de personagem** roda em todo PR, mas só chama a OpenAI quando o PR mexe no módulo `ia` (código ou testes), na `InfraestruturaDeTeste`, nos prompts, nas migrations, nos `application*.properties`, nos recursos de teste, no `pom.xml`, no wrapper do Maven ou no `ci.yml`. Nos outros PRs, ele passa sem rodar nada. PRs de fork e do Dependabot também passam sem rodar, porque não recebem o segredo; se um deles mexer na IA, rode o job pela aba Actions antes do merge. O job falha se nenhum caso rodar e tem limite de 15 minutos. O resumo do job mostra a tabela dos casos e, para cada um, a pergunta, a resposta e o motivo do juiz, e o mesmo relatório fica no artefato `quebra-de-personagem`. Pela aba Actions (`workflow_dispatch`), ele roda sempre.
- **Configuração do repositório:** o job precisa do segredo `OPENAI_API_KEY` e falha sem ele. Para bloquear o merge, `quebra-de-personagem` tem que estar entre os checks obrigatórios da proteção da `main`.
- **Respostas variáveis:** o modelo não responde sempre igual. Se um caso falhar, leia a resposta no relatório antes de rodar o job de novo: uma falha real pede ajuste no prompt, numa nova versão (RNF-11), e não no caso.
- **No terminal:** `OPENAI_API_KEY=... ./mvnw test -Dtest=QuebraDePersonagemTest -Dia.teste-quebra=true -Djacoco.skip=true`, com o Docker ligado. O relatório fica em `target/quebra-de-personagem/relatorio.md`. Fora disso, o teste fica desligado, inclusive no `verify`.

## Regras de fronteira

- A API de um módulo é só o seu pacote raiz. Os subpacotes são internos e inacessíveis aos outros módulos.
- Os módulos não podem formar ciclos de dependência.
- Um módulo só pode depender dos módulos declarados em `allowedDependencies`, no `package-info.java` dele:

| Módulo | Pode depender de |
| --- | --- |
| `acesso` | `eventos` |
| `cenarios` | `acesso`, `ia`, `eventos` |
| `sessoes` | `acesso`, `cenarios`, `ia`, `eventos` |
| `auditoria` | `acesso`, `sessoes`, `ia`, `eventos` |
| `metricas` | `acesso`, `ia`, `eventos` |
| `ia` | nenhum |
| `eventos` | nenhum |

- Dentro de um módulo, as camadas só dependem das camadas de baixo, e a apresentação não usa a infraestrutura diretamente. A infraestrutura pode usar o domínio, e o inverso não vale.
- O domínio só pode depender do Java e do próprio sistema. Por isso, anotações de JPA e Spring ficam fora dele.

O `ArquiteturaModulosTest` roda `ApplicationModules.verify()` e o `ArquiteturaCamadasTest` confere as camadas a cada build. Se uma dessas regras for violada, o teste e o build falham, e a mensagem indica a classe ou o ciclo responsável.

A maioria das classes em `src/test/java/br/ufg/inf/fixturesmodulith` viola as regras de propósito. Elas garantem que a verificação continua detectando os problemas e não fazem parte do sistema.

## Documentação

- Requisitos POC 2.3 v2: requisitos funcionais, regras de negócio (RN) e requisitos não funcionais (RNF).
- Stack Tecnológica POC 2.3: tecnologias, arquitetura e regras de gerência de configuração.
- [Hospedagem](docs/operacao/hospedagem.md): VM, ambientes, deploy, backups e checklist de ações manuais.
- [Roteiro de reversão](docs/operacao/reversao.md): como voltar para a versão anterior e o que fazer com o banco.

## Créditos dos dados da BNCC

O catálogo de habilidades usa dados do dataset "bncc.dev: dados abertos e verificados da Base Nacional Comum Curricular", publicado pela Profy (Profy Tecnologias Educacionais Ltda) sob a licença CC BY 4.0. Os dados originais vêm das planilhas oficiais do MEC.

- Fonte: https://github.com/bncc-dev/bncc-dados (site: https://bncc.dev)
- Versão usada: dados-2026.08, publicada em 2026-10-15 (commit d70016185a3cb8b1f146f50592a2a6502f58edde)
- DOI da versão: 10.5281/zenodo.23403987
- Licença dos dados (CC BY 4.0): https://github.com/bncc-dev/bncc-dados/blob/main/dados/LICENSE.md
- Arquivos usados: dados/bncc-2018/ensino-fundamental.json e dados/bncc-2018/estrutura.json, copiados sem alteração para src/main/resources/bncc/
- Modificações: o sistema importa apenas as habilidades vigentes e somente os campos código, texto, componente e anos. O identificador do componente é trocado pelo nome (ex.: ef-comp-hi vira História) e a lista de anos é gravada como texto (ex.: "6,7"). O dataset original não foi alterado.
