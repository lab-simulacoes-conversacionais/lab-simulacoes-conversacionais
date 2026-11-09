package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.List;
import java.util.Objects;

/**
 * Cenário de simulação (RF-06). Cada transição confere as regras antes de mudar o estado, para que
 * cada regra tenha um teste próprio, sem banco nem tela (Stack, seção 5).
 *
 * @param versao a versão mais recente
 */
public record Cenario(Long id, long autorId, StatusDoCenario status, VersaoDoCenario versao) {

    public Cenario {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(versao, "versao");
    }

    public static Cenario novo(long autorId, ConteudoDoCenario conteudo) {
        return new Cenario(null, autorId, StatusDoCenario.RASCUNHO, VersaoDoCenario.primeira(autorId, conteudo));
    }

    public boolean temAutor(long usuarioId) {
        return autorId == usuarioId;
    }

    /** Mantém o número da versão: só o que já saiu do rascunho vira histórico. */
    public Cenario alterarRascunho(ConteudoDoCenario conteudo) {
        exigirRascunho();
        return new Cenario(id, autorId, status, versao.comConteudo(conteudo));
    }

    /**
     * @throws CenarioIncompleto com tudo o que falta, para o autor corrigir de uma vez (RN-02, RN-03)
     */
    public Cenario enviarACuradoria() {
        exigirRascunho();
        List<PendenciaDoCenario> pendencias = versao.conteudo().pendencias();
        if (!pendencias.isEmpty()) {
            throw new CenarioIncompleto(pendencias);
        }
        return new Cenario(id, autorId, StatusDoCenario.EM_CURADORIA, versao.emCuradoria());
    }

    /** Olha a versão, e não o cenário, porque a edição de um aprovado gera versão nova em rascunho (RN-08). */
    private void exigirRascunho() {
        if (!versao.emRascunho()) {
            throw new CenarioForaDeRascunho(id, versao.status());
        }
    }
}
