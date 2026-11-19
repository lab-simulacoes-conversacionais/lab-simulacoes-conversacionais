package br.ufg.inf.labsimulacoes.ia.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Opções do modelo só para a sugestão de cenário (RNF-03, RNF-13). */
class OpcoesDaSugestaoTest {

    @Test
    void textosChegamSemEspacosNasPontas() {
        assertThat(new OpcoesDaSugestao(" gpt-6.1-terra ", " low ", " default ", 4000))
                .isEqualTo(new OpcoesDaSugestao("gpt-6.1-terra", "low", "default", 4000));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void esforcoENivelEmBrancoUsamOsDaFala(String valor) {
        var opcoes = new OpcoesDaSugestao("gpt-6.1-terra", valor, valor, 4000);

        assertThat(opcoes.esforcoDeRaciocinio()).isNull();
        assertThat(opcoes.nivelDeServico()).isNull();
    }

    /** Sem IA_MODELO_SUGESTAO, a sugestão não roda. */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void semModeloASugestaoFicaDesligada(String modelo) {
        var opcoes = new OpcoesDaSugestao(modelo, "low", "default", 4000);

        assertThat(opcoes.modelo()).isNull();
        assertThat(opcoes.ligada()).isFalse();
    }

    @Test
    void comModeloASugestaoFicaLigada() {
        assertThat(new OpcoesDaSugestao("gpt-6.1-terra", null, null, 4000).ligada()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void tetoDeTokensPrecisaSerPositivo(int teto) {
        assertThatThrownBy(() -> new OpcoesDaSugestao("gpt-6.1-terra", "low", "default", teto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("teto de tokens");
    }
}
