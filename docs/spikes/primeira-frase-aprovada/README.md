# Spike: primeira frase aprovada na tela (issue #19)

Confere se a moderação frase a frase cumpre o RNF-03, como pede a seção 5 da [stack.md](../../stack.md)
(fluxo de uma pergunta, passo 7). O RN-20 exige moderar antes de exibir, e o RNF-03 exige o primeiro
trecho em até 3 s. O critério é ter **95% das 30 perguntas de referência com a primeira frase aprovada
na tela em até 3 s**.

- **Se atingir:** a moderação por frase continua.
- **Se não atingir:** a moderação passa a ser da resposta inteira, com o aviso "personagem pensando", e o
  grupo leva ao PO a revisão do RNF-03.

## Como rodar

Precisa de Java 21, Docker (para o PostgreSQL e o Redis dos testes) e da variável `OPENAI_API_KEY` no
terminal. Leva uns 7 minutos e gasta poucos centavos de dólar.

```sh
export OPENAI_API_KEY=...
./mvnw test -Dtest=PrimeiraFraseAprovadaComOpenAiTest -Dia.spike-primeira-frase=true -Djacoco.skip=true
```

O teste fica em `src/test/java/.../sessoes/apresentacao/PrimeiraFraseAprovadaComOpenAiTest.java`. Sem a
propriedade e a chave, ele não roda, então não entra no `verify` nem no CI. O intervalo entre uma
resposta e a próxima pergunta é de 10 s, para imitar o professor digitando. Ele pode ser trocado com
`-Dspike.intervalo-s=<segundos>`.

O resultado vai para `resultados.md`, nesta pasta, e o resumo de cada pergunta aparece no terminal
enquanto roda.

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
- Um cronômetro em volta do adaptador de IA marca a moderação da pergunta, o primeiro trecho do modelo,
  o fim da primeira frase e a moderação dela. Ele não muda o que o adaptador faz e serve só para mostrar
  onde o tempo foi gasto se o critério não for atingido.
- Antes da medição, faz uma pergunta numa sessão à parte para aquecer a aplicação. Ela fica fora da
  conta, porque o servidor em produção já está rodando quando a aula começa. O tempo dela aparece no
  relatório.
- Se uma pergunta ou resposta for bloqueada, a sessão é retomada e a medição segue. Uma resposta
  bloqueada antes da primeira frase fica fora da conta, porque por regra nada aparece (RN-20). Uma falha
  do provedor conta como acima da meta.

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
