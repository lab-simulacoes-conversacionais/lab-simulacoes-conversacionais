# Laboratório de Simulações Conversacionais — POC 2.3 · Tema 09

**Documento de Requisitos e Regras de Negócio**

Versão 2.0 · 9 de outubro de 2026

Universidade Federal de Goiás · Instituto de Informática
Engenharia de Software · Manutenção de Software · 2026-2

> As figuras do documento original (Figuras 1, 2 e 3) não foram convertidas; aqui ficam apenas as legendas. Consulte o `.docx` para vê-las.

## Sumário

1. Introdução — 1.1 Contexto e objetivo · 1.2 Princípios orientadores · 1.3 Ficha da POC
2. Premissas e decisões — 2.1 Decisões tomadas com o professor · 2.2 Premissas
3. Escopo e visão geral — 3.1 O que está dentro e fora · 3.2 Dinâmica da aula · 3.3 Atores e perfis
4. Requisitos funcionais — 4.1 Acesso e cadastros · 4.2 Cenários e curadoria · 4.3 Sessão em sala · 4.4 Segurança de conteúdo e auditoria · 4.5 Avaliação e métricas
5. Regras de negócio — 5.1 Cenários e curadoria · 5.2 Sessão em sala · 5.3 Segurança de conteúdo e auditoria · 5.4 Avaliação e métricas · 5.5 Dados, privacidade e acesso
6. Requisitos não funcionais
7. Plano de entregas — 7.1 Cronograma de 4 semanas · 7.2 Backlog evolutivo pós-POC
8. Rastreabilidade
9. Histórico e glossário — 9.1 O que mudou da versão 1.0 · 9.2 Glossário

## 1. Introdução

### 1.1 Contexto e objetivo

Este documento especifica os **requisitos funcionais**, as **regras de negócio** e os **requisitos não funcionais** da POC 2.3, o **Laboratório de Simulações Conversacionais**: um sistema em que o professor conduz, na tela projetada, uma conversa da turma com um personagem simulado por Inteligência Artificial, como Santos Dumont em uma aula de História ou uma célula em uma aula de Ciências.

A proposta não é substituir o professor nem a aula expositiva. A simulação é **uma parte interativa da aula**: o professor expõe o conteúdo, a turma conversa com o personagem para explorar o tema, responde a um quiz e o professor fecha a aula sistematizando o que foi aprendido. O sistema existe para apoiar esse momento com segurança, curadoria e dados que permitam avaliar o resultado.

A POC testa a hipótese de que **simulações conversacionais aumentam o engajamento e a compreensão conceitual** em relação à aula apenas expositiva. Por isso o sistema registra, de forma agregada e sem identificar estudantes, o engajamento observado, o desempenho em avaliações conceituais e a taxa de erros factuais. O desenho segue as orientações do documento **"Inteligência Artificial na Educação Básica"** (MEC, 2026), da LGPD e do ECA Digital.

Esta é a **versão 2.0** do documento. Ela reescreve a versão 1.0 incorporando as decisões tomadas com o professor Jacson Rodrigues e o PO Israel Elias Trindade (seção 2), acrescenta as regras de negócio (seção 5) e organiza o desenvolvimento em quatro entregas semanais dentro do prazo de um mês (seção 7).

### 1.2 Princípios orientadores

Os princípios abaixo guiam todos os requisitos e regras deste documento. Quando houver dúvida sobre como interpretar um requisito, vale o princípio.

| Princípio | Descrição |
| --- | --- |
| **A IA complementa, não substitui** | O professor conduz a aula. A simulação é um momento interativo **dentro da aula expositiva**, nunca uma aula no lugar do professor. |
| **Nada chega à sala sem curadoria** | Todo cenário passa por **revisão humana** antes de ser usado com a turma, inclusive os sugeridos pela IA. |
| **Professor no controle** | Só o professor opera o sistema em sala: digita as perguntas da turma e pode **pausar ou encerrar** a qualquer momento. |
| **Privacidade por padrão** | Estudantes não têm conta e **nenhum dado pessoal** deles é coletado, conforme a LGPD e o ECA Digital. |
| **Transparência** | A turma sempre sabe que conversa com um **personagem simulado por IA**, que pode errar e deve ser questionado. |

### 1.3 Ficha da POC

**POC 2.3 · Laboratório de simulações conversacionais**

- **Classificação de risco.** Moderado. Coorte de entrada 2.
- **Hipótese de teste.** Simulações conversacionais aumentam o engajamento e a compreensão conceitual em relação à aula expositiva.
- **Salvaguardas na experimentação.** Curadoria prévia dos cenários. Operação preferencial em tela compartilhada sob condução docente. Auditoria de precisão histórica e científica dos conteúdos simulados.
- **Requisitos de dados.** Cenários curados e registros das sessões coletivas, sem perfis individuais.
- **Métricas de saída.** Engajamento observado. Desempenho em avaliações conceituais. Taxa de erros factuais identificados.
- **Prazo e forma de entrega.** Um mês, em quatro entregas semanais incrementais.

