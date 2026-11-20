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

    /** A regra compartilhada pelo alerta e pela tela de consumo: estritamente acima do limite. */
    @Test
    void passouDoLimiteEEstritamenteAcima() {
        assertThat(AlertaDeCusto.passouDoLimite(new BigDecimal("0.010001"), new BigDecimal("0.01"))).isTrue();
        assertThat(AlertaDeCusto.passouDoLimite(new BigDecimal("0.010000"), new BigDecimal("0.01"))).isFalse();
        assertThat(AlertaDeCusto.passouDoLimite(new BigDecimal("0.009"), new BigDecimal("0.01"))).isFalse();
        assertThatThrownBy(() -> AlertaDeCusto.passouDoLimite(null, BigDecimal.ONE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AlertaDeCusto.passouDoLimite(BigDecimal.ONE, null))
                .isInstanceOf(NullPointerException.class);
    }

    /** seUltrapassou e o construtor seguem a mesma regra: um alerta só existe quando passouDoLimite. */
    @Test
    void alertaSegueAMesmaRegra() {
        for (String custo : new String[] {"0.009", "0.01", "0.0100", "0.011", "5"}) {
            var valor = new BigDecimal(custo);
            assertThat(AlertaDeCusto.seUltrapassou(DIA, valor, LIMITE).isPresent())
                    .as(custo)
                    .isEqualTo(AlertaDeCusto.passouDoLimite(valor, LIMITE.orElseThrow()));
        }
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
