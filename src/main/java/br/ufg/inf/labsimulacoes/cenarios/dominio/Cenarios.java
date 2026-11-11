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

    /**
     * Grava o cenário e a versão mais recente. Uma devolução ainda sem identificador é gravada como
     * nova, e a versão aprovada passa a ser a vigente do cenário (RN-08).
     */
    Cenario salvar(Cenario cenario);

    /**
     * A versão aprovada vigente, só se o cenário estiver Aprovado (RN-01): rascunho, em curadoria,
     * suspenso ou arquivado não vai para a sala.
     */
    Optional<VersaoDoCenario> versaoVigente(long cenarioId);

    /**
     * Biblioteca (RF-12): os cenários Aprovados com a versão vigente, de todos os autores, pelo mesmo
     * critério de {@link #versaoVigente}. Em ordem de título, com desempate pelo id do cenário, para
     * que a mesma página traga sempre os mesmos cenários.
     */
    TrechoDaBiblioteca listarAprovados(Pagina pagina);

    /**
     * Acervo do curador (RN-37): os cenários de todos os autores, cada um com a versão mais recente,
     * na mesma ordem da biblioteca (título da versão e id do cenário).
     *
     * @param status só os cenários com este status; {@code null} traz todos
     */
    TrechoDoAcervo listarTodos(StatusDoCenario status, Pagina pagina);
}
