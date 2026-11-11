package br.ufg.inf.labsimulacoes.cenarios.dominio;

/**
 * Página de uma listagem. A primeira página é a 0.
 *
 * @param numero página pedida, a partir de 0
 * @param tamanho quantos itens por página, de 1 a {@link #TAMANHO_MAXIMO}
 */
public record Pagina(int numero, int tamanho) {

    /** Nenhuma listagem traz mais que isto de uma vez. */
    public static final int TAMANHO_MAXIMO = 100;

    public Pagina {
        if (numero < 0) {
            throw new IllegalArgumentException("A página começa em 0");
        }
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("O tamanho da página precisa estar entre 1 e " + TAMANHO_MAXIMO);
        }
    }

    /** Quantos itens pular. Em long porque número vezes tamanho pode passar do limite do int. */
    public long deslocamento() {
        return (long) numero * tamanho;
    }
}