## 2. Premissas e decisões

### 2.1 Decisões tomadas com o professor

As perguntas abaixo foram levadas ao professor da disciplina. Cada decisão aparece ligada aos requisitos e regras que a implementam, para facilitar a análise de impacto caso alguma delas mude.

| Tema | Decisão | Onde aparece |
| --- | --- | --- |
| Provedor de IA | Qualquer provedor pode ser usado; não há exigência de modelo nacional. | RNF-02, RN-34 |
| Infraestrutura da escola | Considera-se o cenário ideal: internet estável, projetor e computador do professor na sala. | RNF-03, RNF-06, RNF-07 |
| Interação da turma | A turma fala; o **professor digita** as perguntas no sistema. | RF-16, RN-11, RN-12 |
| Login | Só o professor (e demais profissionais) faz login. **Estudantes não acessam** o sistema. | RF-01, RN-11 |
| Criação de cenários | O professor cria do zero **ou** a IA sugere e o curador ajusta. | RF-06, RF-07, RN-04, RN-05 |
| Vínculo com a BNCC | Obrigatório: todo cenário tem objetivos ligados a habilidades da BNCC. | RF-05, RN-02, RN-26 |
| Prazo da POC | 1 mês de desenvolvimento. | Seção 7 |
| Relação com Manutenção de Software | Entregar uma versão inicial rapidamente e evoluir o sistema a cada iteração. | Seção 7, RNF-11, RNF-12 |
| Papel da IA | Complementar a aula expositiva com uma parte interativa, sem substituir o professor. | Seção 1.2, RN-12, RN-15 |

### 2.2 Premissas

Além das decisões acima, o grupo assumiu as seguintes premissas. Se alguma se mostrar falsa, os requisitos afetados devem ser revistos.

- O piloto ocorre em **uma escola**, com poucas turmas, durante o mês da POC.
- A escola indica ao menos **um curador pedagógico** (pode ser um professor com esse perfil adicional).
- O catálogo de habilidades da BNCC é carregado a partir da base pública oficial do MEC.
- O sistema é usado em **português** e o provedor de IA escolhido atende bem o idioma.
- Os custos de uso da IA durante a POC são cobertos pelo projeto e monitorados (RNF-13).

## 3. Escopo e visão geral

### 3.1 O que está dentro e fora

**Dentro do escopo da POC**

- Cadastro de profissionais, perfis e turmas (sem estudantes).
- Criação de cenários pelo professor ou por sugestão da IA, com vínculo à BNCC.
- Curadoria, teste, versionamento e biblioteca de cenários.
- Sessões coletivas em tela projetada, conduzidas pelo professor.
- Filtro de conteúdo, sinalização de erros e auditoria de precisão.
- Quiz pós-simulação, registro de engajamento e comparação com aula expositiva.
- Painel de métricas e relatórios agregados.

**Fora do escopo (backlog evolutivo)**

- Uso individual pelos estudantes ou acesso em dispositivos dos alunos.
- Perfis, contas ou qualquer identificação de estudantes.
- Integração com sistemas de matrícula, diário de classe ou notas.
- Operação em várias escolas e alta disponibilidade 24×7.
- Interação por voz e conversas com vários personagens ao mesmo tempo.

### 3.2 Dinâmica da aula

A Figura 1 mostra onde o sistema entra na aula. Ele é usado nos momentos 2 e 3; a exposição e o fechamento continuam com o professor. Os tempos são sugestões e o professor decide o ritmo.

*Figura 1 · A simulação como parte interativa de uma aula expositiva (ver documento original)*

### 3.3 Atores e perfis

Só profissionais da escola têm acesso ao sistema. A turma participa da conversa por meio do professor.

| Ator | Responsabilidades | Faz login? |
| --- | --- | --- |
| Professor | Cria cenários, conduz sessões em sala, digita as perguntas da turma, sinaliza erros, aplica o quiz e registra o engajamento. | Sim |
| Curador Pedagógico | Ajusta cenários sugeridos pela IA, testa e aprova ou devolve cenários, faz a triagem das sinalizações e a auditoria de precisão. | Sim |
| Administrador | Cadastra usuários, perfis e turmas, define parâmetros (limites, custo) e consulta o log. Não altera conteúdo pedagógico. | Sim |
| Turma (estudantes) | Participa oralmente; suas perguntas chegam ao sistema pelo professor. Responde ao quiz de forma não identificada. | Não |
| Provedor de IA | Sistema externo que gera as falas do personagem, as sugestões de cenário e a moderação de conteúdo. | Integração |

## 4. Requisitos funcionais

