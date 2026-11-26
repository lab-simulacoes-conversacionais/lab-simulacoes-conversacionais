package br.ufg.inf.labsimulacoes.eventos;

import java.time.Instant;
import java.util.Objects;

/**
 * Um professor ou um curador sinalizou uma resposta da IA como possível erro (RF-23, RN-21). Publicado
 * pela Auditoria. Ainda não tem quem escute: com a RF-25, Cenários e Sessões reagem à sinalização grave
 * do professor, que suspende o cenário e pausa as sessões em andamento (RN-23a, RN-25).
 *
 * <p>Não leva a pergunta, a resposta nem a justificativa, para nenhum texto digitado ou gerado sair da
 * Auditoria por evento; quem precisar deles lê a sinalização pelo id.
 *
 * @param sinalizacaoId a sinalização gravada
 * @param sessaoId a sessão em que a resposta saiu
 * @param versaoId a versão do cenário em uso na sessão, que a RN-23 suspende
 * @param tipo {@code ERRO_HISTORICO}, {@code ERRO_CIENTIFICO}, {@code ANACRONISMO},
 *     {@code SAIDA_DO_PERSONAGEM} ou {@code CONTEUDO_INADEQUADO}
 * @param gravidadePercebida {@code LEVE}, {@code MODERADA} ou {@code GRAVE}
 * @param perfil {@code PROFESSOR} ou {@code CURADOR}; só a sinalização grave do professor suspende o
 *     cenário na hora (RN-23a)
 * @param sinalizadoPorId quem sinalizou
 * @param ocorridoEm momento da sinalização
 */
public record SinalizacaoRegistrada(
        long sinalizacaoId,
        long sessaoId,
        long versaoId,
        String tipo,
        String gravidadePercebida,
        String perfil,
        long sinalizadoPorId,
        Instant ocorridoEm) {

    public SinalizacaoRegistrada {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(gravidadePercebida, "gravidadePercebida");
        Objects.requireNonNull(perfil, "perfil");
        Objects.requireNonNull(ocorridoEm, "ocorridoEm");
    }
}
