package br.ufg.inf.labsimulacoes.eventos;

import java.time.Instant;
import java.util.Objects;

/**
 * O curador confirmou ou descartou uma sinalização (RF-24, RN-22). Publicado pela Auditoria. Ainda não tem
 * quem escute: com a RF-25, Cenários mantém ou retira a suspensão (RN-23b a d), e Métricas usa as
 * confirmadas na taxa de erros (RN-29).
 *
 * <p>Como a {@link SinalizacaoRegistrada}, não leva a pergunta, a resposta nem a justificativa.
 *
 * @param sinalizacaoId a sinalização triada, a mesma da {@link SinalizacaoRegistrada}
 * @param versaoId a versão do cenário em que a resposta saiu
 * @param decisao {@code CONFIRMADA} ou {@code DESCARTADA}
 * @param gravidadeFinal {@code LEVE}, {@code MODERADA} ou {@code GRAVE} na confirmada; nula na descartada
 * @param gravidadePercebida a de quem sinalizou, para saber se o descarte desfaz uma suspensão (RN-23d)
 * @param perfil {@code PROFESSOR} ou {@code CURADOR}, de quem sinalizou
 * @param triadaPorId o curador que triou
 * @param ocorridoEm momento da triagem
 */
public record SinalizacaoTriada(
        long sinalizacaoId,
        long versaoId,
        String decisao,
        String gravidadeFinal,
        String gravidadePercebida,
        String perfil,
        long triadaPorId,
        Instant ocorridoEm) {

    public SinalizacaoTriada {
        Objects.requireNonNull(decisao, "decisao");
        Objects.requireNonNull(gravidadePercebida, "gravidadePercebida");
        Objects.requireNonNull(perfil, "perfil");
        Objects.requireNonNull(ocorridoEm, "ocorridoEm");
        if ("CONFIRMADA".equals(decisao) != (gravidadeFinal != null)) {
            throw new IllegalArgumentException("Só a sinalização confirmada tem gravidade final");
        }
    }

    public boolean confirmada() {
        return "CONFIRMADA".equals(decisao);
    }
}