Os requisitos estão agrupados por módulo. A coluna **Regras** aponta as regras de negócio da seção 5 ou, quando aplicável, o requisito não funcional relacionado. A **prioridade** segue a escala Essencial (sem ela a POC não testa a hipótese), Importante e Desejável; a coluna **Entrega** indica em qual semana o requisito fica pronto (seção 7).

### 4.1 Acesso e cadastros

| ID | Requisito | Descrição | Regras | Prioridade | Entrega |
| --- | --- | --- | --- | --- | --- |
| RF-01 | Autenticação de profissionais | Permitir login com e-mail e senha apenas para profissionais da escola. Estudantes não têm conta. | RN-11 | Essencial | E1 |
| RF-02 | Perfis de acesso | Liberar funções conforme os perfis Professor, Curador Pedagógico e Administrador; um usuário pode acumular perfis. | RN-36, RN-37 | Essencial | E1 |
| RF-03 | Gestão de usuários | O administrador cadastra, ativa, desativa usuários e atribui perfis. | RN-37 | Essencial | E1 |
| RF-04 | Cadastro de turmas | Cadastrar turmas apenas com escola, ano/série, turno e rótulo (ex.: "8º A"), sem lista de estudantes. | RN-32 | Essencial | E1 |
| RF-05 | Catálogo da BNCC | Oferecer catálogo pesquisável das habilidades da BNCC (código, descrição, etapa, ano e componente) para vincular aos cenários. | RN-02 | Essencial | E1 |

### 4.2 Cenários e curadoria

| ID | Requisito | Descrição | Regras | Prioridade | Entrega |
| --- | --- | --- | --- | --- | --- |
| RF-06 | Criação manual de cenário | Professor ou curador cria o cenário informando personagem, contexto, objetivos, habilidades BNCC, limites de assunto, fontes, faixa etária, duração e perguntas disparadoras. | RN-02, RN-03 | Essencial | E1 |
| RF-07 | Sugestão de cenário pela IA | A partir de tema, componente, ano e habilidades BNCC, a IA gera uma proposta completa de cenário (com quiz), que entra como rascunho para o curador ajustar. | RN-04, RN-05 | Importante | E2 |
| RF-08 | Envio e decisão de curadoria | O autor envia o cenário à curadoria; o curador aprova ou devolve com comentário. | RN-01, RN-05, RN-07 | Essencial | E1 |
| RF-09 | Teste do cenário | O curador conversa com o personagem em modo de teste antes de aprovar, sem gerar registro de sessão de turma. | RN-06 | Essencial | E2 |
| RF-10 | Checklist de curadoria | Exigir o preenchimento do checklist de curadoria para aprovar um cenário. | RN-06 | Essencial | E2 |
| RF-11 | Versionamento de cenários | Manter histórico de versões, comparar duas versões e restaurar uma versão aprovada anterior como novo rascunho. | RN-08 | Importante | E2 |
| RF-12 | Biblioteca de cenários | Listar os cenários aprovados da escola, com busca e filtros por componente, ano, habilidade BNCC e tema (lista simples na E1). | RN-01, RN-37 | Essencial | E1 / E2 |
| RF-13 | Arquivamento | O curador arquiva um cenário, retirando-o da biblioteca sem apagar o histórico. | RN-09 | Desejável | E2 |

### 4.3 Sessão em sala

| ID | Requisito | Descrição | Regras | Prioridade | Entrega |
| --- | --- | --- | --- | --- | --- |
| RF-14 | Início de sessão coletiva | O professor escolhe a turma e um cenário aprovado e inicia a sessão, vinculada à versão vigente do cenário. | RN-01, RN-10 | Essencial | E1 |
| RF-15 | Modo projeção | Tela para projetor com a conversa em fonte grande, aviso permanente de personagem simulado por IA e painel de controle do professor (Figura 2). | RN-14 | Essencial | E1 |
| RF-16 | Interação mediada pelo professor | O professor digita as perguntas da turma e a IA responde no papel do personagem, dentro dos limites do cenário. | RN-11, RN-12, RN-15, RN-16, RN-18 | Essencial | E1 |
| RF-17 | Controles da sessão | Pausar, retomar, reiniciar e encerrar a sessão a qualquer momento. | RN-19 | Essencial | E1 |
| RF-18 | Perguntas disparadoras | Mostrar só ao professor perguntas sugeridas pelo cenário para estimular a conversa. | — | Desejável | E2 |
| RF-19 | Controle de tempo | Mostrar tempo decorrido e previsto, avisar ao atingir o previsto e pausar no limite máximo. | RN-17 | Importante | E3 |
| RF-20 | Retomada após falha | Restaurar a sessão em andamento após queda de conexão, recarga da página ou falha do provedor. | RNF-04 | Importante | E3 |
| RF-21 | Alerta de dados pessoais | Verificar o texto digitado antes do envio e bloqueá-lo se houver padrões de dado pessoal. | RN-13 | Importante | E3 |

