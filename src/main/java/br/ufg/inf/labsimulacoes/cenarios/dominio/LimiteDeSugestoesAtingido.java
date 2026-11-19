package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.time.Duration;

/** RNF-13: o usuário já pediu todas as sugestões de cenário da janela. */
public class LimiteDeSugestoesAtingido extends RuntimeException {

    public LimiteDeSugestoesAtingido(int pedidosPorJanela, Duration janela) {
        super("Você já pediu " + pedidosPorJanela + " sugestões de cenário em " + janela.toMinutes()
                + " minutos. Tente de novo mais tarde.");
    }
}
