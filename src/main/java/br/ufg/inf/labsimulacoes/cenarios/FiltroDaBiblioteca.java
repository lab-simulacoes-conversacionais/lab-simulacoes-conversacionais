package br.ufg.inf.labsimulacoes.cenarios;

/**
 * Busca e filtros da {@link BibliotecaDeCenarios} (RF-12). Todos são opcionais e se combinam: um
 * cenário só aparece se atender a todos os informados. Texto em branco conta como não informado.
 *
 * @param texto tema procurado no título, no componente, no personagem, no contexto ou nos objetivos,
 *     sem diferenciar maiúsculas nem acentos; até 100 caracteres
 * @param componente componente curricular, como "História", sem diferenciar maiúsculas
 * @param ano ano escolar, de 1 a 9
 * @param habilidade código BNCC, como EF08HI03, sem diferenciar maiúsculas
 */
public record FiltroDaBiblioteca(String texto, String componente, Integer ano, String habilidade) {

    /** Sem filtro: a biblioteca inteira. */
    public static final FiltroDaBiblioteca NENHUM = new FiltroDaBiblioteca(null, null, null, null);
}
