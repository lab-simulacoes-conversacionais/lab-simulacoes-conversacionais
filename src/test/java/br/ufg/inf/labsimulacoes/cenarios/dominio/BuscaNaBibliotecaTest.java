package br.ufg.inf.labsimulacoes.cenarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Normalização da busca e dos filtros da biblioteca (RF-12), sem banco nem Spring. */
class BuscaNaBibliotecaTest {

    @Test
    void textoFicaSemAcentoEmMinusculasESemEspacosNasPontas() {
        var busca = new BuscaNaBiblioteca("  Revolução FRANCESA ", null, null, null);

        assertThat(busca.texto()).isEqualTo("revolucao francesa");
    }

    @Test
    void semAcentoTrataAsLetrasDoPortugues() {
        assertThat(BuscaNaBiblioteca.semAcento("ÁàÂãÉêÍÓôÕÚüÇñ")).isEqualTo("aaaaeeiooouucn");
        assertThat(BuscaNaBiblioteca.semAcento(null)).isNull();
    }

    /** Texto colado de alguns sistemas traz o acento separado da letra; ele é juntado antes (NFC). */
    @Test
    void acentoSeparadoDaLetraTambemSai() {
        assertThat(BuscaNaBiblioteca.semAcento("Café colonial")).isEqualTo("cafe colonial");
        assertThat(BuscaNaBiblioteca.semAcento("Ångström")).isEqualTo("angstrom");
    }

    /** O banco aplica a mesma lista no translate(), que exige as duas do mesmo tamanho. */
    @Test
    void listasDeLetrasTemOMesmoTamanho() {
        assertThat(BuscaNaBiblioteca.COM_ACENTO).hasSameSizeAs(BuscaNaBiblioteca.SEM_ACENTO);
    }

    @Test
    void habilidadeFicaEmMaiusculasEComponenteSemEspacos() {
        var busca = new BuscaNaBiblioteca(null, "  História ", 8, " ef08hi03 ");

        assertThat(busca.componente()).isEqualTo("História");
        assertThat(busca.componenteSemAcento()).isEqualTo("historia");
        assertThat(busca.ano()).isEqualTo(8);
        assertThat(busca.habilidade()).isEqualTo("EF08HI03");
    }

    @Test
    void emBrancoNaoFiltra() {
        var busca = new BuscaNaBiblioteca("   ", "", null, "\t");

        assertThat(busca).isEqualTo(BuscaNaBiblioteca.TUDO);
        assertThat(busca.componenteSemAcento()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10, -1})
    void anoForaDoEnsinoFundamentalERecusado(int ano) {
        assertThatThrownBy(() -> new BuscaNaBiblioteca(null, null, ano, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entre 1 e 9");
    }

    @Test
    void textoLongoDemaisERecusado() {
        String noLimite = "a".repeat(BuscaNaBiblioteca.TAMANHO_MAXIMO_DO_TEXTO);

        assertThat(new BuscaNaBiblioteca(noLimite, null, null, null).texto()).hasSize(100);
        assertThatThrownBy(() -> new BuscaNaBiblioteca(noLimite + "a", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
