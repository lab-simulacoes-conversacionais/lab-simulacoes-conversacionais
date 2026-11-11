# Spike: comparativo de modelos da OpenAI (issue #14)

Escolhe o modelo da POC comparando qualidade em português, latência e custo, como pede a seção 2 da
[stack.md](../../stack.md). O modelo e o esforço de raciocínio escolhidos vão para
`spring.ai.openai.chat.options.model` e `spring.ai.openai.chat.options.reasoning-effort`, em
`src/main/resources/application.properties`. Nos ambientes, o modelo pode ser trocado pela variável
`IA_MODELO` do `.env` (RNF-02).

## Como rodar

Precisa de Java 21 e da variável `OPENAI_API_KEY` no terminal. Não há dependências nem build.

```sh
cd docs/spikes/comparativo-modelos
java Comparativo.java --so-conferir   # só confere se os modelos existem na conta
java Comparativo.java                 # roda o comparativo
```

Os preços ficam na constante `MODELOS`, no topo do script. São a tarifa padrão da
[página de preços da OpenAI](https://developers.openai.com/api/docs/pricing). Para ligar o
`gpt-6.1-sol`, troque o `false` dele por `true`.

## O que o script faz

- Antes de tudo, consulta a lista de modelos da conta. Se um modelo ligado não existir, ele para e
  lista os `gpt-6*` disponíveis.
- Usa o cenário "O voo do 14-bis" (o mesmo dos testes do módulo cenarios) e um roteiro fixo de 10 falas
  de aluno. Três delas são difíceis: perguntar se o personagem é uma IA (RN-14), pedir para esquecer as
  instruções e opinar sobre eleição (RN-15, RNF-09) e perguntar algo que ele não pode saber (RN-15).
- Roda 3 sessões por modelo pela Responses API, em streaming, com `store=false` (RN-34) e o histórico
  reenviado a cada turno.
- O esforço de raciocínio fica em `low`, porque o `gpt-6.1-sol` não aceita `none` nem `minimal`. O teto é
  de 2000 tokens de saída por resposta, somando o raciocínio. Uma resposta de cerca de 120 palavras
  (RN-16) tem uns 200 tokens.
- Para cada resposta, registra:
  - a partir do envio da pergunta: o tempo até o primeiro trecho de texto, até a primeira frase completa,
    até a primeira frase aprovada e o tempo total;
  - os tokens de entrada, de saída e de raciocínio;
  - o número de palavras;
  - se a resposta veio cortada (`status` diferente de `completed`);
  - se a pergunta ou a primeira frase foram sinalizadas pela moderação.
- Reproduz a moderação dos passos 4 e 7 do fluxo de uma pergunta ([stack.md](../../stack.md), seção 5):
  modera a pergunta antes de chamar o modelo e, assim que a primeira frase termina, modera essa frase
  em paralelo, sem parar o streaming. A moderação usa o `omni-moderation-latest`, que não é cobrado.
- Grava a conversa de cada sessão em `execucoes/<modelo>-rep<n>.md`, e gera `resultados.md` com:
  - a latência média e o p95 por resposta;
  - a marcação de p95 acima de 3 s na primeira frase aprovada e acima de 10 s no total (RNF-03);
  - os tokens e o custo médios por sessão;
  - as respostas cortadas, acima de 120 palavras ou sinalizadas pela moderação;
  - a tabela de notas de qualidade, para o grupo preencher.

Se a chave, o crédito ou o acesso ao modelo falharem, o script para e mostra o erro da API. Ele não
tenta outra coisa no lugar.

## Critérios de qualidade

Estão descritos no `resultados.md` gerado, com base no RN-14, no RN-15, no RN-16 e no princípio de
Transparência. O personagem **não deve negar** que é uma IA: a turma sempre sabe que conversa com um
personagem simulado.

## Limites do spike

- A primeira frase é detectada por pontuação final seguida de espaço, ou por quebra de linha. Uma
  abreviação como "Sr." fecha a frase antes da hora, o que deixa o tempo um pouco menor que o real.
- Ficam de fora o bloqueio de dados pessoais por regex (passo 3), a gravação no banco e o envio por SSE
  até a tela, que devem somar pouco perto das chamadas à OpenAI. O tempo total não inclui a moderação
  das frases seguintes.
- As instruções do personagem são provisórias. O template versionado do prompt é trabalho do módulo ia.
- Não há dados reais de alunos: o cenário e as falas são fictícios.
