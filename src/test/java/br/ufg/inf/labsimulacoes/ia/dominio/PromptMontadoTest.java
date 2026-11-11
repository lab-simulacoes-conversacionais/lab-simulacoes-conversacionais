package br.ufg.inf.labsimulacoes.ia.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PromptMontadoTest {

    /** RNF-09: o pedido da turma é sempre a última mensagem, de usuário, separada das instruções. */
    @Test
    void promptPrecisaTerminarComMensagemDoUsuario() {
        assertThatThrownBy(() -> new PromptMontado("v1", "Instruções", List.of(
                        MensagemDoPrompt.doUsuario("Oi"), MensagemDoPrompt.doAssistente("Olá"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mensagem do usuário");
        assertThatThrownBy(() -> new PromptMontado("v1", "Instruções", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mensagem do usuário");
        assertThatThrownBy(() -> new PromptMontado("v1", "Instruções", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void versaoEInstrucoesSaoObrigatorias(String vazio) {
        List<MensagemDoPrompt> pedido = List.of(MensagemDoPrompt.doUsuario("Oi"));

        assertThatThrownBy(() -> new PromptMontado(vazio, "Instruções", pedido))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("versão");
        assertThatThrownBy(() -> new PromptMontado("v1", vazio, pedido))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("instruções");
    }

    @Test
    void mensagensNaoPodemSerAlteradasDepois() {
        List<MensagemDoPrompt> mensagens = new ArrayList<>(List.of(MensagemDoPrompt.doUsuario("Oi")));
        PromptMontado prompt = new PromptMontado("v1", "Instruções", mensagens);

        mensagens.add(MensagemDoPrompt.doAssistente("Olá"));

        assertThat(prompt.mensagens()).hasSize(1);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void mensagemExigeTexto(String vazio) {
        assertThatThrownBy(() -> MensagemDoPrompt.doUsuario(vazio))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("texto");
    }

    @Test
    void mensagemExigePapel() {
        assertThatThrownBy(() -> new MensagemDoPrompt(null, "Oi"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("papel");
    }

    @Test
    void geracaoSemParametrosNemUsoFicaComValoresVazios() {
        Geracao<String> geracao = new Geracao<>("Olá", "gpt-6.1-terra", null, null);

        assertThat(geracao.parametros()).isEmpty();
        assertThat(geracao.uso()).isEqualTo(UsoDoModelo.desconhecido());
        assertThat(new Geracao<>("Olá", "m", Map.of("a", 1), new UsoDoModelo(1, 1)).parametros()).containsKey("a");
    }
}
