# Spike: primeira frase aprovada na tela (issues #19 e #80)

Confere se a moderação frase a frase cumpre o RNF-03, como pede a seção 5 da [stack.md](../../stack.md)
(fluxo de uma pergunta, passo 7). O RN-20 exige moderar antes de exibir, e o RNF-03 exige o primeiro
trecho em até 3 s. O critério é ter **95% das 30 perguntas de referência com a primeira frase aprovada
na tela em até 3 s**.

- **Se atingir:** a moderação por frase continua.
- **Se não atingir:** a moderação passa a ser da resposta inteira, com o aviso "personagem pensando", e o
  grupo leva ao PO a revisão do RNF-03.

A primeira medição (`resultados01.md`) ficou em 93,3%. Antes de trocar a moderação, a issue #80 tentou
reduzir a latência mantendo a moderação por frase. Cada estratégia foi medida com este spike, num
relatório próprio, e comparada à linha de base das execuções 02 a 04 (veja abaixo).

## Resultado da issue #80

**O RNF-03 foi atingido nas duas execuções da configuração final, e a moderação por frase continua.**
A configuração final é o esforço de raciocínio `none`, o nível de serviço prioritário da OpenAI e uma
conexão única com o provedor, aberta ao subir a aplicação. Ela é o padrão do `application.properties`.
As duas execuções (08 e 09) rodaram na mesma tarde, com 11 minutos de diferença (13h51 e 14h02), e a 09
é uma execução de diagnóstico, com o log HTTP ligado. Sem o nível prioritário, a meta não é atingida: o
`none` no nível padrão (05) ficou em 83,3%. Veja em "Pontos a observar" o que acontece se a OpenAI
atender no nível padrão.