*Figura 2 · Esboço do modo projeção: conversa à esquerda, painel do professor à direita (RF-15 a RF-18) (ver documento original)*

### 4.4 Segurança de conteúdo e auditoria

| ID | Requisito | Descrição | Regras | Prioridade | Entrega |
| --- | --- | --- | --- | --- | --- |
| RF-22 | Filtro de conteúdo | Moderar perguntas e respostas antes da exibição; resposta bloqueada não aparece, a sessão pausa e o caso vai para o curador. | RN-20 | Essencial | E1 |
| RF-23 | Sinalização de erro | Professor e curador sinalizam uma resposta com tipo, gravidade percebida e justificativa; trecho, sessão e versão são anexados automaticamente. | RN-21 | Essencial | E3 |
| RF-24 | Triagem de sinalizações | Fila do curador para confirmar ou descartar sinalizações, classificar a gravidade e encaminhar a correção. | RN-22, RN-24 | Essencial | E3 |
| RF-25 | Suspensão e reativação | Suspender e reativar cenários automaticamente conforme as regras de gravidade. | RN-23, RN-25 | Essencial | E3 |
| RF-26 | Log de auditoria | Registrar as ações críticas com usuário e data/hora; consulta pelo curador e pelo administrador. | RN-35 | Essencial | E1 / E3 |

### 4.5 Avaliação e métricas

| ID | Requisito | Descrição | Regras | Prioridade | Entrega |
| --- | --- | --- | --- | --- | --- |
| RF-27 | Quiz do cenário | Cadastrar o quiz do cenário, ligado aos objetivos BNCC; pode ser sugerido pela IA e é aprovado junto com o cenário. | RN-26 | Essencial | E4 |
| RF-28 | Aplicação do quiz | Projetar o quiz após a simulação e registrar só a contagem de respostas por alternativa e o número de presentes. | RN-27 | Essencial | E4 |
| RF-29 | Registro de engajamento | Ao encerrar, o professor registra o nível de participação; o sistema calcula indicadores automáticos. | RN-28 | Essencial | E4 |
| RF-30 | Sessão de controle | Registrar uma aula expositiva sem simulação aplicando o mesmo quiz, para comparação. | RN-30 | Importante | E4 |
| RF-31 | Painel de métricas | Exibir por sessão, cenário, turma e período: engajamento, desempenho no quiz, taxa de erros factuais e comparação simulação × expositiva. | RN-29, RN-30, RN-31 | Essencial | E4 |
| RF-32 | Exportação de relatórios | Exportar relatórios agregados em CSV e PDF. | RN-31 | Importante | E4 |

## 5. Regras de negócio

As regras de negócio definem **como o sistema deve se comportar** em situações específicas, independentemente da tecnologia usada. Elas são a referência para os testes de aceitação e, durante a manutenção, para a análise de impacto de qualquer mudança.

### 5.1 Cenários e curadoria

*Figura 3 · Ciclo de vida de um cenário (RN-01 a RN-09 e RN-23) (ver documento original). Estados: Rascunho, Em curadoria, Aprovado, Suspenso e Arquivado.*

| ID | Regra | Descrição |
| --- | --- | --- |
| RN-01 | Só cenário aprovado vai para a sala | Apenas a versão **aprovada vigente** de um cenário pode ser usada em sessões. Rascunhos, cenários em curadoria, suspensos ou arquivados não aparecem para iniciar sessão. |
| RN-02 | Vínculo obrigatório com a BNCC | Todo cenário declara componente curricular, etapa e ano, **ao menos uma habilidade da BNCC** (código oficial, ex.: EF08HI03) e ao menos um objetivo de aprendizagem escrito a partir dela. Sem isso, não pode ser enviado à curadoria. |
| RN-03 | Conteúdo mínimo do cenário | São obrigatórios: título, personagem e papel da IA, contexto, objetivos, limites de assunto (o que a IA não deve abordar), ao menos uma fonte de referência, faixa etária e duração prevista. |
| RN-04 | Sugestão da IA é só ponto de partida | Professor ou curador pode pedir uma sugestão à IA. O resultado entra como **Rascunho** marcado "Sugerido por IA" e vai ao curador, que confere os fatos com as fontes e ajusta o texto. A IA nunca aprova, publica ou altera um cenário aprovado. |
| RN-05 | Quem aprova | O professor **não aprova** cenários. Cenário criado pelo professor é aprovado por um curador. Cenário sugerido pela IA é ajustado e aprovado pelo curador, que assume a responsabilidade pedagógica por ele. |
| RN-06 | Teste e checklist antes de aprovar | Para aprovar, o curador **testa o cenário com pelo menos 5 perguntas** e preenche o checklist: fatos conferidos com as fontes, linguagem adequada à faixa etária, objetivos coerentes com a BNCC e limites de assunto respeitados no teste. Vale a partir da E2; na E1 a aprovação é registrada sem checklist. |
| RN-07 | Devolução com justificativa | Ao devolver um cenário, o curador deve escrever um comentário dizendo o que precisa mudar. O cenário volta a Rascunho. |
| RN-08 | Versões | Editar um cenário aprovado gera **nova versão em Rascunho**. A versão aprovada anterior continua valendo em sala até a nova ser aprovada. Versões aprovadas nunca são apagadas. |
| RN-09 | Arquivamento | Cenário arquivado sai da biblioteca, mas seu histórico, versões e sessões continuam disponíveis para consulta e auditoria. |

