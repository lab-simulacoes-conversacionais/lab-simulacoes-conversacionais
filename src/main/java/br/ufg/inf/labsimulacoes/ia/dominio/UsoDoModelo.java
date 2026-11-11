package br.ufg.inf.labsimulacoes.ia.dominio;

/**
 * Tokens consumidos em uma chamada ao modelo. Um valor nulo quer dizer que o provedor não informou,
 * o que é diferente de zero.
 */
public record UsoDoModelo(Integer tokensEntrada, Integer tokensSaida) {

    public UsoDoModelo {
        naoNegativo(tokensEntrada, "de entrada");
        naoNegativo(tokensSaida, "de saída");
    }

    public static UsoDoModelo desconhecido() {
        return new UsoDoModelo(null, null);
    }

    public boolean conhecido() {
        return tokensEntrada != null && tokensSaida != null;
    }

    private static void naoNegativo(Integer tokens, String tipo) {
        if (tokens != null && tokens < 0) {
            throw new IllegalArgumentException("Os tokens " + tipo + " não podem ser negativos: " + tokens);
        }
    }
}
