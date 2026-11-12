package br.ufg.inf.labsimulacoes.sessoes.dominio;

import java.time.Instant;
import java.util.Objects;

/**
 * Mensagem que aparece na tela de projeção.
 *
 * @param autoria sempre genérica: a turma ou a IA, nunca um estudante (RN-11)
 * @param em quando foi gravada
 * @param respostaBloqueada só na pergunta da turma: o filtro de conteúdo bloqueou a resposta a ela
 *     (RN-20), então ela não espera mais resposta
 */
public record MensagemExibida(Autoria autoria, String texto, Instant em, boolean respostaBloqueada) {

    public enum Autoria {
        TURMA,
        IA
    }

    public MensagemExibida {
        Objects.requireNonNull(autoria, "autoria");
        Objects.requireNonNull(texto, "texto");
        Objects.requireNonNull(em, "em");
        if (respostaBloqueada && autoria != Autoria.TURMA) {
            throw new IllegalArgumentException("Só a pergunta da turma tem a resposta bloqueada");
        }
    }

    public MensagemExibida(Autoria autoria, String texto, Instant em) {
        this(autoria, texto, em, false);
    }
}
