package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.time.Instant;
import java.util.Objects;

/**
 * O que Cenários sabe de uma sinalização feita numa versão do cenário, pelos eventos da Auditoria (RF-25). Só
 * ids e códigos: a pergunta, a resposta e a justificativa ficam na Auditoria (RNF-01). As regras de gravidade
 * (RN-23) decidem a suspensão a partir das sinalizações da versão vigente.
 *
 * @param sinalizacaoId o id da sinalização na Auditoria
 * @param versaoId a versão do cenário em que a resposta saiu
 * @param gravidadeFinal presente só na confirmada
 * @param triadaPorId o curador que triou; ausente na pendente
 * @param recebidaEm quando a sinalização chegou a Cenários; se a triagem chegou antes do registro, o momento
 *     da triagem
 * @param triadaEm ausente na pendente
 */
public record SinalizacaoDaVersao(
        long sinalizacaoId,
        long versaoId,
        PerfilDeQuemSinalizou perfil,
        GravidadeDoErro gravidadePercebida,
        SituacaoDaSinalizacao situacao,
        GravidadeDoErro gravidadeFinal,
        Long triadaPorId,
        Instant recebidaEm,
        Instant triadaEm) {

    public SinalizacaoDaVersao {
        Objects.requireNonNull(perfil, "perfil");
        Objects.requireNonNull(gravidadePercebida, "gravidadePercebida");
        Objects.requireNonNull(situacao, "situacao");
        Objects.requireNonNull(recebidaEm, "recebidaEm");
        boolean triada = situacao != SituacaoDaSinalizacao.PENDENTE;
        if (triada != (triadaPorId != null) || triada != (triadaEm != null)) {
            throw new IllegalArgumentException("Só a sinalização triada tem quem triou e quando, e ela sempre tem");
        }
        if ((situacao == SituacaoDaSinalizacao.CONFIRMADA) != (gravidadeFinal != null)) {
            throw new IllegalArgumentException("Só a sinalização confirmada tem gravidade final, e ela sempre tem");
        }
    }

    /** Recebida pela {@code SinalizacaoRegistrada}, antes da triagem. */
    public static SinalizacaoDaVersao pendente(
            long sinalizacaoId, long versaoId, PerfilDeQuemSinalizou perfil, GravidadeDoErro percebida,
            Instant recebidaEm) {
        return new SinalizacaoDaVersao(
                sinalizacaoId, versaoId, perfil, percebida, SituacaoDaSinalizacao.PENDENTE, null, null, recebidaEm,
                null);
    }

    /**
     * Recebida pela {@code SinalizacaoTriada} com a decisão de confirmar.
     *
     * @param recebidaEm usado só se o registro ainda não tiver chegado
     */
    public static SinalizacaoDaVersao confirmada(
            long sinalizacaoId, long versaoId, PerfilDeQuemSinalizou perfil, GravidadeDoErro percebida,
            GravidadeDoErro gravidadeFinal, long triadaPorId, Instant recebidaEm, Instant triadaEm) {
        return new SinalizacaoDaVersao(
                sinalizacaoId, versaoId, perfil, percebida, SituacaoDaSinalizacao.CONFIRMADA,
                Objects.requireNonNull(gravidadeFinal, "gravidadeFinal"), triadaPorId, recebidaEm, triadaEm);
    }

    /**
     * Recebida pela {@code SinalizacaoTriada} com a decisão de descartar.
     *
     * @param recebidaEm usado só se o registro ainda não tiver chegado
     */
    public static SinalizacaoDaVersao descartada(
            long sinalizacaoId, long versaoId, PerfilDeQuemSinalizou perfil, GravidadeDoErro percebida,
            long triadaPorId, Instant recebidaEm, Instant triadaEm) {
        return new SinalizacaoDaVersao(
                sinalizacaoId, versaoId, perfil, percebida, SituacaoDaSinalizacao.DESCARTADA, null, triadaPorId,
                recebidaEm, triadaEm);
    }

    public boolean pendente() {
        return situacao == SituacaoDaSinalizacao.PENDENTE;
    }
}
