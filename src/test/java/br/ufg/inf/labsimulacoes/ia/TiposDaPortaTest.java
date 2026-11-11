package br.ufg.inf.labsimulacoes.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.ia.MensagemDaConversa.Autor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Validação e normalização do que chega pela porta do adaptador. */
class TiposDaPortaTest {

    static CenarioDaConversa cenario() {
        return new CenarioDaConversa("O voo do 14-bis", "Santos Dumont", null, null,
                null, null, null, null, null, null, null);
    }

    @Test
    void cenarioLimpaTextosEListas() {
        var cenario = new CenarioDaConversa(" O voo do 14-bis ", " Santos Dumont ", "  ", " Paris, 1906 ",
                Arrays.asList(" Explicar o voo ", null, " "), null, List.of("Museu"), " História ", 8,
                List.of("EF08HI03"), "  ");

        assertThat(cenario.titulo()).isEqualTo("O voo do 14-bis");
        assertThat(cenario.personagem()).isEqualTo("Santos Dumont");
        assertThat(cenario.papelDaIa()).isNull();
        assertThat(cenario.contexto()).isEqualTo("Paris, 1906");
        assertThat(cenario.objetivos()).containsExactly("Explicar o voo");
        assertThat(cenario.limitesDeAssunto()).isEmpty();
        assertThat(cenario.componente()).isEqualTo("História");
        assertThat(cenario.faixaEtaria()).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void cenarioExigeTituloEPersonagem(String vazio) {
        assertThatThrownBy(() -> new CenarioDaConversa(vazio, "Santos Dumont", null, null, null, null, null, null,
                        null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("título");
        assertThatThrownBy(() -> new CenarioDaConversa("O voo", vazio, null, null, null, null, null, null,
                        null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("personagem");
    }

    @Test
    void mensagemExigeAutorETexto() {
        assertThat(new MensagemDaConversa(Autor.TURMA, " Oi! ").texto()).isEqualTo("Oi!");
        assertThatThrownBy(() -> new MensagemDaConversa(null, "Oi"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("autor");
        assertThatThrownBy(() -> new MensagemDaConversa(Autor.PERSONAGEM, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("texto");
    }

    @Test
    void pedidoDeFalaCopiaOHistoricoEExigeCenarioEPergunta() {
        List<MensagemDaConversa> historico = new ArrayList<>(List.of(new MensagemDaConversa(Autor.TURMA, "Oi")));
        var pedido = new PedidoDeFala(cenario(), historico, " Quem é você? ");
        historico.clear();

        assertThat(pedido.historico()).hasSize(1);
        assertThat(pedido.pergunta()).isEqualTo("Quem é você?");
        assertThat(new PedidoDeFala(cenario(), null, "Oi").historico()).isEmpty();
        assertThatThrownBy(() -> new PedidoDeFala(null, List.of(), "Oi"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cenário");
        assertThatThrownBy(() -> new PedidoDeFala(cenario(), List.of(), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pergunta");
    }

    @Test
    void respostaSemTextoViraVazia() {
        assertThat(new RespostaDoPersonagem(null, 1).texto()).isEmpty();
        assertThat(new RespostaDoPersonagem(" Olá. ", 1).texto()).isEqualTo("Olá.");
    }

    @Test
    void pedidoDeSugestaoExigeTema() {
        var pedido = new PedidoDeSugestao(" Voo do 14-bis ", " ", "EF", 8, null);

        assertThat(pedido.tema()).isEqualTo("Voo do 14-bis");
        assertThat(pedido.componente()).isNull();
        assertThat(pedido.habilidades()).isEmpty();
        assertThatThrownBy(() -> new PedidoDeSugestao(null, "História", "EF", 8, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tema");
    }

    @Test
    void roteiroSugeridoAceitaCamposVazios() {
        var roteiro = new RoteiroSugerido(" ", null, null, null, null, null, null, List.of(" Por quê? "), null, 20);

        assertThat(roteiro.titulo()).isNull();
        assertThat(roteiro.objetivos()).isEmpty();
        assertThat(roteiro.perguntasDisparadoras()).containsExactly("Por quê?");
    }

    @Test
    void sugestaoExigeRoteiro() {
        assertThatThrownBy(() -> new SugestaoDeCenario(null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("roteiro");
    }

    @Test
    void moderacaoAprovadaNaoTemCategorias() {
        assertThat(ResultadoDaModeracao.aprovado()).isEqualTo(new ResultadoDaModeracao(false, List.of()));
        assertThat(new ResultadoDaModeracao(true, null).categorias()).isEmpty();
    }
}
