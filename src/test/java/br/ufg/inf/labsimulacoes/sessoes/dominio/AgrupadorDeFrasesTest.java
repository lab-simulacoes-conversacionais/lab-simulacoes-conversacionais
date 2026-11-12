package br.ufg.inf.labsimulacoes.sessoes.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Frases em português a partir dos trechos do streaming (RN-20, Stack, seção 5, passo 7). */
class AgrupadorDeFrasesTest {

    @Test
    void fraseTerminaNoPontoSeguidoDeEspaco() {
        assertThat(frases("Sou Santos Dumont. Nasci em Minas.")).containsExactly("Sou Santos Dumont. ", "Nasci em Minas.");
    }

    @Test
    void exclamacaoInterrogacaoEReticenciasTambemFecham() {
        assertThat(frases("Que voo! Você viu? Eu vi... Foi lindo?! Sim."))
                .containsExactly("Que voo! ", "Você viu? ", "Eu vi... ", "Foi lindo?! ", "Sim.");
        assertThat(frases("Esperei… Voei.")).containsExactly("Esperei… ", "Voei.");
    }

    /** Juntar as frases devolve o texto, e o espaço fica no fim da frase, porque o SSE descarta o do início. */
    @Test
    void juntarAsFrasesDevolveOTexto() {
        String texto = "  Olá!  Sou eu.\n\nVoei em 1906. Até logo";

        List<String> frases = frases(texto);

        assertThat(frases).containsExactly("  Olá!  ", "Sou eu.\n\n", "Voei em 1906. ", "Até logo");
        assertThat(String.join("", frases)).isEqualTo(texto);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "O Sr. Ferber me ajudou.",
        "A Sra. Ferber me ajudou.",
        "A Dra. Lima explicou.",
        "O Prof. Lima explicou.",
        "No séc. XX tudo mudou.",
        "Foi D. Pedro II quem reinou.",
        "Isso foi em 300 a.C. no Egito.",
        "Mede aprox. dez metros."
    })
    void abreviacaoEInicialNaoFecham(String frase) {
        assertThat(frases(frase + " Depois.")).containsExactly(frase + " ", "Depois.");
    }

    /** O ano no fim da frase fecha; o ponto entre dígitos, não. */
    @Test
    void numeros() {
        assertThat(frases("Voei em 1906. Depois descansei.")).containsExactly("Voei em 1906. ", "Depois descansei.");
        assertThat(frases("Pesava 3.5 quilos e custou 1.000 francos.")).containsExactly("Pesava 3.5 quilos e custou 1.000 francos.");
    }

    @Test
    void aspasEParentesesDeFechamentoFicamComAFrase() {
        assertThat(frases("Ele disse: \"Voei!\" Todos riram.")).containsExactly("Ele disse: \"Voei!\" ", "Todos riram.");
        assertThat(frases("(Foi em Paris.) Era outono.")).containsExactly("(Foi em Paris.) ", "Era outono.");
    }

    @Test
    void quebraDeLinhaFechaAFrase() {
        assertThat(frases("Primeiro item\nSegundo item")).containsExactly("Primeiro item\n", "Segundo item");
    }

    /** Com o ponto no fim do trecho, não dá para saber se a frase acabou: ela espera o próximo. */
    @Test
    void pontoNoFimDoTrechoEsperaOProximo() {
        var agrupador = new AgrupadorDeFrases();

        assertThat(agrupador.acrescentar("Custou 3.")).isEmpty();
        assertThat(agrupador.acrescentar("5 francos. ")).containsExactly("Custou 3.5 francos. ");
        assertThat(agrupador.terminar()).isEmpty();
    }

    @Test
    void trechosQuebradosNoMeioDaPalavraFormamAFrase() {
        var agrupador = new AgrupadorDeFrases();
        List<String> frases = new ArrayList<>();

        for (String trecho : List.of("Sou San", "tos Du", "mont", ". Vo", "ei.", " ")) {
            frases.addAll(agrupador.acrescentar(trecho));
        }

        assertThat(frases).containsExactly("Sou Santos Dumont. ", "Voei. ");
        assertThat(agrupador.terminar()).isEmpty();
    }

    /** Sem pontuação, o texto é cortado no último espaço antes do limite, para a tela não ficar parada. */
    @Test
    void textoSemPontuacaoECortadoNoLimite() {
        String palavras = "palavra ".repeat(60);
        var agrupador = new AgrupadorDeFrases();

        List<String> frases = agrupador.acrescentar(palavras);

        assertThat(frases).hasSize(1);
        assertThat(frases.getFirst()).hasSizeLessThanOrEqualTo(AgrupadorDeFrases.LIMITE_SEM_PONTUACAO).endsWith(" ");
        assertThat(frases.getFirst() + agrupador.terminar().orElseThrow()).isEqualTo(palavras);
    }

    @Test
    void textoSemPontuacaoNemEspacoECortadoNoLimite() {
        String grudado = "a".repeat(AgrupadorDeFrases.LIMITE_SEM_PONTUACAO + 10);

        List<String> frases = new AgrupadorDeFrases().acrescentar(grudado);

        assertThat(frases).containsExactly("a".repeat(AgrupadorDeFrases.LIMITE_SEM_PONTUACAO));
    }

    @Test
    void terminarDescarregaORestoMasNaoSoEspacos() {
        var agrupador = new AgrupadorDeFrases();
        agrupador.acrescentar("Sem ponto final");

        assertThat(agrupador.terminar()).contains("Sem ponto final");
        assertThat(agrupador.terminar()).isEmpty();
        agrupador.acrescentar("   ");
        assertThat(agrupador.terminar()).isEmpty();
        assertThat(agrupador.acrescentar(null)).isEmpty();
    }

    private static List<String> frases(String texto) {
        var agrupador = new AgrupadorDeFrases();
        List<String> frases = new ArrayList<>(agrupador.acrescentar(texto));
        agrupador.terminar().ifPresent(frases::add);
        return frases;
    }
}
