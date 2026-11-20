package br.ufg.inf.labsimulacoes.cenarios.dominio;

/** RNF-13: o curador já fez todas as perguntas de teste que cabem nesta versão. */
public class LimiteDoTesteAtingido extends RuntimeException {

    public LimiteDoTesteAtingido(int perguntasPorVersao) {
        super("Você já fez " + perguntasPorVersao + " perguntas de teste nesta versão do cenário.");
    }
}
