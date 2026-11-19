package br.ufg.inf.labsimulacoes.metricas.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Regra do alerta de custo diário (RNF-13), sem banco nem Spring. */
class AlertaDeCustoTest {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 19);
    private static final Optional<BigDecimal> LIMITE = Optional.of(new BigDecimal("0.01"));

    @Test
    void custoAcimaDoLimiteGeraAlerta() {
        assertThat(AlertaDeCusto.seUltrapassou(DIA, new BigDecimal("0.02"), LIMITE))
                .contains(new AlertaDeCusto(DIA, new BigDecimal("0.02"), new BigDecimal("0.01")));
    }

    /** "Passar do limite" é estrito: igual ao limite ainda não alerta. */
    @Test
    void custoIgualOuAbaixoDoLimiteNaoGeraAlerta() {
        assertThat(AlertaDeCusto.seUltrapassou(DIA, new BigDecimal("0.010000"), LIMITE)).isEmpty();
        assertThat(AlertaDeCusto.seUltrapassou(DIA, new BigDecimal("0.009999"), LIMITE)).isEmpty();
        assertThat(AlertaDeCusto.seUltrapassou(DIA, BigDecimal.ZERO, LIMITE)).isEmpty();
    }

    /** Sem custo diário máximo definido pelo administrador, não há alerta. */
    @Test
    void semLimiteNaoGeraAlerta() {
        assertThat(AlertaDeCusto.seUltrapassou(DIA, new BigDecimal("1000"), Optional.empty())).isEmpty();
    }

    @Test
    void alertaSoExisteComOCustoAcimaDeUmLimitePositivo() {
        assertThatThrownBy(() -> new AlertaDeCusto(DIA, new BigDecimal("0.01"), new BigDecimal("0.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AlertaDeCusto(DIA, BigDecimal.ONE, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AlertaDeCusto(null, BigDecimal.ONE, new BigDecimal("0.5")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AlertaDeCusto.seUltrapassou(DIA, null, LIMITE))
                .isInstanceOf(NullPointerException.class);
    }
}
