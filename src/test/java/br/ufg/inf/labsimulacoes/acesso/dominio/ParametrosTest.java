package br.ufg.inf.labsimulacoes.acesso.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ParametrosTest {

    @Test
    void limitePadraoEDeQuarentaPerguntas() {
        assertThat(Parametros.padrao().limiteDePerguntasPorSessao()).isEqualTo(40);
    }

    @Test
    void custoDiarioComecaSemValor() {
        assertThat(Parametros.padrao().custoDiarioMaximo()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 25, 100})
    void limiteDePerguntasPodeSerAlterado(int limite) {
        assertThat(new Parametros(limite, null).limiteDePerguntasPorSessao()).isEqualTo(limite);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void limiteDePerguntasPrecisaSerPositivo(int limite) {
        assertThatThrownBy(() -> new Parametros(limite, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limite de perguntas");
    }

    @Test
    void custoDiarioPodeSerDefinido() {
        assertThat(new Parametros(40, new BigDecimal("2.50")).custoDiarioMaximo()).contains(new BigDecimal("2.5"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1"})
    void custoDiarioPrecisaSerMaiorQueZero(String custo) {
        assertThatThrownBy(() -> new Parametros(40, new BigDecimal(custo)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("custo diário");
    }

    @Test
    void custoComZerosADireitaEOMesmoValor() {
        assertThat(new Parametros(40, new BigDecimal("5.00"))).isEqualTo(new Parametros(40, new BigDecimal("5")));
    }

    @Test
    void custoRedondoNaoViraNotacaoCientifica() {
        assertThat(new Parametros(40, new BigDecimal("50.00")).custoDiarioMaximoUsd()).hasToString("50");
    }
}
