package br.ufg.inf.labsimulacoes.ia.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class MetadadosDaRespostaTest {

    private static final TabelaDePrecos PRECOS = new TabelaDePrecos(new BigDecimal("1.00"), new BigDecimal("5.00"));

    @Test
    void daChamadaCalculaCustoELatencia() {
        MetadadosDaResposta metadados = MetadadosDaResposta.daChamada(
                "openai", "gpt-6.1-terra", Map.of("reasoning_effort", "low"), "provisorio-1",
                new UsoDoModelo(316, 97), PRECOS, Duration.ofMillis(1960));

        assertThat(metadados.id()).isNull();
        assertThat(metadados.custo()).isEqualByComparingTo("0.000801");
        assertThat(metadados.latenciaMs()).isEqualTo(1960);
        assertThat(metadados.parametros()).containsExactly(Map.entry("reasoning_effort", "low"));
    }

    @Test
    void semTokensInformadosOCustoFicaNulo() {
        MetadadosDaResposta metadados = MetadadosDaResposta.daChamada(
                "openai", "gpt-6.1-terra", Map.of(), "provisorio-1",
                UsoDoModelo.desconhecido(), PRECOS, Duration.ofMillis(10));

        assertThat(metadados.custo()).isNull();
    }

    @Test
    void comIdMantemOsDemaisCampos() {
        MetadadosDaResposta metadados = MetadadosDaResposta.daChamada(
                "openai", "gpt-6.1-terra", Map.of("temperature", 0.7), "provisorio-1",
                new UsoDoModelo(1, 2), PRECOS, Duration.ofMillis(10));

        MetadadosDaResposta gravado = metadados.comId(42);

        assertThat(gravado.id()).isEqualTo(42L);
        assertThat(gravado).usingRecursiveComparison().ignoringFields("id").isEqualTo(metadados);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void provedorModeloEVersaoDoPromptSaoObrigatorios(String vazio) {
        assertThatThrownBy(() -> metadados(vazio, "gpt-6.1-terra", "provisorio-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provedor");
        assertThatThrownBy(() -> metadados("openai", vazio, "provisorio-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modelo");
        assertThatThrownBy(() -> metadados("openai", "gpt-6.1-terra", vazio))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("versão do prompt");
    }

    @Test
    void textosFicamSemEspacosNasPontas() {
        MetadadosDaResposta metadados = metadados(" openai ", " gpt-6.1-terra ", " provisorio-1 ");

        assertThat(metadados.provedor()).isEqualTo("openai");
        assertThat(metadados.modelo()).isEqualTo("gpt-6.1-terra");
        assertThat(metadados.versaoPrompt()).isEqualTo("provisorio-1");
    }

    @Test
    void parametroSemValorNaoEntraNoRastro() {
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("reasoning_effort", "low");
        parametros.put("temperature", null);

        MetadadosDaResposta metadados = new MetadadosDaResposta(
                null, "openai", "gpt-6.1-terra", parametros, "provisorio-1", null, null, 0);

        assertThat(metadados.parametros()).containsOnlyKeys("reasoning_effort");
    }

    @Test
    void parametrosEUsoAusentesViramVazios() {
        MetadadosDaResposta metadados = new MetadadosDaResposta(
                null, "openai", "gpt-6.1-terra", null, "provisorio-1", null, null, 0);

        assertThat(metadados.parametros()).isEmpty();
        assertThat(metadados.uso()).isEqualTo(UsoDoModelo.desconhecido());
    }

    @Test
    void parametrosNaoPodemSerAlteradosDepois() {
        Map<String, Object> parametros = new HashMap<>(Map.of("reasoning_effort", "low"));
        MetadadosDaResposta metadados = new MetadadosDaResposta(
                null, "openai", "gpt-6.1-terra", parametros, "provisorio-1", null, null, 0);

        parametros.put("temperature", 1.0);

        assertThat(metadados.parametros()).containsOnlyKeys("reasoning_effort");
        assertThatThrownBy(() -> metadados.parametros().put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void custoELatenciaNaoPodemSerNegativos() {
        assertThatThrownBy(() -> new MetadadosDaResposta(
                        null, "openai", "gpt-6.1-terra", Map.of(), "provisorio-1", null, new BigDecimal("-0.000001"), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("custo");
        assertThatThrownBy(() -> new MetadadosDaResposta(
                        null, "openai", "gpt-6.1-terra", Map.of(), "provisorio-1", null, null, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("latência");
    }

    private static MetadadosDaResposta metadados(String provedor, String modelo, String versaoPrompt) {
        return new MetadadosDaResposta(null, provedor, modelo, Map.of(), versaoPrompt, null, null, 0);
    }
}
