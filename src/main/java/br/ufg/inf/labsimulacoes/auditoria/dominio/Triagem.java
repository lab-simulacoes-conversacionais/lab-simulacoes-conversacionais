package br.ufg.inf.labsimulacoes.auditoria.dominio;

import java.time.Instant;
import java.util.Objects;

/**
 * A decisão do curador sobre uma sinalização pendente (RF-24). A confirmada exige a gravidade final, Leve,
 * Moderada ou Grave; a descartada não tem gravidade (RN-22). Uma sinalização é triada uma vez só.
 *
 * @param gravidade a gravidade final; nula só na descartada
 */
public record Triagem(
        long sinalizacaoId, DecisaoDaTriagem decisao, GravidadeFinal gravidade, long triadaPorId, Instant triadaEm) {

    public Triagem {
        Objects.requireNonNull(decisao, "decisao");
        Objects.requireNonNull(triadaEm, "triadaEm");
        if (decisao == DecisaoDaTriagem.CONFIRMADA && gravidade == null) {
            throw new IllegalArgumentException("Escolha a gravidade final do erro confirmado");
        }
        if (decisao == DecisaoDaTriagem.DESCARTADA && gravidade != null) {
            throw new IllegalArgumentException("A sinalização descartada não tem gravidade final");
        }
    }

    /** O curador confirma o erro com a gravidade final (RN-22). */
    public static Triagem confirmar(long sinalizacaoId, GravidadeFinal gravidade, long curadorId, Instant em) {
        return new Triagem(sinalizacaoId, DecisaoDaTriagem.CONFIRMADA, gravidade, curadorId, em);
    }

    /** O curador descarta a sinalização: não há erro. */
    public static Triagem descartar(long sinalizacaoId, long curadorId, Instant em) {
        return new Triagem(sinalizacaoId, DecisaoDaTriagem.DESCARTADA, null, curadorId, em);
    }

    public boolean confirmada() {
        return decisao == DecisaoDaTriagem.CONFIRMADA;
    }
}
