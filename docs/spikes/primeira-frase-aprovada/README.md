# Spike: primeira frase aprovada na tela (issues #19 e #80)

Confere se a moderação frase a frase cumpre o RNF-03, como pede a seção 5 da [stack.md](../../stack.md)
(fluxo de uma pergunta, passo 7). O RN-20 exige moderar antes de exibir, e o RNF-03 exige o primeiro
trecho em até 3 s. O critério é ter **95% das 30 perguntas de referência com a primeira frase aprovada
na tela em até 3 s**.

- **Se atingir:** a moderação por frase continua.
- **Se não atingir:** a moderação passa a ser da resposta inteira, com o aviso "personagem pensando", e o
  grupo leva ao PO a revisão do RNF-03.

A primeira medição (`resultados01.md`) ficou em 93,3%. Antes de trocar a moderação, a issue #80 tenta
reduzir a latência mantendo a moderação por frase. Cada estratégia é medida com este spike, num relatório
próprio, e comparada à medição original.

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

Cada relatório diz qual estratégia de latência foi medida. Sem a propriedade, a execução é uma nova linha
de base, com os mesmos parâmetros da original:

```sh
./mvnw test -Dtest=PrimeiraFraseAprovadaComOpenAiTest -Dia.spike-primeira-frase=true -Djacoco.skip=true \
  -Dspike.estrategia="A: conexão de streaming aquecida ao subir"
```

O relatório traz uma tabela que compara a execução com o `resultados01.md`: a proporção dentro da meta,
a média, o p95 e o máximo da primeira frase na tela, e as etapas principais. Os números da original
ficam copiados no teste, para a comparação continuar valendo mesmo que o formato do relatório mude.

Para estabelecer a variação natural da latência, a linha de base é repetida em horários diferentes,
sem mudar nada no código nem nos parâmetros. O RNF-03 só conta como atingido com 95% em mais de uma
execução.

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
