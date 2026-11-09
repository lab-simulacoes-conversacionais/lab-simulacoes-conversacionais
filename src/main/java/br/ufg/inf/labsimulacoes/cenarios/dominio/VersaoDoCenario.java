package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.Objects;

/** Snapshot do conteúdo de um cenário. Só muda enquanto está em rascunho (Stack, seção 6). */
public record VersaoDoCenario(
        Long id, int numero, StatusDaVersao status, ConteudoDoCenario conteudo, long criadaPorId) {

    private static final int PRIMEIRO_NUMERO = 1;

    public VersaoDoCenario {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(conteudo, "conteudo");
    }

    static VersaoDoCenario primeira(long autorId, ConteudoDoCenario conteudo) {
        return new VersaoDoCenario(null, PRIMEIRO_NUMERO, StatusDaVersao.RASCUNHO, conteudo, autorId);
    }

    boolean emRascunho() {
        return status == StatusDaVersao.RASCUNHO;
    }

    VersaoDoCenario comConteudo(ConteudoDoCenario novo) {
        return new VersaoDoCenario(id, numero, status, novo, criadaPorId);
    }

    VersaoDoCenario emCuradoria() {
        return new VersaoDoCenario(id, numero, StatusDaVersao.EM_CURADORIA, conteudo, criadaPorId);
    }
}
