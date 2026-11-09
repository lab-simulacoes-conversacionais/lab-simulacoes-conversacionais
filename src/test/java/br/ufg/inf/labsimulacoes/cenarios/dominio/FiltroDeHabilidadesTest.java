package br.ufg.inf.labsimulacoes.cenarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FiltroDeHabilidadesTest {

    @Test
    void normalizaMaiusculasEEspacos() {
        var filtro = new FiltroDeHabilidades(" ef08hi03 ", " Revolução ", " ef ", 8, " HISTÓRIA ", 50);

        assertThat(filtro).isEqualTo(new FiltroDeHabilidades("EF08HI03", "revolução", "EF", 8, "história", 50));
    }

    @Test
    void textoVazioViraFiltroAusente() {
        var filtro = new FiltroDeHabilidades("", "  ", null, null, "\t", 50);

        assertThat(filtro.codigo()).isNull();
        assertThat(filtro.descricao()).isNull();
        assertThat(filtro.etapa()).isNull();
        assertThat(filtro.componente()).isNull();
    }

    @Test
    void buscasIguaisTemAMesmaChave() {
        var uma = new FiltroDeHabilidades("ef08hi03 ", null, "EF", null, "História", 50);
        var outra = new FiltroDeHabilidades(" EF08HI03", "", "ef", null, "história ", 50);

        assertThat(uma.chave()).isEqualTo(outra.chave());
    }

    @Test
    void buscasDiferentesTemChavesDiferentes() {
        var base = new FiltroDeHabilidades(null, null, null, 8, null, 50);

        assertThat(base.chave())
                .isNotEqualTo(new FiltroDeHabilidades(null, null, null, 7, null, 50).chave())
                .isNotEqualTo(new FiltroDeHabilidades(null, null, null, 8, null, 10).chave())
                .isNotEqualTo(new FiltroDeHabilidades(null, null, null, null, null, 50).chave());
    }

    /** Um texto com o separador da chave não pode imitar outra combinação de filtros. */
    @Test
    void descricaoNaoImitaAChaveDeOutroFiltro() {
        var comSeparador = new FiltroDeHabilidades(null, "a|2:EF", null, null, null, 50);
        var comEtapa = new FiltroDeHabilidades(null, "a", "EF", null, null, 50);

        assertThat(comSeparador.chave()).isNotEqualTo(comEtapa.chave());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10})
    void anoPrecisaEstarEntreUmENove(int ano) {
        assertThatThrownBy(() -> new FiltroDeHabilidades(null, null, null, ano, null, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ano");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, FiltroDeHabilidades.LIMITE_MAXIMO + 1})
    void limitePrecisaSerValido(int limite) {
        assertThatThrownBy(() -> new FiltroDeHabilidades(null, null, null, null, null, limite))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limite");
    }
}
