package br.ufg.inf.labsimulacoes.metricas.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * O custo da IA no dia passou do limite que o administrador definiu (RNF-13). Há no máximo um alerta por
 * dia: depois do primeiro, o dia já está alertado, mesmo que o custo continue subindo ou que o limite
 * mude.
 *
 * @param custo o custo do dia no momento do alerta, em dólares
 * @param limite o custo diário máximo no momento do alerta, em dólares
 */
public record AlertaDeCusto(LocalDate dia, BigDecimal custo, BigDecimal limite) {

    public AlertaDeCusto {
        Objects.requireNonNull(dia, "dia");
        Objects.requireNonNull(custo, "custo");
        Objects.requireNonNull(limite, "limite");
        if (limite.signum() <= 0) {
            throw new IllegalArgumentException("O limite do custo diário precisa ser maior que zero: " + limite);
        }
        if (!passouDoLimite(custo, limite)) {
            throw new IllegalArgumentException("Só há alerta quando o custo passa do limite: " + custo + " e " + limite);
        }
    }

    /**
     * A regra do RNF-13, num lugar só: o custo passou do limite, estritamente. Igual ao limite ainda não
     * passou. Vale para gravar o alerta e para a tela de consumo mostrar que o dia está acima do limite.
     */
    public static boolean passouDoLimite(BigDecimal custo, BigDecimal limite) {
        Objects.requireNonNull(custo, "custo");
        Objects.requireNonNull(limite, "limite");
        return custo.compareTo(limite) > 0;
    }

    /**
     * Regra do alerta: o custo do dia passou do limite ({@link #passouDoLimite}).
     *
     * @param limite vazio quando o administrador não definiu um custo diário máximo, e então não há alerta
     * @return o alerta, ou vazio se o custo não passou do limite
     */
    public static Optional<AlertaDeCusto> seUltrapassou(LocalDate dia, BigDecimal custo, Optional<BigDecimal> limite) {
        Objects.requireNonNull(custo, "custo");
        return limite.filter(valor -> passouDoLimite(custo, valor)).map(valor -> new AlertaDeCusto(dia, custo, valor));
    }
}