### 5.2 Sessão em sala

| ID | Regra | Descrição |
| --- | --- | --- |
| RN-10 | Quem inicia a sessão | Somente usuários com perfil Professor iniciam sessões. Toda sessão fica ligada a professor, turma, cenário e versão. Cada turma tem no máximo **uma sessão em andamento**. |
| RN-11 | Estudantes não usam o sistema | Não há login, cadastro, nome, foto ou qualquer identificador de estudante. Tudo o que a turma pergunta é digitado pelo professor e registrado com autoria genérica **"Turma"**. |
| RN-12 | Professor como mediador | O professor escolhe quais perguntas encaminhar e pode reformulá-las. A IA nunca se dirige a um estudante específico, não pede informações pessoais e não substitui a explicação do professor. |
| RN-13 | Bloqueio de dados pessoais | Antes do envio, o texto é verificado quanto a padrões de dado pessoal (e-mail, telefone, CPF, endereço). Se houver, o envio é **bloqueado** até o professor reescrever. O professor é orientado a não digitar nomes de estudantes. |
| RN-14 | A IA sempre se identifica | A tela de projeção exibe o tempo todo "Personagem simulado por Inteligência Artificial · pode conter erros", e a primeira fala de cada sessão apresenta o personagem como simulação. |
| RN-15 | Personagem dentro dos limites | A IA mantém o papel e os limites do cenário: redireciona perguntas fora do tema, não dá opinião político-partidária ou religiosa, não dá conselhos pessoais ou de saúde, não atribui notas e **admite quando não sabe** em vez de inventar. |
| RN-16 | Linguagem adequada | Vocabulário e complexidade seguem a etapa e o ano do cenário. Cada resposta tem no máximo cerca de **120 palavras**, para caber na tela e manter o ritmo da aula. |
| RN-17 | Duração da sessão | A duração prevista do cenário fica entre **10 e 30 minutos**. Ao atingi-la, o sistema avisa o professor sem interromper. Aos **50 minutos** (uma hora-aula) a sessão pausa automaticamente e só continua se o professor confirmar. |
| RN-18 | Limite de perguntas | Cada sessão aceita até **40 perguntas** da turma (valor configurável pelo administrador). Ao atingir o limite, o envio é bloqueado e o sistema sugere passar ao quiz. Mantém o ritmo da aula e o custo previsível. |
| RN-19 | Reiniciar e encerrar | Reiniciar limpa a conversa da tela, mas mantém o que já ocorreu no registro da sessão. Sessão encerrada **não pode ser reaberta**; para continuar o tema, inicia-se uma nova sessão. |

### 5.3 Segurança de conteúdo e auditoria

| ID | Regra | Descrição |
| --- | --- | --- |
| RN-20 | Filtro antes de exibir | Perguntas e respostas passam por filtro de conteúdo impróprio à faixa etária (violência, conteúdo sexual, automutilação, discurso de ódio, drogas) **antes de aparecer na tela**. Resposta bloqueada não é exibida; a sessão pausa, o professor vê um aviso neutro e o caso vai para a fila do curador. |
| RN-21 | Quem sinaliza e como | O professor sinaliza durante a sessão ou até 7 dias depois; o curador, a qualquer momento. A sinalização exige tipo (erro histórico, erro científico, anacronismo, saída do personagem, conteúdo inadequado), gravidade percebida e justificativa curta. |
| RN-22 | Gravidade do erro | O curador classifica cada sinalização confirmada como: **Leve** (imprecisão que não afeta o objetivo), **Moderada** (erro que pode levar a uma compreensão incorreta) ou **Grave** (erro sobre o conceito central do objetivo BNCC ou conteúdo inadequado à faixa etária). |
| RN-23 | Suspensão do cenário | (a) Sinalização marcada como grave pelo professor **suspende o cenário na hora**, por precaução. (b) Erro grave confirmado mantém o cenário suspenso até uma nova versão ser aprovada. (c) **Três erros moderados** confirmados na mesma versão também suspendem o cenário. (d) Se o curador descartar a sinalização que causou a suspensão, o cenário volta a Aprovado. |
| RN-24 | Prazo de triagem | Sinalizações devem ser triadas pelo curador em até **2 dias úteis**. Pendências fora do prazo ficam em destaque para o curador e o administrador. |
| RN-25 | Suspensão durante a aula | Se o cenário for suspenso com uma sessão em andamento, a sessão pausa. O professor pode encerrá-la ou retomá-la depois de corrigir o erro com a turma; novas sessões com o cenário ficam bloqueadas. |

