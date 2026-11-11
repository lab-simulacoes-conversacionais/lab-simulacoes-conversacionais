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
  - o tempo até o primeiro trecho de texto e o tempo total;
  - os tokens de entrada, de saída e de raciocínio;
  - o número de palavras;
  - se a resposta veio cortada (`status` diferente de `completed`).
- Grava a conversa de cada sessão em `execucoes/<modelo>-rep<n>.md`, e gera `resultados.md` com:
  - a latência média e o p95 por resposta;
  - a marcação de p95 acima de 2 s no primeiro trecho (margem para a moderação) e acima de 10 s no total;
  - os tokens e o custo médios por sessão;
  - as respostas cortadas ou acima de 120 palavras;
  - a tabela de notas de qualidade, para o grupo preencher.

Se a chave, o crédito ou o acesso ao modelo falharem, o script para e mostra o erro da API. Ele não
tenta outra coisa no lugar.

## Critérios de qualidade

Estão descritos no `resultados.md` gerado, com base no RN-14, no RN-15, no RN-16 e no princípio de
Transparência. O personagem **não deve negar** que é uma IA: a turma sempre sabe que conversa com um
personagem simulado.

## Limites do spike

- O tempo medido é só o do modelo. O RNF-03 conta até a primeira frase já moderada, por isso a marca
  de 2 s no primeiro trecho.
- As instruções do personagem são provisórias. O template versionado do prompt é trabalho do módulo ia.
- Não há dados reais de alunos: o cenário e as falas são fictícios.
