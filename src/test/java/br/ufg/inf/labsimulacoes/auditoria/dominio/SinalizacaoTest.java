package br.ufg.inf.labsimulacoes.auditoria.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.auditoria.dominio.TrechoSinalizado.MetadadosCopiados;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** O que a sinalização exige (RN-21) e o que ela copia da conversa (Stack, seção 6). */
class SinalizacaoTest {

    private static final Instant INICIO = Instant.parse("2026-11-20T13:00:00Z");
    private static final Instant RESPOSTA = Instant.parse("2026-11-20T13:05:00Z");
    private static final Instant AGORA = Instant.parse("2026-11-21T10:00:00Z");
    private static final MetadadosCopiados METADADOS = new MetadadosCopiados("openai", "m", "personagem-v1");
    private static final TrechoSinalizado TRECHO = new TrechoSinalizado(
            1L, INICIO, 2, 3, 4L, 5L, METADADOS, "Como foi o primeiro voo?", "Voei em 1906, em Paris.", RESPOSTA);

    @Test
    void justificativaPerdeOsEspacosDasPontas() {
        var sinalizacao = sinalizacao("  O voo homologado foi em outubro.  ");

        assertThat(sinalizacao.justificativa()).isEqualTo("O voo homologado foi em outubro.");
        assertThat(sinalizacao.tipo()).isEqualTo(TipoDeErro.ERRO_HISTORICO);
        assertThat(sinalizacao.gravidade()).isEqualTo(GravidadePercebida.MODERADA);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    void justificativaEObrigatoria(String justificativa) {
        assertThatThrownBy(() -> sinalizacao(justificativa))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Escreva a justificativa");
    }

    @Test
    void justificativaTemNoMaximoQuinhentosCaracteres() {
        assertThat(sinalizacao("a".repeat(500)).justificativa()).hasSize(500);
        assertThat(sinalizacao(" " + "a".repeat(500) + " ").justificativa()).hasSize(500);
        assertThatThrownBy(() -> sinalizacao("a".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A justificativa tem no máximo 500 caracteres");
    }

    @Test
    void tipoEGravidadeSaoObrigatorios() {
        assertThatThrownBy(() -> new Sinalizacao(
                        TRECHO, null, GravidadePercebida.LEVE, "Justificativa", 2, PerfilDaSinalizacao.PROFESSOR, AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Escolha o tipo do erro");
        assertThatThrownBy(() -> new Sinalizacao(
                        TRECHO, TipoDeErro.ANACRONISMO, null, "Justificativa", 2, PerfilDaSinalizacao.PROFESSOR, AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Escolha a gravidade percebida");
    }

    @Test
    void trechoPerfilEMomentoSaoObrigatorios() {
        assertThatThrownBy(() -> new Sinalizacao(null, TipoDeErro.ANACRONISMO, GravidadePercebida.LEVE, "J", 2,
                        PerfilDaSinalizacao.CURADOR, AGORA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Sinalizacao(TRECHO, TipoDeErro.ANACRONISMO, GravidadePercebida.LEVE, "J", 2,
                        null, AGORA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Sinalizacao(TRECHO, TipoDeErro.ANACRONISMO, GravidadePercebida.LEVE, "J", 2,
                        PerfilDaSinalizacao.CURADOR, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void trechoExigeOsTextosEOsMomentos() {
        assertThatThrownBy(() -> new TrechoSinalizado(1L, null, 2, 3, 4L, null, null, "P", "R", RESPOSTA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TrechoSinalizado(1L, INICIO, 2, 3, 4L, null, null, null, "R", RESPOSTA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TrechoSinalizado(1L, INICIO, 2, 3, 4L, null, null, "P", null, RESPOSTA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TrechoSinalizado(1L, INICIO, 2, 3, 4L, null, null, "P", "R", null))
                .isInstanceOf(NullPointerException.class);
    }

    /** A referência aos metadados nunca vem sem a cópia; a cópia sem a referência é o que sobra da retenção. */
    @Test
    void referenciaAosMetadadosVemComACopia() {
        assertThatThrownBy(() -> new TrechoSinalizado(1L, INICIO, 2, 3, 4L, 5L, null, "P", "R", RESPOSTA))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(new TrechoSinalizado(null, INICIO, 2, 3, null, null, METADADOS, "P", "R", RESPOSTA).metadados())
                .isEqualTo(METADADOS);
        assertThat(new TrechoSinalizado(1L, INICIO, 2, 3, 4L, null, null, "P", "R", RESPOSTA).metadados()).isNull();
    }

    @Test
    void metadadosCopiadosTemOsTresCampos() {
        assertThatThrownBy(() -> new MetadadosCopiados(null, "m", "v1")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MetadadosCopiados("openai", null, "v1")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MetadadosCopiados("openai", "m", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void tiposEGravidadesTemRotuloParaATela() {
        assertThat(TipoDeErro.SAIDA_DO_PERSONAGEM.rotulo()).isEqualTo("Saída do personagem");
        assertThat(TipoDeErro.values()).hasSize(5);
        assertThat(GravidadePercebida.GRAVE.rotulo()).isEqualTo("Grave");
        assertThat(GravidadePercebida.values()).allSatisfy(gravidade -> assertThat(gravidade.descricao()).isNotBlank());
    }

    private static Sinalizacao sinalizacao(String justificativa) {
        return new Sinalizacao(TRECHO, TipoDeErro.ERRO_HISTORICO, GravidadePercebida.MODERADA, justificativa, 2,
                PerfilDaSinalizacao.PROFESSOR, AGORA);
    }
}
