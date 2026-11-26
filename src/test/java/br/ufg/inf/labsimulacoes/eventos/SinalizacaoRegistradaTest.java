package br.ufg.inf.labsimulacoes.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * O evento da sinalização não leva texto (RF-23, RNF-01): nem a pergunta, nem a resposta, nem a
 * justificativa saem da Auditoria por evento. Se alguém acrescentar um campo de texto, este teste falha
 * e pede a revisão do payload.
 */
class SinalizacaoRegistradaTest {

    private static final Instant MOMENTO = Instant.parse("2026-11-21T10:00:00Z");

    @Test
    void payloadTemSoIdsCodigosEOMomento() {
        assertThat(Arrays.stream(SinalizacaoRegistrada.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("sinalizacaoId", "sessaoId", "versaoId", "tipo", "gravidadePercebida", "perfil",
                        "sinalizadoPorId", "ocorridoEm");
    }

    /** Os únicos textos são códigos de listas fechadas; nada digitado ou gerado. */
    @Test
    void textosDoPayloadSaoSoOsCodigos() {
        assertThat(Arrays.stream(SinalizacaoRegistrada.class.getRecordComponents())
                        .filter(componente -> componente.getType() == String.class)
                        .map(RecordComponent::getName))
                .containsExactly("tipo", "gravidadePercebida", "perfil");
        assertThat(Arrays.stream(SinalizacaoRegistrada.class.getRecordComponents()).map(RecordComponent::getName))
                .doesNotContain("pergunta", "resposta", "justificativa", "trecho", "texto");
    }

    @Test
    void codigosEMomentoSaoObrigatorios() {
        assertThatThrownBy(() -> new SinalizacaoRegistrada(1, 2, 3, null, "GRAVE", "PROFESSOR", 4, MOMENTO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SinalizacaoRegistrada(1, 2, 3, "ANACRONISMO", null, "PROFESSOR", 4, MOMENTO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SinalizacaoRegistrada(1, 2, 3, "ANACRONISMO", "GRAVE", null, 4, MOMENTO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SinalizacaoRegistrada(1, 2, 3, "ANACRONISMO", "GRAVE", "PROFESSOR", 4, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void eventoCompletoGuardaOsValores() {
        var evento = new SinalizacaoRegistrada(1, 2, 3, "ANACRONISMO", "GRAVE", "PROFESSOR", 4, MOMENTO);

        assertThat(evento.sinalizacaoId()).isEqualTo(1);
        assertThat(evento.gravidadePercebida()).isEqualTo("GRAVE");
        assertThat(evento.ocorridoEm()).isEqualTo(MOMENTO);
    }
}