### 5.4 Avaliação e métricas

| ID | Regra | Descrição |
| --- | --- | --- |
| RN-26 | Quiz do cenário | Cada cenário tem um quiz de **3 a 10 questões** de múltipla escolha, com 4 alternativas e uma correta, cada uma ligada a um objetivo BNCC do cenário. O quiz é revisado e aprovado com o cenário; cenários aprovados antes da E4 recebem o quiz em nova versão. |
| RN-27 | Respostas sem identificação | O quiz é respondido sem identificar estudantes (mãos levantadas, cartões ou papel). O professor registra só a **contagem por alternativa** e o número de presentes. A soma das respostas de uma questão não pode passar do número de presentes. |
| RN-28 | Registro de engajamento | Ao encerrar, o professor registra a participação da turma de 1 a 5 (1 = quase ninguém; 3 = cerca de metade; 5 = praticamente todos). Sem esse registro a sessão não entra nas métricas. O sistema calcula sozinho: perguntas da turma, duração efetiva e tempo em pausa. |
| RN-29 | Taxa de erros factuais | Taxa = sinalizações confirmadas (moderadas e graves) ÷ respostas da IA, por versão de cenário e no total. Sinalizações descartadas ou pendentes não entram no cálculo. |
| RN-30 | Comparação com aula expositiva | Uma sessão de controle é uma aula expositiva, sem simulação, na mesma série e com o mesmo quiz. A comparação só aparece quando há ao menos uma sessão de cada tipo com o mesmo quiz. |
| RN-31 | Uso dos indicadores | Indicadores são sempre agregados por turma. **Não é permitido ranquear professores** nem usar os dados para avaliar desempenho docente; servem para validar a hipótese da POC e melhorar os cenários. |

### 5.5 Dados, privacidade e acesso

| ID | Regra | Descrição |
| --- | --- | --- |
| RN-32 | Identificação da turma | A turma é identificada só por escola, ano/série, turno e rótulo (ex.: "8º A"). O número de presentes é informado por sessão; não há lista de estudantes. |
| RN-33 | Retenção | As conversas das sessões ficam guardadas até **90 dias após o fim da POC**, para auditoria, e então são excluídas automaticamente. Métricas agregadas, sinalizações e versões de cenários são mantidas. |
| RN-34 | Provedor de IA | Qualquer provedor pode ser usado, desde que os dados enviados **não sejam usados para treinar modelos** e fiquem retidos o mínimo possível. São enviados apenas o cenário, o histórico da sessão e a pergunta digitada, nunca dados de usuários. |
| RN-35 | Log de ações críticas | Aprovar, devolver, suspender, reativar, arquivar, triar sinalizações, alterar perfis e exportar relatórios ficam registrados com usuário e data/hora. O log não pode ser editado nem apagado. |
| RN-36 | Acúmulo de perfis | Um mesmo usuário pode ter mais de um perfil (por exemplo, Professor e Curador). Mesmo assim, ele não aprova cenários que criou como professor (RN-05). |
| RN-37 | Quem vê o quê | O professor vê seus cenários, suas sessões e a biblioteca aprovada da escola. O curador vê todos os cenários e sinalizações da escola. O administrador gerencia usuários, turmas e parâmetros, sem editar conteúdo pedagógico. |

## 6. Requisitos não funcionais

As metas foram dimensionadas para o piloto de um mês, considerando o cenário ideal de infraestrutura definido com o professor. Metas de escala (várias escolas, operação 24×7) ficam para a evolução pós-POC.

