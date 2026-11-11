package br.ufg.inf.labsimulacoes.ia.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PreenchimentoDeTemplateTest {

    private static final String ORIGEM = "template teste-v1, seção instrucoes";

    @Test
    void trocaCadaMarcadorPeloValor() {
        String texto = PreenchimentoDeTemplate.preencher(ORIGEM, "Olá, {{nome}}! Você é {{papel}}. Tchau, {{nome}}.",
                Map.of("nome", "turma", "papel", "Santos Dumont"));

        assertThat(texto).isEqualTo("Olá, turma! Você é Santos Dumont. Tchau, turma.");
    }

    @Test
    void textoSemMarcadorFicaIgual() {
        assertThat(PreenchimentoDeTemplate.preencher(ORIGEM, "Sem marcadores }} aqui.", Map.of()))
                .isEqualTo("Sem marcadores }} aqui.");
    }

    /** Uma passagem só: o valor é copiado como veio, mesmo com marcadores dentro. */
    @Test
    void valorComMarcadorNaoEPreenchidoDeNovo() {
        String texto = PreenchimentoDeTemplate.preencher(ORIGEM, "A: {{a}} / B: {{b}}",
                Map.of("a", "{{b}} {{inexistente}} {{", "b", "beta"));

        assertThat(texto).isEqualTo("A: {{b}} {{inexistente}} {{ / B: beta");
    }

    @Test
    void marcadorSemValorFalhaComONomeEAOrigem() {
        assertThatThrownBy(() -> PreenchimentoDeTemplate.preencher(ORIGEM, "Olá, {{nome}}.", Map.of("outro", "x")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("O marcador {{nome}} de template teste-v1, seção instrucoes não tem valor");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Olá, {{ nome }}.", "Olá, {{Nome}}.", "Olá, {{nome.", "Olá, {{}}.", "Fim {{"})
    void marcadorMalformadoFalha(String texto) {
        assertThatThrownBy(() -> PreenchimentoDeTemplate.preencher(ORIGEM, texto, Map.of("nome", "turma")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Marcador malformado em template teste-v1, seção instrucoes");
    }
}
