# Prompts versionados

Templates dos prompts do Adaptador de IA, guardados como artefatos fora do código (RNF-11). A versão
de cada template é gravada em `resposta_ia_meta.versao_prompt` em toda resposta gerada (RNF-10).

| Arquivo | Uso | Propriedade que escolhe a versão |
| --- | --- | --- |
| `personagem-v1.md` | Falas do personagem em sala (RF-16) | `ia.prompt.personagem` |
| `sugestao-v1.md` | Sugestão de cenário pela IA (RN-04) | `ia.prompt.sugestao` |

## Convenção de versões

- **Uma versão já publicada não é editada.** Para mudar um prompt, crie um arquivo novo com a próxima
  versão (por exemplo, `personagem-v2.md`) e aponte a propriedade para ela. Assim, a versão gravada
  nos metadados sempre corresponde ao texto que gerou a resposta.
- O nome do arquivo é a versão, e o cabeçalho repete a mesma versão. A aplicação não sobe se os dois
  forem diferentes, ou se a versão configurada não tiver arquivo.
- As versões usam só letras minúsculas, números e hífen.

## Formato

```
---
versao: personagem-v1
---

## instrucoes

Texto da mensagem de sistema, com marcadores como {{personagem}}.

## abertura

Texto acrescentado às instruções só na primeira fala da sessão.
```

- Cada linha `## nome` abre uma seção. Por isso, o texto de uma seção não pode ter linhas começando
  por `## `. Não pode haver texto fora das seções.
- O personagem precisa das seções `instrucoes` e `abertura`. A sugestão precisa só de `instrucoes`.
- A seção `abertura` entra na mensagem de sistema só quando o histórico está vazio: no início da
  sessão e depois de reiniciar. Assim, a primeira fala apresenta o personagem como simulação (RN-14).

## Marcadores do personagem

| Marcador | Valor |
| --- | --- |
| `{{titulo}}` | Título do cenário |
| `{{personagem}}` | Nome do personagem |
| `{{papel_da_ia}}` | Como a IA age no papel |
| `{{contexto}}` | Lugar e época da conversa |
| `{{objetivos}}` | Objetivos de aprendizagem, um por linha com `- ` |
| `{{limites_de_assunto}}` | O que não abordar, um por linha com `- ` |
| `{{fontes}}` | Fontes de referência, uma por linha com `- ` |
| `{{componente}}` | Componente curricular |
| `{{ano}}` | Ano escolar, só o número |
| `{{habilidades}}` | Códigos BNCC separados por vírgula |
| `{{faixa_etaria}}` | Faixa etária da turma |

Todos os campos são obrigatórios num cenário aprovado (RN-02, RN-03). Um campo vazio faz a montagem
falhar com o nome do campo, em vez de deixar uma lacuna no prompt.

- Um marcador desconhecido, ou malformado (`{{` sem o fechamento), impede a aplicação de subir.
- O preenchimento é feito em uma passagem só: um `{{...}}` dentro dos dados do cenário fica como texto
  e não é preenchido de novo.
- A pergunta da turma e o histórico nunca entram no template: vão em mensagens separadas (RNF-09).
- O template da sugestão não tem marcadores. O pedido do professor vai na mensagem de usuário.