| Relatório | Esforço | Nível de serviço | Conexão única | Em até 3 s | Média | p95 | Espera do provedor (média) |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: |
| 01 (issue #19, à 01h) | low | padrão | não | 93,3% | 2,28 | 3,07 | – |
| 02 a 04 (linha de base) | low | padrão | não | 64,4% | 2,82 | 3,85 | 1,66 |
| 05 (E1) | none | padrão | não | 83,3% | 2,56 | 3,72 | 1,38 |
| 06 (E2) | none | prioritário | não | 100% | 1,99 | 2,60 | 0,93 |
| 07 (E3) | low | prioritário | sim | 96,7% | 2,08 | 2,65 | 1,14 |
| **08 (final)** | **none** | **prioritário** | **sim** | **96,7%** | **2,03** | **2,22** | **0,89** |
| **09 (final, diagnóstico)** | **none** | **prioritário** | **sim** | **100%** | **1,84** | **2,12** | **0,88** |

Tempos em segundos, a partir do clique em enviar. A linha 02 a 04 junta as 90 respostas das três.

### Onde o tempo estava

Nas linhas de base, o código da aplicação (banco, Redis, preparo, montagem do prompt, envio pelo SSE)
somava cerca de 0,05 s. O resto era a OpenAI: a moderação da pergunta (~0,36 s), a espera até o primeiro
trecho do modelo (1,66 s em média, a etapa que mais variava), o modelo fechando a primeira frase
(~0,35 s) e a moderação dessa frase (~0,34 s). Por isso só rendiam as estratégias que atacam a espera do
provedor.

### Estratégias

- **E, esforço de raciocínio e nível de serviço (adotada).** O `gpt-6.1-terra` recusa `minimal` (HTTP
  400); o `none` funciona. Sozinho, ele levou a 83,3%, abaixo da meta. Com o nível prioritário, a espera
  do provedor caiu quase pela metade. O `low` com o nível prioritário (07) também passou, mas sem folga:
  29 de 30, com uma resposta acima de 3 s, que é o máximo que os 95% permitem em 30.
- **A, conexão única aquecida (adotada).** O Spring AI fazia o streaming pelo `WebClient` e a moderação
  pelo `RestClient`, cada um com o próprio cliente HTTP do JDK. Agora os dois usam um cliente só
  (`ConexaoComOProvedor`), e uma moderação ao subir a aplicação abre a conexão
  (`AquecimentoDaConexao`, propriedade `ia.aquecimento.habilitado`). A moderação da pergunta, feita logo
  antes da geração, deixa aberta a conexão que o streaming usa. No 09, as 178 requisições à OpenAI foram
  por uma só conexão HTTP/2 (veja "Diagnóstico da conexão com o provedor"). Com 10 s entre as perguntas, o
  ganho no spike é pequeno, porque a conexão de antes também não chegava a cair; o ganho aparece com
  intervalos maiores, como numa aula.
- **B, pré-geração barata (descartada).** Buscar a versão aprovada e montar o prompt levava ~0,016 s.
- **C, contexto reduzido (não medida).** São ~1.150 tokens de entrada por chamada; o ganho seria pequeno.
- **D, moderação das frases em paralelo (fora do RNF-03).** A primeira frase é a primeira moderada, então
  o paralelismo não muda o tempo dela. Pode melhorar a resposta completa, fora desta issue.
- **F, gerar antes de o canal SSE abrir (desnecessária).** O canal abre ~0,002 s depois de o fragmento
  voltar; no navegador, talvez 0,1 s. Com o RNF-03 atingido, ficou de fora.
- **Ficaram de fora por ferir o RN-20:** moderar a pergunta em paralelo com a geração e cortar a primeira
  frase mais cedo, na vírgula.

### Qualidade

As respostas do `none` foram lidas lado a lado com as do `low`, nas 30 perguntas, com atenção às difíceis
(16 a 20). A qualidade foi aprovada pelo grupo: ele admite ser IA (RN-14), recusa política e o pedido de
esquecer as instruções (RNF-09), diz que não sabe em vez de inventar, fica em 1906 e volta ao tema
(RN-15). Diferenças observadas: o `low` é um pouco mais cauteloso nos fatos; o `none` é um pouco mais
envolvente, devolvendo perguntas à turma, mas improvisa mais. Em duas execuções ele afirmou ter
patenteado invenções dos dirigíveis (05) e sugeriu consultar o Museu Aeroespacial, de 1976 (06).
Nenhuma das duas configurações errou um fato central do 14-bis.

### Custo

O nível prioritário custa o dobro do padrão em todos os itens: US$ 2,00 por milhão de tokens de entrada e
US$ 10,00 de saída, contra US$ 1,00 e 5,00 (página de preços da OpenAI, conferida em 19/11/2026, onde o
nível aparece com o nome novo de "Fast"). Com ~1.150 tokens de entrada e ~75 de saída por resposta, uma
aula de 30 perguntas passa de cerca de US$ 0,046 para US$ 0,093. O esforço quase não muda o custo, porque
a entrada domina. Os preços padrão de `ia.preco.*` foram para 2,00 e 10,00, para o custo gravado em cada
resposta (RNF-13) bater com o cobrado. Até o 07, os relatórios calculam o custo no preço padrão, mesmo
os que rodaram no nível prioritário; do 08 em diante, no prioritário.

### Pontos a observar

- **Moderação lenta isolada.** No 08, a moderação da pergunta 18 levou 5,33 s (mediana de 0,37 s; o maior
  valor nas outras execuções foi 1,22 s), sem nova tentativa, erro ou tempo limite no log. O diagnóstico
  (09) não repetiu o caso e mostrou a conexão estável, então a causa provável é uma lentidão pontual da
  OpenAI. Os timers `lab.ia.chamada` da aplicação acompanham isso em produção.
- **Conexão ociosa.** O cliente HTTP do JDK fecha, por padrão, a conexão parada há 30 s. Com intervalos
  maiores entre as perguntas, a moderação da pergunta reabre a conexão e paga o handshake; o streaming
  continua aproveitando a conexão que ela abriu.
- **Rebaixamento do nível de serviço.** A OpenAI pode atender um pedido prioritário no nível padrão, e
  informa isso no campo `service_tier` da resposta. A aplicação não lê esse campo: nesse caso, a
  latência esperada volta à da 05 (83,3% em até 3 s, abaixo da meta), o custo gravado fica no dobro do
  cobrado e os parâmetros gravados continuam dizendo `priority`.
- **Horário.** A latência da OpenAI varia com o horário: a 01, de madrugada, foi bem mais rápida que as
  linhas de base, rodadas em horário comercial nos EUA. As execuções finais rodaram à tarde.

## Como rodar

Precisa de Java 21, Docker (para o PostgreSQL e o Redis dos testes) e da variável `OPENAI_API_KEY` no
terminal. Leva uns 7 minutos e gasta poucos centavos de dólar.

```sh
export OPENAI_API_KEY='<a chave da OpenAI>'
./mvnw test -Dtest=PrimeiraFraseAprovadaComOpenAiTest -Dia.spike-primeira-frase=true -Djacoco.skip=true
```

O teste fica em `src/test/java/.../sessoes/apresentacao/PrimeiraFraseAprovadaComOpenAiTest.java`. Sem a
propriedade e a chave, ele não roda, então não entra no `verify` nem no CI. O intervalo entre uma
resposta e a próxima pergunta é de 10 s, para imitar o professor digitando. Ele pode ser trocado com
`-Dspike.intervalo-s=<segundos>`.

Cada execução grava um relatório novo nesta pasta, numerado depois do maior que já existe:
`resultados01.md`, `resultados02.md` e assim por diante. Nenhuma execução sobrescreve outra, então dá
para comparar estratégias para reduzir a latência. O resumo de cada pergunta aparece no terminal enquanto
roda, e o nome do arquivo gravado aparece no fim.

O `resultados01.md` é a medição registrada na issue #19, com o modelo `gpt-6.1-terra` e o esforço `low`.

### Anotar a estratégia (issue #80)

Cada relatório diz qual estratégia de latência foi medida, com a propriedade `-Dspike.estrategia`. Sem
ela, o relatório anota uma linha de base, que hoje roda com a configuração final do
`application.properties`:

```sh
./mvnw test -Dtest=PrimeiraFraseAprovadaComOpenAiTest -Dia.spike-primeira-frase=true -Djacoco.skip=true \
  -Dspike.estrategia="Nome da estratégia medida"
```

As propriedades da aplicação também podem ser trocadas na linha de comando, sem mexer no código. O
cabeçalho do relatório mostra o modelo, o esforço de raciocínio e o nível de serviço usados. Por
exemplo, para voltar ao `low` no nível padrão das linhas de base:

```sh
./mvnw test -Dtest=PrimeiraFraseAprovadaComOpenAiTest -Dia.spike-primeira-frase=true -Djacoco.skip=true \
  -Dspring.ai.openai.chat.options.reasoning-effort=low \
  -Dspring.ai.openai.chat.options.service-tier=default \
  -Dspike.estrategia="Linha de base: low no nível padrão"
```

O relatório traz uma tabela que compara a execução com a linha de base: a proporção dentro da meta, a
média, o p95 e o máximo da primeira frase na tela, a espera do provedor até o primeiro trecho e as
etapas principais. Os números ficam copiados no teste, para a comparação continuar valendo mesmo que o
formato do relatório mude.

### Linha de base (issue #80)

A linha de base foi repetida três vezes sem mudar nada no código nem nos parâmetros
(`resultados02.md` a `resultados04.md`), e as 90 respostas juntas são a referência das estratégias:
64,4% (58 de 90) com a primeira frase na tela em até 3 s, média de 2,82 s e p95 de 3,85 s.

Nas três, as etapas da aplicação (banco, Redis, preparo, montagem do prompt, envio pelo SSE) somam cerca
de 0,05 s e quase não mudaram. A diferença para a original, de 93,3%, está toda na espera da OpenAI até o
primeiro trecho: a mediana do envio até o primeiro trecho foi de 1,11 s na 01 e de 1,47 a 1,81 s nas
outras. A 01 rodou à 01h; as outras, entre 10h e 16h30, em horário comercial nos EUA. A variação entre
as três (73,3%, 63,3% e 56,7%) cabe no acaso de 30 amostras com a mediana perto dos 3 s: as médias foram
2,76, 2,76 e 2,95 s. A original continua na tabela só como referência.

O RNF-03 só conta como atingido com 95% em mais de uma execução.

### Diagnóstico da conexão com o provedor

O log do cliente HTTP do JDK mostra cada requisição, com a versão do protocolo e a porta local, e os
quadros de controle do HTTP/2 (abertura, `GOAWAY`, `RST_STREAM`). Ele sai no relatório do Surefire, em
`target/surefire-reports`, e é sobrescrito na execução seguinte:

```sh
./mvnw test -Dtest=PrimeiraFraseAprovadaComOpenAiTest -Dia.spike-primeira-frase=true -Djacoco.skip=true \
  -Djdk.httpclient.HttpClient.log=errors,requests,frames:control \
  -Dspike.estrategia="Diagnóstico: ..."
```

No `resultados09.md`, com a configuração final, as 178 requisições à OpenAI (31 gerações e 147
moderações) foram todas pela mesma conexão HTTP/2, aberta pelo aquecimento ao subir, sem nenhum `GOAWAY`
ou `RST_STREAM`. As requisições em HTTP/1.1 do log são as do próprio teste à aplicação.

## O que o teste faz

- Sobe a aplicação inteira, com a configuração de `application.properties`: o modelo, o esforço de
  raciocínio e o modelo de moderação são os mesmos da aplicação. O PostgreSQL e o Redis rodam em
  containers.
- Cadastra uma professora, faz login pela tela de login e inicia uma sessão no cenário "O voo do 14-bis",
  o mesmo dos testes do módulo sessoes.
- Envia as 30 perguntas de [perguntas.txt](perguntas.txt), em ordem, na mesma sessão, como numa aula. Cada
  pergunta vai por uma requisição HTTP igual à do HTMX. Quando o fragmento volta, o teste abre o canal SSE
  da resposta, como a tela.
- Mede a partir do clique em enviar, ou seja, do início da requisição da pergunta. O tempo da primeira
  frase é o da chegada do primeiro evento `trecho` do SSE, que só sai depois de a moderação aprovar a
  frase. Esse caminho inclui:
  1. a moderação da pergunta (passo 4);
  2. a gravação no banco e no Redis (passo 5);
  3. a volta do fragmento e a abertura do canal;
  4. o modelo gerando até fechar a primeira frase;
  5. a moderação dessa frase (passo 7).
- Um cronômetro recebe as observações que a aplicação já registra (Micrometer) e separa o tempo em
  etapas. Ele não muda o que a aplicação faz e serve só para mostrar onde o tempo foi gasto:

  | Observação | Etapas |
  | --- | --- |
  | `http.server.requests` | a requisição da pergunta e a do canal SSE, no servidor |
  | `lab.conversa.etapa` | conferência da sessão, gravação da pergunta, espera da thread, preparo da resposta, agrupamento de frases, envio pelo SSE, gravação da resposta |
  | `lab.sessoes.redis` | leitura e gravação do estado no Redis |
  | `lab.ia.chamada` | moderação, montagem do prompt, espera até o primeiro trecho, geração |

  A gravação da pergunta no banco é a transação dela menos o Redis gravado logo depois da confirmação. A
  abertura do canal vai do fragmento de volta no cliente até o servidor receber o pedido do SSE. No
  spike o cliente é o do teste; no navegador, entra ainda o tempo de o HTMX trocar o fragmento.
- As mesmas observações viram timers do Micrometer na aplicação, com os mesmos nomes, para acompanhar o
  RNF-03 fora do spike.
- Antes da medição, faz uma pergunta numa sessão à parte para aquecer a aplicação. Ela fica fora da
  conta, porque o servidor em produção já está rodando quando a aula começa. O tempo dela aparece no
  relatório.
- O aquecimento da conexão com o provedor ao subir (`ia.aquecimento.habilitado`) fica ligado, como na
  aplicação, e acontece antes da pergunta de aquecimento.
- Se uma pergunta ou resposta for bloqueada, a sessão é retomada e a medição segue. Uma resposta
  bloqueada antes da primeira frase fica fora da conta, porque por regra nada aparece (RN-20). Uma falha
  do provedor no meio da resposta conta como acima da meta.
- Se o aquecimento não chegar à tela, ou se uma pergunta for recusada por outro motivo que não o
  bloqueio, como a chave errada ou a moderação fora do ar, o teste falha na hora e não grava o
  relatório. O motivo aparece no erro do teste e no log do provedor de IA.

## Perguntas de referência

São 30 falas de aluno sobre o 14-bis. As 15 primeiras e as 10 últimas são perguntas de conteúdo, curtas
e longas. As de 16 a 20 são difíceis:

- perguntar se o personagem é uma IA (RN-14);
- pedir para esquecer as instruções e opinar sobre eleição (RNF-09);
- perguntar um fato que o personagem não sabe;
- perguntar algo que ele não poderia conhecer em 1906;
- puxar um assunto fora do tema (RN-15).

Nas perguntas difíceis o modelo costuma abrir com uma frase curta, o que pode mudar o tempo da
primeira frase.

## Limites do spike

- Não entra o navegador: o tempo de o HTMX trocar o fragmento, abrir o `EventSource` e desenhar o texto.
  São milissegundos perto das chamadas à OpenAI.
- Aplicação, banco e Redis rodam na mesma máquina, sem a rede entre a escola e a VM. Rodar de uma rede
  doméstica dá uma ideia da latência até a OpenAI, mas não é a rede da escola.
- São 30 respostas de uma execução. Com 30 amostras, 95% quer dizer no máximo uma resposta acima de 3 s.
  Vale rodar de novo em outro horário antes de uma decisão por pouca margem.
- O corte das frases é o do `AgrupadorDeFrases` da aplicação. O relatório mostra cada frase exibida,
  para conferir se o corte ficou certo.
