package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.time.Instant;
import java.util.Objects;

/**
 * Devolução de uma versão ao autor, com o que precisa mudar (RN-07).
 *
 * @param id {@code null} enquanto não foi gravada
 * @param curadorId curador que devolveu
 * @param comentario o que precisa mudar, sem espaços nas pontas
 * @param em momento da devolução
 */
public record Devolucao(Long id, long curadorId, String comentario, Instant em) {

    public Devolucao {
        if (comentario == null || comentario.isBlank()) {
            throw new ComentarioObrigatorio();
        }
        comentario = comentario.strip();
        Objects.requireNonNull(em, "em");
    }

    static Devolucao nova(long curadorId, String comentario, Instant em) {
        return new Devolucao(null, curadorId, comentario, em);
    }

    public Devolucao comId(long novoId) {
        return new Devolucao(novoId, curadorId, comentario, em);
    }
}
