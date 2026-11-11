package br.ufg.inf.labsimulacoes.sessoes.dominio;

import java.util.Optional;

/**
 * Porta do estado quente das sessões, uma chave por sessão no Redis (Stack, seção 5). Nada existe só
 * aqui: o estado pode sumir a qualquer momento e é refeito a partir do PostgreSQL.
 */
public interface EstadosDasSessoes {

    /** Vazio se a chave expirou, sumiu ou não pôde ser lida. */
    Optional<EstadoDaSessao> buscar(long sessaoId);

    /**
     * Grava o estado depois que a transação em curso for confirmada, para o Redis nunca mostrar uma
     * mudança que o PostgreSQL desfez. Sem transação, grava na hora.
     */
    void gravar(EstadoDaSessao estado);
}
