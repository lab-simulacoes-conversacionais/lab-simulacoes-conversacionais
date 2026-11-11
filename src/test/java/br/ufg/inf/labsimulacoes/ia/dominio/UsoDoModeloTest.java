package br.ufg.inf.labsimulacoes.ia.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UsoDoModeloTest {

    @Test
    void usoSoEhConhecidoComEntradaESaida() {
        assertThat(new UsoDoModelo(10, 5).conhecido()).isTrue();
        assertThat(new UsoDoModelo(0, 0).conhecido()).isTrue();
        assertThat(new UsoDoModelo(10, null).conhecido()).isFalse();
        assertThat(new UsoDoModelo(null, 5).conhecido()).isFalse();
        assertThat(UsoDoModelo.desconhecido().conhecido()).isFalse();
    }

    @Test
    void tokensNaoPodemSerNegativos() {
        assertThatThrownBy(() -> new UsoDoModelo(-1, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("de entrada");
        assertThatThrownBy(() -> new UsoDoModelo(10, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("de saída");
    }
}
