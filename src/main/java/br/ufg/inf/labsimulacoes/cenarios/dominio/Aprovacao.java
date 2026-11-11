package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.time.Instant;
import java.util.Objects;

/**
 * Quem aprovou a versão e quando. Na E1 a aprovação é só registrada, sem checklist (RN-06).
 *
 * @param curadorId curador que aprovou
 * @param em momento da aprovação
 */
public record Aprovacao(long curadorId, Instant em) {

    public Aprovacao {
        Objects.requireNonNull(em, "em");
    }
}
