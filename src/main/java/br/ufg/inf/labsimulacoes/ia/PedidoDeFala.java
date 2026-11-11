package br.ufg.inf.labsimulacoes.ia;

import java.util.List;

/**
 * Pergunta da turma ao personagem. A pergunta vai sempre em uma mensagem separada das instruções,
 * nunca concatenada a elas (RNF-09).
 *
 * @param historico falas anteriores, da mais antiga para a mais recente, já cortadas no último
 *     reinício da sessão
 */
public record PedidoDeFala(CenarioDaConversa cenario, List<MensagemDaConversa> historico, String pergunta) {

    public PedidoDeFala {
        if (cenario == null) {
            throw new IllegalArgumentException("O cenário da conversa é obrigatório");
        }
        historico = historico == null ? List.of() : List.copyOf(historico);
        pergunta = Textos.obrigatorio(pergunta, "pergunta");
    }
}
