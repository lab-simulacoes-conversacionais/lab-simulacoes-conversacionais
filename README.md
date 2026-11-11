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
| `metricas` | Métricas | Indicadores, comparação com sessões de controle, painel e exportação |
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
| `cenarios` | `BibliotecaDeCenarios` | Listar, em páginas de até 100, os cenários aprovados da escola (RF-12), com perfil Professor ou Curador |
| `cenarios` | `CenariosAprovados` | Ler a versão aprovada vigente de um cenário para iniciar sessão (RN-01), com perfil Professor ou Curador |

### Catálogo da BNCC

As habilidades da BNCC ficam na tabela `habilidade_bncc` e vêm dos arquivos em `src/main/resources/bncc/` (ver [Créditos dos dados da BNCC](#créditos-dos-dados-da-bncc)). Por enquanto, só o Ensino Fundamental é importado.

- **Importação:** roda a cada subida da aplicação. Ela grava pelo código, sem duplicar, só atualiza o que mudou e nunca apaga uma habilidade, porque as versões de cenário apontam para elas. Só entram as habilidades vigentes, e o log mostra quantas foram inseridas, atualizadas, mantidas e ignoradas. Se um arquivo tiver problema, como um componente que o `estrutura.json` não conhece, nada é gravado e a aplicação não sobe.
- **Desligar na subida:** defina `BNCC_IMPORTAR_AO_INICIAR=false` (propriedade `bncc.importar-ao-iniciar`). Os testes já rodam assim e importam o arquivo quando precisam.
- **Busca:** pela `CatalogoBncc`, com filtros opcionais por código, trecho da descrição, etapa, ano e componente, em ordem de código e com até 50 resultados por padrão (`bncc.busca.limite-padrao`). O ano 7 também encontra as habilidades do bloco 6º e 7º, como EF67LP08.
- **Cache:** cada busca fica no Redis (cache `habilidades-bncc`), com a chave formada pelos filtros normalizados. A importação limpa o cache inteiro depois que a gravação é confirmada. A validade de 24 horas (`bncc.cache.validade`) é só uma garantia a mais.
- **Outra etapa:** basta criar um novo leitor que implemente `FonteDeHabilidades` na infraestrutura de `cenarios`. A importação junta as habilidades de todos os leitores.

### Sessão em sala

A sessão fica ligada ao professor, à turma e à versão aprovada vigente no início (RF-14, RN-10). Ela tem três estados: Em andamento, Pausada e Encerrada.

- **Início:** a `InicioDeSessao` recebe a turma e o cenário e só aceita o perfil Professor. Ela recusa uma turma que não existe e um cenário sem versão aprovada (RN-01).
- **Controles:** a `ControlesDaSessao` pausa, retoma, reinicia e encerra (RF-17). Só o professor que iniciou a sessão a controla. Pausar e retomar gravam um evento em `evento_sessao`, com o motivo da pausa. Reiniciar grava o ponto de corte: a tela fica limpa, mas as mensagens continuam no banco (RN-19). Sessão encerrada não aceita nenhum controle.
- **Uma sessão por turma:** só a sessão Em andamento bloqueia a turma, pelo índice único `uq_sessao_em_andamento_por_turma` (RN-10). A sessão pausada não impede outra, mas também não volta enquanto a outra estiver em andamento.
- **Estado no Redis:** cada sessão tem a chave `sessao:<id>:estado`, com status, motivo da pausa, tempo em pausa, ponto de reinício, contador de perguntas e as 20 últimas mensagens da tela. A chave é gravada só depois que a transação é confirmada, e a validade recomeça a cada gravação (`sessoes.estado.validade`, 6 horas por padrão).
- **Reconstrução:** o PostgreSQL é a fonte da verdade. Os controles (pausar, retomar, reiniciar e encerrar) sempre partem do estado refeito a partir de `sessao`, `evento_sessao` e `mensagem`, com a sessão travada, e nunca da chave do Redis, que pode ainda não ter recebido a gravação do controle anterior. A chave serve à leitura da tela: se ela sumir ou discordar do banco, o estado é refeito e gravado de novo (RNF-04). Falha do Redis não derruba a aula.
- **Mensagens:** quem gravar mensagens (RF-16) precisa usar o horário da aplicação em `mensagem.criado_em`, porque o ponto de reinício também usa esse relógio.

## Regras de fronteira

- A API de um módulo é só o seu pacote raiz. Os subpacotes são internos e inacessíveis aos outros módulos.
- Os módulos não podem formar ciclos de dependência.
- Um módulo só pode depender dos módulos declarados em `allowedDependencies`, no `package-info.java` dele:

| Módulo | Pode depender de |
| --- | --- |
| `acesso` | `eventos` |
| `cenarios` | `acesso`, `ia`, `eventos` |
| `sessoes` | `acesso`, `cenarios`, `ia`, `eventos` |
| `auditoria` | `acesso`, `eventos` |
| `metricas` | `acesso`, `eventos` |
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
