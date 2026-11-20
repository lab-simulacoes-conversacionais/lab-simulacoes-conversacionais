package br.ufg.inf.labsimulacoes.cenarios;

/**
 * Biblioteca de cenários da escola (RF-12), para o professor escolher o que levar para a sala (RF-14),
 * com busca por tema e filtros por componente, ano e habilidade BNCC.
 *
 * <p>Exige o perfil Professor ou Curador (RN-37). Só entram cenários Aprovados com versão vigente, de
 * todos os autores: rascunho, em curadoria, suspenso e arquivado ficam de fora (RN-01, RN-09). Os
 * filtros olham só a versão vigente, a mesma que vai para a sala.
 */
public interface BibliotecaDeCenarios {

    /** Nenhuma página traz mais cenários que isto. */
    int TAMANHO_MAXIMO_DA_PAGINA = 100;

    /**
     * Uma página da biblioteca, só com os cenários que atendem a todos os filtros, em ordem de título,
     * com desempate pelo cenário. A mesma busca traz sempre a mesma página enquanto a biblioteca não
     * mudar.
     *
     * @param filtro {@link FiltroDaBiblioteca#NENHUM} para a biblioteca inteira
     * @param pagina a partir de 0
     * @param tamanho de 1 a {@link #TAMANHO_MAXIMO_DA_PAGINA}
     * @throws IllegalArgumentException se a página for negativa, o tamanho estiver fora da faixa, o ano
     *     não estiver entre 1 e 9 ou o texto passar de 100 caracteres
     */
    PaginaDaBiblioteca listar(FiltroDaBiblioteca filtro, int pagina, int tamanho);

    /**
     * Uma página da biblioteca inteira, como {@code listar(FiltroDaBiblioteca.NENHUM, pagina, tamanho)}.
     * Não é um método default: a checagem de perfil do Spring não alcança métodos default da interface.
     */
    PaginaDaBiblioteca listar(int pagina, int tamanho);

    /** Componentes, anos e habilidades que os filtros podem usar agora, para a tela montar as opções. */
    FiltrosDaBiblioteca filtrosDisponiveis();
}
