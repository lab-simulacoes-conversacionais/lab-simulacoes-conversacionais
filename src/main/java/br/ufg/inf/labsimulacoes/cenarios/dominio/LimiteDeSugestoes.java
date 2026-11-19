package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.time.Duration;
import java.util.Objects;

/**
 * RNF-13: quantas sugestões de cenário cada usuário pede por janela de tempo. Cada pedido gasta tokens
 * do provedor, então um professor clicando em loop é recusado antes da chamada.
 *
 * @param pedidosPorJanela quantos pedidos cabem em uma janela
 * @param janela a janela, contada a partir do primeiro pedido dela
 */
public record LimiteDeSugestoes(int pedidosPorJanela, Duration janela) {

    public LimiteDeSugestoes {
        if (pedidosPorJanela < 1) {
            throw new IllegalArgumentException("O limite precisa aceitar ao menos um pedido: " + pedidosPorJanela);
        }
        Objects.requireNonNull(janela, "janela");
        if (janela.isNegative() || janela.isZero()) {
            throw new IllegalArgumentException("A janela do limite precisa ser positiva: " + janela);
        }
    }

    /**
     * @param pedidosNaJanela quantos pedidos o usuário fez na janela atual, contando este
     * @throws LimiteDeSugestoesAtingido se este pedido passar do limite
     */
    public void exigirDentro(long pedidosNaJanela) {
        if (pedidosNaJanela > pedidosPorJanela) {
            throw new LimiteDeSugestoesAtingido(pedidosPorJanela, janela);
        }
    }
}
