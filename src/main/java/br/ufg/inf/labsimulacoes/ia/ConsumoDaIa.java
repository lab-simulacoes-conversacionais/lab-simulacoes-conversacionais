package br.ufg.inf.labsimulacoes.ia;

import java.time.Instant;

/**
 * Consumo da IA somado a partir dos metadados de cada resposta (RNF-13). O banco é a fonte da verdade:
 * os contadores do Micrometer recomeçam a cada subida da aplicação, e estes totais não.
 *
 * <p>Não confere perfil, porque é usada também pela conferência agendada do custo do dia, que roda sem
 * login; quem mostra os totais a alguém é que confere o perfil.
 */
public interface ConsumoDaIa {

    /**
     * Todo o consumo entre os dois instantes, de qualquer finalidade, inclusive o dos metadados gravados
     * antes de a finalidade existir.
     *
     * @param inicio incluído
     * @param fim excluído
     * @throws IllegalArgumentException se o início não vier antes do fim
     */
    ResumoDoConsumo custoEntre(Instant inicio, Instant fim);

    /**
     * O consumo de uma finalidade e uma referência, como o de uma sessão em sala ({@link
     * FinalidadeDoConsumo#FALA_EM_SALA} e o id da sessão). Entram as respostas exibidas, as bloqueadas e
     * as que vieram vazias, porque todas consumiram.
     */
    ResumoDoConsumo daReferencia(FinalidadeDoConsumo finalidade, long referenciaId);
}