| ID | Categoria | Requisito | Entrega |
| --- | --- | --- | --- |
| RNF-01 | Privacidade | **Privacidade por padrão.** Nenhum dado pessoal de estudante é coletado, armazenado ou enviado ao provedor; a configuração mais protetiva é a padrão (LGPD, Lei 13.709/2018; ECA Digital, Lei 15.211/2025, art. 7º). | E1 |
| RNF-02 | Independência de provedor | **Troca de IA sem reescrever o sistema.** A integração fica isolada em um módulo adaptador; mudar de provedor ou modelo exige só configuração e um novo adaptador, sem alterar as regras de negócio. | E1 |
| RNF-03 | Desempenho | **Ritmo de conversa.** Respostas exibidas em streaming: primeiro trecho em até 3 s e resposta completa em até 10 s em 95% das interações. | E1 |
| RNF-04 | Resiliência | **Aula não se perde.** O estado da sessão é salvo a cada interação; recarga da página ou queda de conexão retoma a sessão sem perda; falha do provedor gera aviso e permite reenviar. | E3 |
| RNF-05 | Usabilidade | **Simples para o professor.** Pausar, retomar e encerrar com um clique e por atalho de teclado; um professor sem formação técnica conduz a primeira sessão após um tutorial de até 15 minutos. | E1 |
| RNF-06 | Legibilidade em projeção | **Leitura do fundo da sala.** Texto da conversa com no mínimo 28 px em 1920×1080 e contraste mínimo de 7:1. | E1 |
| RNF-07 | Portabilidade | **Sem instalação.** Aplicação web nas duas versões mais recentes de Chrome, Edge e Firefox, em 1366×768 e 1920×1080. | E1 |
| RNF-08 | Acessibilidade | Telas dos profissionais conformes às WCAG 2.1, nível AA. | E4 |
| RNF-09 | Segurança | HTTPS em toda a comunicação; senhas com hash forte (ex.: Argon2 ou bcrypt); sessão expira por inatividade; instruções do cenário isoladas do texto digitado para reduzir injeção de prompt, com testes automáticos de tentativa de "quebrar" o personagem. | E1 a E3 |
| RNF-10 | Rastreabilidade | Cada resposta guarda cenário, versão, provedor, modelo, parâmetros e data/hora, permitindo auditar qualquer fala exibida em sala. | E1 |
| RNF-11 | Manutenibilidade | Arquitetura em módulos (Acesso, Cenários, Sessões, Auditoria, Métricas e Adaptador de IA); prompts versionados como artefatos fora do código; regras de negócio com testes automatizados (cobertura ≥ 80% nesses módulos) e análise estática no pipeline. | E1 |
| RNF-12 | Entrega contínua | Pipeline de CI/CD com testes e implantação automática em homologação; cada entrega semanal publicada no piloto; reversão para a versão anterior em até 15 minutos. | E1 |
| RNF-13 | Observabilidade e custo | Registrar latência, erros e consumo (tokens e custo) por sessão; alertar o administrador quando o custo diário passar do limite definido. | E2 |
| RNF-14 | Interoperabilidade | Relatórios exportados em formatos abertos: CSV (UTF-8) e PDF. | E4 |
| RNF-15 | Retenção e descarte | Exclusão automática dos dados conforme RN-33, com registro de cada execução. | E4 |
| RNF-16 | Disponibilidade | Disponibilidade ≥ 98% nos horários letivos da escola piloto durante a POC. | E1 |

## 7. Plano de entregas

Em vez de entregar tudo no fim do mês, o sistema vai para a sala de aula **já na primeira semana**, com o mínimo necessário para uma simulação segura, e evolui a cada semana. Essa é a lógica de **manutenção evolutiva com entrega contínua**: cada incremento passa pelo pipeline de testes, é implantado no piloto e o retorno dos professores orienta a próxima entrega.

### 7.1 Cronograma de 4 semanas

| Entrega | Quando | Nome | Requisitos funcionais | Resultado em sala |
| --- | --- | --- | --- | --- |
| E1 | Semana 1 | Primeira simulação em sala | RF-01 a RF-06, RF-08, RF-12 (lista), RF-14 a RF-17, RF-22, RF-26 (registro) | Professor faz login, cria um cenário com BNCC, o curador aprova e a turma conversa com o personagem no projetor, com filtro de conteúdo ativo. |
| E2 | Semana 2 | Curadoria completa | RF-07, RF-09 a RF-11, RF-12 (filtros), RF-13, RF-18 | IA sugere cenários, o curador testa e aprova com checklist, versões são comparáveis e a biblioteca tem busca. |
| E3 | Semana 3 | Auditoria e segurança | RF-19 a RF-21, RF-23 a RF-25, RF-26 (consulta) | Erros sinalizados são triados e suspendem cenários automaticamente; sessões resistem a falhas; dados pessoais são bloqueados. |
| E4 | Semana 4 | Evidência pedagógica | RF-27 a RF-32 | Quiz, engajamento, turma de controle e painel de métricas permitem testar a hipótese da POC. |

*Os requisitos não funcionais acompanham as entregas indicadas na seção 6. Regras de negócio entram junto com os requisitos que as referenciam.*

### 7.2 Backlog evolutivo pós-POC

O que fica fora do mês não é descartado: vira backlog, organizado pelas categorias de manutenção de software. Isso deixa claro, desde já, como o sistema deve evoluir depois do piloto.

| Categoria | Itens previstos |
| --- | --- |
| Corretiva | Corrigir defeitos relatados no piloto e cenários com erros confirmados (fluxo das RN-21 a RN-25). |
| Preventiva | Criar um conjunto de perguntas de referência por cenário e rodá-lo como **teste de regressão** a cada troca de prompt ou de modelo; atualizar dependências e revisar segurança. |
| Adaptativa | Trocar ou atualizar o modelo de IA pelo adaptador (RNF-02); adequar o sistema a novas normas sobre IA e classificação indicativa; preparar a operação em várias escolas. |
| Aditiva | Uso individual supervisionado, voz para o personagem, conversa com vários personagens, banco de cenários compartilhado entre escolas e integração com sistemas da rede. |
| Perfectiva | Melhorar usabilidade e relatórios a partir do retorno dos professores; opção de o professor revisar a resposta antes de exibi-la. |

