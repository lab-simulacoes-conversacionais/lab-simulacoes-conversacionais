package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.Optional;

/** Porta de persistência dos cenários. A implementação fica na infraestrutura. */
public interface Cenarios {

    /** Traz o cenário com a versão mais recente. */
    Optional<Cenario> buscar(long id);

    /**
     * Como {@link #buscar}, mas segura o cenário até o fim da transação. Sem isso, uma edição podia
     * sobrescrever a versão que acabou de ir à curadoria.
     */
    Optional<Cenario> buscarParaAlterar(long id);

    Cenario salvar(Cenario cenario);
}
