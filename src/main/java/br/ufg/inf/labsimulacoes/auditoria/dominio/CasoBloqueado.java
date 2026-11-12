package br.ufg.inf.labsimulacoes.auditoria.dominio;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Caso aberto na fila do curador quando o filtro de conteúdo bloqueia uma pergunta ou uma frase da
 * resposta (RN-20). Guarda só o que a triagem precisa (RF-24): de onde veio, os textos copiados, as
 * categorias e, na resposta, os metadados do modelo.
 *
 * @param trechoBloqueado a frase bloqueada; presente só quando a origem é a resposta
 * @param respostaIaMetaId ausente na pergunta e quando o provedor falhou antes de terminar a resposta
 */
public record CasoBloqueado(
        long sessaoId,
        long versaoId,
        Origem origem,
        String pergunta,
        String trechoBloqueado,
        List<String> categorias,
        Long respostaIaMetaId,
        Instant ocorridoEm) {

    public enum Origem {
        PERGUNTA,
        RESPOSTA
    }

    /** Todo caso entra aberto; os outros status vêm com a triagem (RF-24). */
    public static final String ABERTO = "ABERTO";

    public CasoBloqueado {
        Objects.requireNonNull(origem, "origem");
        Objects.requireNonNull(pergunta, "pergunta");
        Objects.requireNonNull(ocorridoEm, "ocorridoEm");
        if ((origem == Origem.RESPOSTA) != (trechoBloqueado != null)) {
            throw new IllegalArgumentException("Só o caso da resposta tem trecho bloqueado, e ele sempre tem");
        }
        categorias = categorias == null ? List.of() : List.copyOf(categorias);
    }
}
