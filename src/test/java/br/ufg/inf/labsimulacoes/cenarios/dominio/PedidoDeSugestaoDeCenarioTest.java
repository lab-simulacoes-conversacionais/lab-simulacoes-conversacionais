package br.ufg.inf.labsimulacoes.cenarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** RF-07: o pedido de sugestão e o rascunho montado com a resposta da IA. */
class PedidoDeSugestaoDeCenarioTest {

    private static final VinculoCurricular CURRICULO = ConteudosDeTeste.CURRICULO;
    private static final PedidoDeSugestaoDeCenario PEDIDO =
            new PedidoDeSugestaoDeCenario("  O voo do 14-bis  ", CURRICULO);

    @Test
    void temaChegaSemEspacosNasPontas() {
        assertThat(PEDIDO.tema()).isEqualTo("O voo do 14-bis");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void temaEhObrigatorio(String tema) {
        assertThatThrownBy(() -> new PedidoDeSugestaoDeCenario(tema, CURRICULO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tema");
    }

    /** RNF-13: o tema vai inteiro para o prompt. */
    @Test
    void temaTemNoMaximo200Caracteres() {
        assertThat(new PedidoDeSugestaoDeCenario("a".repeat(200), CURRICULO).tema()).hasSize(200);
        assertThatThrownBy(() -> new PedidoDeSugestaoDeCenario("a".repeat(201), CURRICULO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("200 caracteres");
    }

    /** Os espaços nas pontas são tirados antes de medir. */
    @Test
    void espacosNasPontasNaoContamNoTamanhoDoTema() {
        assertThat(new PedidoDeSugestaoDeCenario("  " + "a".repeat(200) + "  ", CURRICULO).tema()).hasSize(200);
    }

    @Test
    void curriculoEhObrigatorio() {
        assertThatThrownBy(() -> new PedidoDeSugestaoDeCenario("Tema", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("componente");
    }

    @Test
    void componenteEhObrigatorio() {
        var semComponente = new VinculoCurricular(null, "EF", 8, List.of("EF08HI03"));

        assertThatThrownBy(() -> new PedidoDeSugestaoDeCenario("Tema", semComponente))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("componente");
    }

    @Test
    void anoEhObrigatorio() {
        var semAno = new VinculoCurricular("História", "EF", null, List.of("EF08HI03"));

        assertThatThrownBy(() -> new PedidoDeSugestaoDeCenario("Tema", semAno))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ano");
    }

    @Test
    void precisaDeAoMenosUmaHabilidade() {
        var semHabilidade = new VinculoCurricular("História", "EF", 8, List.of());

        assertThatThrownBy(() -> new PedidoDeSugestaoDeCenario("Tema", semHabilidade))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("habilidade");
    }

    @Test
    void etapaEhOpcional() {
        var semEtapa = new VinculoCurricular("História", null, 8, List.of("EF08HI03"));

        assertThat(new PedidoDeSugestaoDeCenario("Tema", semEtapa).curriculo().etapa()).isNull();
    }

    @Test
    void rascunhoTemOQueAIaSugeriuEOVinculoDoPedido() {
        ConteudoDoCenario conteudo =
                PEDIDO.conteudoSugerido("Santos Dumont em Paris", ConteudosDeTeste.ROTEIRO, "13 a 14 anos", 20);

        assertThat(conteudo).isEqualTo(new ConteudoDoCenario(
                "Santos Dumont em Paris", ConteudosDeTeste.ROTEIRO, CURRICULO, "13 a 14 anos", 20));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void semTituloORascunhoUsaOTema(String titulo) {
        assertThat(PEDIDO.conteudoSugerido(titulo, null, null, null).titulo()).isEqualTo("O voo do 14-bis");
    }

    /** RN-17: uma duração fora da faixa não derruba a sugestão; o curador preenche. */
    @ParameterizedTest
    @ValueSource(ints = {9, 31, 0, -5})
    void duracaoForaDaFaixaFicaEmBranco(int duracao) {
        assertThat(PEDIDO.conteudoSugerido("Título", null, null, duracao).duracaoPrevistaMin()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 30})
    void duracaoNosLimitesDaFaixaEhMantida(int duracao) {
        assertThat(PEDIDO.conteudoSugerido("Título", null, null, duracao).duracaoPrevistaMin()).isEqualTo(duracao);
    }

    @Test
    void semDuracaoFicaEmBranco() {
        assertThat(PEDIDO.conteudoSugerido("Título", null, null, null).duracaoPrevistaMin()).isNull();
    }
}
