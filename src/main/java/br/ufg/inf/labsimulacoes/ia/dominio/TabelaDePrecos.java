package br.ufg.inf.labsimulacoes.ia.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Preço do modelo em dólares por 1 milhão de tokens, para o custo de cada resposta (RNF-13).
 *
 * @param entradaPorMilhao preço dos tokens enviados ao modelo
 * @param saidaPorMilhao preço dos tokens gerados, que incluem os de raciocínio
 */
public record TabelaDePrecos(BigDecimal entradaPorMilhao, BigDecimal saidaPorMilhao) {

    /** Casas decimais da coluna custo de resposta_ia_meta. */
    public static final int CASAS_DO_CUSTO = 6;

    private static final BigDecimal UM_MILHAO = BigDecimal.valueOf(1_000_000);

    public TabelaDePrecos {
        entradaPorMilhao = precoValido(entradaPorMilhao, "de entrada");
        saidaPorMilhao = precoValido(saidaPorMilhao, "de saída");
    }

    /**
     * Custo em dólares, arredondado para {@value #CASAS_DO_CUSTO} casas. Nulo quando o provedor não
     * informou os tokens: um custo zero esconderia o consumo real.
     */
    public BigDecimal custoDe(UsoDoModelo uso) {
        if (!uso.conhecido()) {
            return null;
        }
        BigDecimal entrada = entradaPorMilhao.multiply(BigDecimal.valueOf(uso.tokensEntrada()));
        BigDecimal saida = saidaPorMilhao.multiply(BigDecimal.valueOf(uso.tokensSaida()));
        return entrada.add(saida).divide(UM_MILHAO, CASAS_DO_CUSTO, RoundingMode.HALF_UP);
    }

    private static BigDecimal precoValido(BigDecimal preco, String tipo) {
        if (preco == null) {
            throw new IllegalArgumentException("O preço " + tipo + " é obrigatório");
        }
        if (preco.signum() < 0) {
            throw new IllegalArgumentException("O preço " + tipo + " não pode ser negativo: " + preco);
        }
        return preco;
    }
}
