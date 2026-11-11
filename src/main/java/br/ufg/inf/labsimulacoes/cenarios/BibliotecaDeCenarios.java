package br.ufg.inf.labsimulacoes.cenarios;

/**
 * Biblioteca de cenários da escola (RF-12), para o professor escolher o que levar para a sala (RF-14).
 * Na E1 é uma lista simples, sem busca nem filtros.
 *
 * <p>Exige o perfil Professor ou Curador (RN-37). Só entram cenários Aprovados com versão vigente, de
 * todos os autores: rascunho, em curadoria, suspenso e arquivado ficam de fora (RN-01, RN-09).
 */
public interface BibliotecaDeCenarios {

    /** Nenhuma página traz mais cenários que isto. */
    int TAMANHO_MAXIMO_DA_PAGINA = 100;

    /**
     * Uma página da biblioteca, em ordem de título, com desempate pelo cenário. A mesma página traz
     * sempre os mesmos cenários enquanto a biblioteca não mudar.
     *
     * @param pagina a partir de 0
     * @param tamanho de 1 a {@link #TAMANHO_MAXIMO_DA_PAGINA}
     * @throws IllegalArgumentException se a página for negativa ou o tamanho estiver fora da faixa
     */
    PaginaDaBiblioteca listar(int pagina, int tamanho);
}
