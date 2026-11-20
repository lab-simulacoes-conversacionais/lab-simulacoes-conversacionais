package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.List;
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
     * Grava o cenário e a versão mais recente. Uma versão ou devolução ainda sem identificador é gravada
     * como nova, e a versão aprovada passa a ser a vigente do cenário (RN-08).
     */
    Cenario salvar(Cenario cenario);

    /**
     * A versão aprovada vigente, só se o cenário estiver Aprovado (RN-01): rascunho, em curadoria,
     * suspenso ou arquivado não vai para a sala.
     */
    Optional<VersaoDoCenario> versaoVigente(long cenarioId);

    /**
     * Uma versão aprovada pelo id, seja ou não a vigente, qualquer que seja o status atual do cenário.
     * Vazio se a versão não existir ou não tiver sido aprovada.
     */
    Optional<VersaoAprovadaDoCenario> versaoAprovada(long versaoId);

    /**
     * Biblioteca (RF-12): os cenários Aprovados com a versão vigente, de todos os autores, pelo mesmo
     * critério de {@link #versaoVigente}, que atendem a todos os filtros da busca. Em ordem de título,
     * com desempate pelo id do cenário, para que a mesma página traga sempre os mesmos cenários.
     */
    TrechoDaBiblioteca listarAprovados(BuscaNaBiblioteca busca, Pagina pagina);

    /** A biblioteca inteira, sem filtro. */
    default TrechoDaBiblioteca listarAprovados(Pagina pagina) {
        return listarAprovados(BuscaNaBiblioteca.TUDO, pagina);
    }

    /** Componentes, anos e habilidades das versões vigentes da biblioteca, para os filtros (RF-12). */
    OpcoesDaBiblioteca opcoesDaBiblioteca();

    /**
     * Acervo do curador (RN-37): os cenários de todos os autores, cada um com a versão mais recente,
     * na mesma ordem da biblioteca (título da versão e id do cenário).
     *
     * @param status só os cenários com este status; {@code null} traz todos. Em curadoria olha a versão
     *     mais recente, e não o cenário: assim a fila do curador traz também a versão nova de um cenário
     *     que continua Aprovado enquanto ela é revisada (RN-08)
     */
    TrechoDoAcervo listarTodos(StatusDoCenario status, Pagina pagina);

    /**
     * Os cenários de um autor (RN-37), em qualquer status, cada um com a versão mais recente e a última
     * devolução, para o autor continuar o rascunho e ler o que o curador pediu (RN-07). Os mais novos
     * primeiro.
     */
    TrechoDoAcervo listarDoAutor(long autorId, Pagina pagina);

    /**
     * Histórico do cenário (RF-11): todas as versões, da mais nova para a mais antiga, marcando a
     * vigente. Vazio se o cenário não existir.
     */
    List<VersaoNoHistorico> historico(long cenarioId);

    /** Uma versão do cenário pelo número, em qualquer status. */
    Optional<VersaoDoCenario> versao(long cenarioId, int numero);
}
