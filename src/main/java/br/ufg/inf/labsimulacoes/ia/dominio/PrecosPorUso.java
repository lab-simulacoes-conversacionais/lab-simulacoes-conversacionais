package br.ufg.inf.labsimulacoes.ia.dominio;

import br.ufg.inf.labsimulacoes.ia.FinalidadeDoConsumo;
import java.util.Objects;

/**
 * As tarifas de cada uso da IA (RNF-13). A fala do personagem, na sala e no teste do curador, roda com o
 * modelo e o nível de serviço da conversa (o prioritário, pelo RNF-03); a sugestão de cenário tem modelo
 * e nível próprios (o padrão). Cada uso é precificado pela tarifa do que ele de fato usa, para o custo
 * gravado, e com ele o alerta de custo diário, bater com o cobrado.
 *
 * @param fala tarifa da fala do personagem, na sala e no teste do curador
 * @param sugestao tarifa da sugestão de cenário
 */
public record PrecosPorUso(TabelaDePrecos fala, TabelaDePrecos sugestao) {

    public PrecosPorUso {
        Objects.requireNonNull(fala, "fala");
        Objects.requireNonNull(sugestao, "sugestao");
    }

    /** A tabela do uso. Sem finalidade informada, a chamada é uma fala, a única que pode vir sem ela. */
    public TabelaDePrecos para(FinalidadeDoConsumo finalidade) {
        return finalidade == FinalidadeDoConsumo.SUGESTAO ? sugestao : fala;
    }
}
