package br.ufg.inf.labsimulacoes.ia;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Totais de consumo da IA (RNF-13).
 *
 * @param respostas quantas respostas foram geradas
 * @param tokensEntrada soma dos tokens de entrada informados pelo provedor
 * @param tokensSaida soma dos tokens de saída informados pelo provedor
 * @param custo em dólares, só das respostas com custo conhecido
 * @param respostasSemCusto quantas respostas ficaram sem custo, porque o provedor não informou os
 *     tokens; o custo real é maior que {@code custo} quando há alguma
 */
public record ResumoDoConsumo(
        long respostas, long tokensEntrada, long tokensSaida, BigDecimal custo, long respostasSemCusto) {

    public ResumoDoConsumo {
        Objects.requireNonNull(custo, "custo");
        if (respostas < 0 || tokensEntrada < 0 || tokensSaida < 0 || respostasSemCusto < 0) {
            throw new IllegalArgumentException("Os totais de consumo não podem ser negativos");
        }
        if (respostasSemCusto > respostas) {
            throw new IllegalArgumentException("Há mais respostas sem custo do que respostas");
        }
        if (custo.signum() < 0) {
            throw new IllegalArgumentException("O custo não pode ser negativo: " + custo);
        }
    }

    public static ResumoDoConsumo vazio() {
        return new ResumoDoConsumo(0, 0, 0, BigDecimal.ZERO, 0);
    }
}
