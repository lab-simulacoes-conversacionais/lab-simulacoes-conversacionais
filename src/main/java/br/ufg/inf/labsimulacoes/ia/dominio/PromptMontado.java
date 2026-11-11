package br.ufg.inf.labsimulacoes.ia.dominio;

import java.util.List;

/**
 * O que vai ao modelo: as instruções na mensagem de sistema e, depois delas, as mensagens da conversa.
 * O texto digitado pela turma fica sempre em mensagens de usuário, nunca dentro das instruções (RNF-09).
 *
 * @param versao versão do template, gravada nos metadados de cada resposta
 * @param mensagens a última é sempre do usuário: é o pedido que o modelo responde
 */
public record PromptMontado(String versao, String instrucoes, List<MensagemDoPrompt> mensagens) {

    public PromptMontado {
        if (versao == null || versao.isBlank()) {
            throw new IllegalArgumentException("A versão do prompt é obrigatória");
        }
        if (instrucoes == null || instrucoes.isBlank()) {
            throw new IllegalArgumentException("As instruções do prompt são obrigatórias");
        }
        mensagens = mensagens == null ? List.of() : List.copyOf(mensagens);
        if (mensagens.isEmpty() || mensagens.getLast().papel() != MensagemDoPrompt.Papel.USUARIO) {
            throw new IllegalArgumentException("O prompt precisa terminar com uma mensagem do usuário");
        }
    }
}