## 8. Rastreabilidade

A matriz liga cada elemento da descrição da POC (Ficha, seção 1.3) e das decisões do projeto aos requisitos e regras que os atendem. Ela deve ser atualizada a cada mudança, permitindo a **rastreabilidade bidirecional**: de um elemento do problema chega-se aos requisitos, e de um requisito chega-se ao motivo de sua existência.

| Elemento do problema | Requisitos funcionais | Regras de negócio | Não funcionais |
| --- | --- | --- | --- |
| Curadoria prévia dos cenários | RF-05 a RF-13 | RN-01 a RN-09 | RNF-10 |
| Operação em tela compartilhada sob condução docente | RF-14 a RF-21 | RN-10 a RN-19 | RNF-03 a RNF-07 |
| Auditoria de precisão histórica e científica | RF-22 a RF-26 | RN-20 a RN-25 | RNF-09, RNF-10 |
| Cenários curados e registros de sessões coletivas, sem perfis individuais | RF-01, RF-04, RF-14, RF-21 | RN-11, RN-13, RN-32 a RN-34 | RNF-01, RNF-15 |
| Métrica: engajamento observado | RF-29, RF-31 | RN-28 | — |
| Métrica: desempenho em avaliações conceituais | RF-27, RF-28, RF-30, RF-31 | RN-26, RN-27, RN-30 | — |
| Métrica: taxa de erros factuais identificados | RF-23, RF-24, RF-31 | RN-22, RN-29 | — |
| Classificação de risco moderado | RF-02, RF-22, RF-25, RF-26 | RN-20, RN-23, RN-35 | RNF-01, RNF-09 |
| Objetivos vinculados à BNCC | RF-05, RF-27 | RN-02, RN-26 | — |
| IA complementa o professor | RF-15 a RF-17 | RN-12, RN-14, RN-15, RN-31 | RNF-05 |

## 9. Histórico e glossário

### 9.1 O que mudou da versão 1.0

| Versão | Data | Descrição |
| --- | --- | --- |
| 1.0 | 25/09/2026 | Versão inicial: 14 requisitos funcionais e 14 não funcionais. |
| 2.0 | 09/10/2026 | Reescrita com as decisões do professor, 32 requisitos funcionais, 37 regras de negócio, 16 requisitos não funcionais e plano de entregas de 1 mês. |

| Aspecto | Mudança |
| --- | --- |
| Atores | O perfil "Auditor de Conteúdo" foi incorporado ao Curador Pedagógico. Fica explícito que estudantes não acessam o sistema. |
| Escopo | O modo individual (antigo RF-13) saiu da POC e foi para o backlog evolutivo. Escala multiescola e 99% de disponibilidade também saíram. |
| Novos requisitos | Catálogo BNCC, sugestão de cenário pela IA, teste antes de aprovar, quiz sem dispositivos dos alunos, sessão de controle, alerta de dados pessoais e retomada após falha. |
| Regras de negócio | Criadas 37 regras de negócio (seção 5), antes implícitas nas descrições dos requisitos. |
| Não funcionais | Metas ajustadas ao piloto (resposta em streaming, disponibilidade ≥ 98% em horário letivo). Novos: independência de provedor, entrega contínua, observabilidade e custo. |
| Planejamento | Roteiro de 2 meses do pitch substituído por 4 entregas semanais, com prioridade por requisito. |

### 9.2 Glossário

| Termo | Significado |
| --- | --- |
| BNCC | Base Nacional Comum Curricular; define as habilidades esperadas por etapa, ano e componente, identificadas por códigos (ex.: EF08HI03). |
| Cenário | Roteiro de simulação: personagem, contexto, objetivos, limites, fontes e quiz. Cada alteração gera uma versão. |
| Curadoria | Revisão humana que testa e aprova (ou devolve) um cenário antes do uso em sala. |
| Modo projeção | Tela exibida no projetor durante a sessão, com a conversa e o painel do professor. |
| POC | Prova de conceito: versão enxuta para testar a hipótese em ambiente real. |
| Sessão coletiva | Uso de um cenário com uma turma, conduzido pelo professor em tela compartilhada. |
| Sessão de controle | Aula expositiva sem simulação, com o mesmo quiz, usada para comparação. |
| Sinalização | Registro de um possível erro ou conteúdo inadequado em uma resposta da IA, para triagem pelo curador. |
| Versão vigente | Última versão aprovada de um cenário, a única que pode ser usada em sala. |
