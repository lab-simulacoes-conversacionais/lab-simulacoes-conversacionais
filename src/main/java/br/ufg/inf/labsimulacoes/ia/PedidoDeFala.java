package br.ufg.inf.labsimulacoes.ia;

import java.util.List;

/**
 * Pergunta da turma ao personagem. A pergunta vai sempre em uma mensagem separada das instruções,
 * nunca concatenada a elas (RNF-09).
 *
 * @param historico falas anteriores, da mais antiga para a mais recente, já cortadas no último
 *     reinício da sessão
 * @param finalidade para que a fala foi pedida, gravada com o consumo (RNF-13); {@code null} quando
 *     quem chama não informou
 * @param referenciaId o que a finalidade identifica, como o id da sessão em {@link
 *     FinalidadeDoConsumo#FALA_EM_SALA}; só existe junto com a finalidade
 */
public record PedidoDeFala(
        CenarioDaConversa cenario,
        List<MensagemDaConversa> historico,
        String pergunta,
        FinalidadeDoConsumo finalidade,
        Long referenciaId) {

    public PedidoDeFala {
        if (cenario == null) {
            throw new IllegalArgumentException("O cenário da conversa é obrigatório");
        }
        historico = historico == null ? List.of() : List.copyOf(historico);
        pergunta = Textos.obrigatorio(pergunta, "pergunta");
        if (referenciaId != null && finalidade == null) {
            throw new IllegalArgumentException("A referência do consumo só existe junto com a finalidade");
        }
    }

    /** Pedido sem a finalidade do consumo, que fica nula nos metadados. */
    public PedidoDeFala(CenarioDaConversa cenario, List<MensagemDaConversa> historico, String pergunta) {
        this(cenario, historico, pergunta, null, null);
    }
}
