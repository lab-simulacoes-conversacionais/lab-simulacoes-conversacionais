package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.time.Duration;

/** Porta da contagem de pedidos de sugestão por usuário (RNF-13). A implementação fica na infraestrutura. */
public interface PedidosDeSugestao {

    /**
     * Conta mais um pedido do usuário na janela atual. A janela começa no primeiro pedido e, quando
     * acaba, a contagem volta a zero.
     *
     * @return quantos pedidos o usuário fez na janela atual, contando este
     */
    long registrar(long usuarioId, Duration janela);
}
