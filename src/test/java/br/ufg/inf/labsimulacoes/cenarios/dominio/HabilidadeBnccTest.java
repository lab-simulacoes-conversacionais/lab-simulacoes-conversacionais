package br.ufg.inf.labsimulacoes.cenarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HabilidadeBnccTest {

    @Test
    void guardaOsCamposSemEspacosNasPontas() {
        var habilidade = new HabilidadeBncc(" EF08HI03 ", " Analisar. ", "EF", List.of(8), " História ");

        assertThat(habilidade).isEqualTo(new HabilidadeBncc("EF08HI03", "Analisar.", "EF", List.of(8), "História"));
    }

    @Test
    void anosFicamEmOrdemESemRepeticao() {
        var habilidade = new HabilidadeBncc("EF67LP08", "Texto.", "EF", Arrays.asList(7, 6, 7), "Língua Portuguesa");

        assertThat(habilidade.anos()).containsExactly(6, 7);
        assertThat(habilidade.anosComoTexto()).isEqualTo("6,7");
    }

    @Test
    void blocoDeCincoAnosViraTextoSemEspacos() {
        var habilidade = new HabilidadeBncc("EF15LP01", "Texto.", "EF", List.of(1, 2, 3, 4, 5), "Língua Portuguesa");

        assertThat(habilidade.anosComoTexto()).isEqualTo("1,2,3,4,5");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ef08hi03", "EF8HI03", "EF08H03", "EF08HI3", "08HI03EF", "EF08HI03X"})
    void codigoForaDoFormatoOficialERecusado(String codigo) {
        assertThatThrownBy(() -> new HabilidadeBncc(codigo, "Texto.", "EF", List.of(8), "História"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("formato oficial");
    }

    @Test
    void codigoPrecisaComecarPelaEtapa() {
        assertThatThrownBy(() -> new HabilidadeBncc("EM13LGG101", "Texto.", "EF", List.of(1), "Linguagens"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("etapa EF");
    }

    @Test
    void formatoDasOutrasEtapasTambemEAceito() {
        assertThat(new HabilidadeBncc("EM13LGG101", "Texto.", "EM", List.of(1, 2, 3), "Linguagens").codigo())
                .isEqualTo("EM13LGG101");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ef", "E", "EFX", "E1"})
    void etapaPrecisaSerUmaSigla(String etapa) {
        assertThatThrownBy(() -> new HabilidadeBncc("EF08HI03", "Texto.", etapa, List.of(8), "História"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sigla");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10, -1})
    void anoPrecisaEstarEntreUmENove(int ano) {
        assertThatThrownBy(() -> new HabilidadeBncc("EF08HI03", "Texto.", "EF", List.of(ano), "História"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entre 1 e 9");
    }

    @Test
    void precisaDePeloMenosUmAno() {
        assertThatThrownBy(() -> new HabilidadeBncc("EF08HI03", "Texto.", "EF", List.of(), "História"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HabilidadeBncc("EF08HI03", "Texto.", "EF", null, "História"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HabilidadeBncc("EF08HI03", "Texto.", "EF", Arrays.asList(8, null), "História"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ano vazio");
    }

    @Test
    void camposDeTextoSaoObrigatorios() {
        assertThatThrownBy(() -> new HabilidadeBncc(null, "Texto.", "EF", List.of(8), "História"))
                .hasMessageContaining("código");
        assertThatThrownBy(() -> new HabilidadeBncc("EF08HI03", " ", "EF", List.of(8), "História"))
                .hasMessageContaining("descrição");
        assertThatThrownBy(() -> new HabilidadeBncc("EF08HI03", "Texto.", "", List.of(8), "História"))
                .hasMessageContaining("etapa");
        assertThatThrownBy(() -> new HabilidadeBncc("EF08HI03", "Texto.", "EF", List.of(8), null))
                .hasMessageContaining("componente");
    }

    /** O resultado das buscas vai para o Redis com a serialização do Java. */
    @Test
    void sobreviveASerializacao() throws Exception {
        var original = List.of(new HabilidadeBncc("EF67LP08", "Texto.", "EF", List.of(6, 7), "Língua Portuguesa"));

        var bytes = new ByteArrayOutputStream();
        try (var saida = new ObjectOutputStream(bytes)) {
            saida.writeObject(original);
        }
        try (var entrada = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(entrada.readObject()).isEqualTo(original);
        }
    }
}
