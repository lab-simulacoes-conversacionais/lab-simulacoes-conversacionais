package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.Objects;

/**
 * Snapshot do conteúdo de um cenário. Só muda enquanto está em rascunho (Stack, seção 6).
 *
 * @param aprovacao quem aprovou e quando; {@code null} enquanto a versão não foi aprovada
 */
public record VersaoDoCenario(
        Long id, int numero, StatusDaVersao status, ConteudoDoCenario conteudo, long criadaPorId,
        Aprovacao aprovacao) {

    private static final int PRIMEIRO_NUMERO = 1;

    public VersaoDoCenario {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(conteudo, "conteudo");
    }

    /** Versão que ainda não foi aprovada. */
    public VersaoDoCenario(Long id, int numero, StatusDaVersao status, ConteudoDoCenario conteudo, long criadaPorId) {
        this(id, numero, status, conteudo, criadaPorId, null);
    }

    static VersaoDoCenario primeira(long autorId, ConteudoDoCenario conteudo) {
        return new VersaoDoCenario(null, PRIMEIRO_NUMERO, StatusDaVersao.RASCUNHO, conteudo, autorId);
    }

    boolean emRascunho() {
        return status == StatusDaVersao.RASCUNHO;
    }

    boolean emCuradoria() {
        return status == StatusDaVersao.EM_CURADORIA;
    }

    VersaoDoCenario comConteudo(ConteudoDoCenario novo) {
        return new VersaoDoCenario(id, numero, status, novo, criadaPorId, aprovacao);
    }

    VersaoDoCenario enviada() {
        return comStatus(StatusDaVersao.EM_CURADORIA, aprovacao);
    }

    /** RN-08: a versão aprovada passa a ser a vigente e não muda mais. */
    VersaoDoCenario aprovada(Aprovacao novaAprovacao) {
        return comStatus(StatusDaVersao.APROVADA, novaAprovacao);
    }

    /** RN-07: a mesma versão volta a rascunho, para o autor corrigir sem perder o número. */
    VersaoDoCenario devolvida() {
        return comStatus(StatusDaVersao.RASCUNHO, aprovacao);
    }

    private VersaoDoCenario comStatus(StatusDaVersao novo, Aprovacao comAprovacao) {
        return new VersaoDoCenario(id, numero, novo, conteudo, criadaPorId, comAprovacao);
    }
}
