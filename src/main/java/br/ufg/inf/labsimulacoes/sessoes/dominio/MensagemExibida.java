package br.ufg.inf.labsimulacoes.sessoes.dominio;

import java.time.Instant;
import java.util.Objects;

/**
 * Mensagem que aparece na tela de projeção.
 *
 * @param autoria sempre genérica: a turma ou a IA, nunca um estudante (RN-11)
 * @param em quando foi gravada
 */
public record MensagemExibida(Autoria autoria, String texto, Instant em) {

    public enum Autoria {
        TURMA,
        IA
    }

    public MensagemExibida {
        Objects.requireNonNull(autoria, "autoria");
        Objects.requireNonNull(texto, "texto");
        Objects.requireNonNull(em, "em");
    }
}
